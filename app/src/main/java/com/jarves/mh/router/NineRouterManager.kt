package com.jarves.mh.router

import android.content.Context
import android.util.Log
import com.jarves.mh.runtime.RuntimeInstaller
import java.io.File
import java.util.UUID
import java.security.SecureRandom
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class NineRouterPhase {
    NEED_RUNTIME,
    INSTALL,
    READY,
    INSTALLING,
    STARTING,
    RUNNING,
    STOPPING,
    CONNECTING,
    ERROR,
}

data class NineRouterState(
    val phase: NineRouterPhase = NineRouterPhase.NEED_RUNTIME,
    val detail: String = "",
    val error: String? = null,
    val installedVersion: String? = null,
    val notice: String? = null,
    val providerLinked: Boolean = false,
) {
    val busy: Boolean
        get() = phase in setOf(
            NineRouterPhase.INSTALLING,
            NineRouterPhase.STARTING,
            NineRouterPhase.STOPPING,
            NineRouterPhase.CONNECTING,
        )
}

/**
 * Installs and runs the npm `9router` package inside the MH Linux runtime,
 * then links it as an OpenAI-compatible provider (127.0.0.1:20128/v1).
 *
 * Prerequisites: base Linux runtime installed (includes Node.js via Web stack).
 * Install: npm install 9router@pinned + sql.js into /root/9router.
 */
class NineRouterManager(context: Context) {
    private val appContext = context.applicationContext
    private val installer = RuntimeInstaller(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val working = AtomicBoolean(false)
    private var process: Process? = null
    @Volatile var lastIntegrationKey: String? = null

    private val guestRoot = "/root/9router"
    private val guestServer = "$guestRoot/node_modules/9router/app/custom-server.js"
    private val guestData = "$guestRoot/data"
    private val guestMarker = "$guestRoot/VERSION"

    private val hostRoot: File
        get() = File(installer.installedRuntime().rootfs, guestRoot.removePrefix("/"))
    private val hostServer: File
        get() = File(installer.installedRuntime().rootfs, guestServer.removePrefix("/"))
    private val hostData: File
        get() = File(installer.installedRuntime().rootfs, guestData.removePrefix("/"))
    private val hostMarker: File
        get() = File(installer.installedRuntime().rootfs, guestMarker.removePrefix("/"))

    private val mutable = MutableStateFlow(refreshState())
    val state = mutable.asStateFlow()

    fun refresh() {
        if (!working.get()) mutable.value = refreshState()
    }

    private fun refreshState(): NineRouterState {
        if (!installer.isInstalled()) {
            return NineRouterState(NineRouterPhase.NEED_RUNTIME, detail = "Install Linux runtime first.")
        }
        val version = hostMarker.takeIf { it.isFile }?.readText()?.trim()
        val running = process?.isAlive == true || portOpen()
        val phase = when {
            running -> NineRouterPhase.RUNNING
            version == null || !hostServer.isFile -> NineRouterPhase.INSTALL
            else -> NineRouterPhase.READY
        }
        return NineRouterState(
            phase = phase,
            installedVersion = version,
            providerLinked = false,
            detail = if (phase == NineRouterPhase.NEED_RUNTIME) "Install Linux runtime first." else "",
        )
    }

    fun install(onDone: (Boolean, String?) -> Unit = { _, _ -> }) = work(NineRouterPhase.INSTALLING, onDone) {
        check(installer.isInstalled()) { "Install the Linux runtime first." }
        runGuest(
            "node --version && npm --version",
            timeoutSec = 60,
        ).also { out ->
            check(out.first == 0) { "Node.js/npm missing. Open Settings → Developer tools and ensure Core/Web tools are installed.\n${out.second}" }
        }
        hostRoot.mkdirs()
        val stage = File(hostRoot, ".stage").also {
            if (it.exists()) it.deleteRecursively()
            it.mkdirs()
        }
        val version = NineRouterRelease.PINNED
        NineRouterRelease.validate(version)
        mutable.update { it.copy(detail = "npm install 9router@$version …") }
        val installCmd =
            "mkdir -p $guestRoot/.stage && " +
                "cd $guestRoot/.stage && " +
                "npm install --prefix . --ignore-scripts --omit=optional --no-audit --no-fund " +
                "9router@$version sql.js@${NineRouterRelease.SQL_JS} && " +
                "test -f node_modules/9router/app/custom-server.js"
        val result = runGuest(installCmd, timeoutSec = 900)
        check(result.first == 0) { "9Router install failed:\n${result.second.takeLast(1500)}" }
        val modules = File(hostRoot, "node_modules")
        if (modules.exists()) modules.deleteRecursively()
        check(File(stage, "node_modules").renameTo(modules)) { "Could not activate 9Router modules." }
        File(stage, "package.json").takeIf { it.isFile }?.copyTo(File(hostRoot, "package.json"), overwrite = true)
        hostMarker.writeText(version)
        stage.deleteRecursively()
        mutable.update { it.copy(notice = "9Router $version installed.", installedVersion = version) }
    }

    fun start(onDone: (Boolean, String?) -> Unit = { _, _ -> }) = work(NineRouterPhase.STARTING, onDone) {
        startServer()
        mutable.update { it.copy(notice = "9Router is running on port $PORT.") }
    }

    fun stop(onDone: (Boolean, String?) -> Unit = { _, _ -> }) = work(NineRouterPhase.STOPPING, onDone) {
        stopServer()
    }

    /**
     * Match AndroidHarness: ensure server is up, then create/reuse integration API key
     * via x-9r-cli-token derived from DATA_DIR machine-id + auth/cli-secret.
     */
    fun connectProvider(onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        work(NineRouterPhase.CONNECTING, onDone) {
            // Auth files must exist before Node loads dashboardGuard (token is cached once).
            ensureAuthFiles()
            if (process?.isAlive != true || !portOpen()) {
                startServer()
            }
            val key = try {
                NineRouterControl(hostData).integrationKey()
            } catch (e: Exception) {
                // Stale in-memory CLI token (server started before files matched) →
                // stop, rewrite credentials like AndroidHarness first run, start clean.
                Log.w(TAG, "9Router control failed (${e.message}); rewriting auth and restarting")
                stopServer()
                File(hostData, "machine-id").delete()
                File(hostData, "auth/cli-secret").delete()
                ensureAuthFiles()
                startServer()
                NineRouterControl(hostData).integrationKey()
            }
            lastIntegrationKey = key
            mutable.update {
                it.copy(
                    providerLinked = true,
                    notice = "9Router linked at $API. Connect upstream models in the dashboard, then Discover models.",
                )
            }
        }
    }

    fun checkForUpdates(onDone: (Boolean, String?) -> Unit = { _, _ -> }) = work(NineRouterPhase.INSTALLING, onDone) {
        check(installer.isInstalled()) { "Install the Linux runtime first." }
        val current = hostMarker.takeIf { it.isFile }?.readText()?.trim()
        check(!current.isNullOrBlank()) { "Install 9Router first." }
        mutable.update { it.copy(detail = "Checking npm registry…") }
        val latest = NineRouterRelease.latest()
        if (!NineRouterRelease.newer(latest, current)) {
            mutable.update { it.copy(notice = "9Router is up to date (v$current).") }
            return@work
        }
        mutable.update { it.copy(detail = "Updating 9Router $current → $latest …") }
        NineRouterRelease.validate(latest)
        val stage = File(hostRoot, ".stage").also {
            if (it.exists()) it.deleteRecursively()
            it.mkdirs()
        }
        val installCmd =
            "mkdir -p $guestRoot/.stage && " +
                "cd $guestRoot/.stage && " +
                "npm install --prefix . --ignore-scripts --omit=optional --no-audit --no-fund " +
                "9router@$latest sql.js@${NineRouterRelease.SQL_JS} && " +
                "test -f node_modules/9router/app/custom-server.js"
        val result = runGuest(installCmd, timeoutSec = 900)
        check(result.first == 0) { "9Router update failed:\n${result.second.takeLast(1500)}" }
        val modules = File(hostRoot, "node_modules")
        if (modules.exists()) modules.deleteRecursively()
        check(File(stage, "node_modules").renameTo(modules)) { "Could not activate 9Router modules." }
        File(stage, "package.json").takeIf { it.isFile }?.copyTo(File(hostRoot, "package.json"), overwrite = true)
        hostMarker.writeText(latest)
        stage.deleteRecursively()
        mutable.update { it.copy(notice = "9Router updated to v$latest.", installedVersion = latest) }
    }





    /**
     * AndroidHarness seeds machine-id (UUID) + auth/cli-secret under DATA_DIR before control calls.
     * 9Router token = sha256(machineId + "9r-cli-auth" + cliSecret).hex.take(16) (see decolua/9router machineId.js).
     * DATA_DIR for proot guest is /root/9router/data → host rootfs/root/9router/data.
     */
    private fun ensureAuthFiles() {
        hostData.mkdirs()
        val machine = File(hostData, "machine-id")
        val secretDir = File(hostData, "auth")
        secretDir.mkdirs()
        val secret = File(secretDir, "cli-secret")
        // Match AndroidHarness: UUID machine-id, 32-byte hex secret. Rewrite if missing/empty.
        if (!machine.isFile || machine.length() == 0L) {
            machine.writeText(UUID.randomUUID().toString())
        }
        if (!secret.isFile || secret.length() == 0L) {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            secret.writeText(bytes.joinToString("") { "%02x".format(it) })
        }
    }

    /** Stop tracked process and wait until port 20128 is free (orphan-safe). */
    private suspend fun stopServer() {
        val child = process
        process = null
        if (child != null) {
            child.destroy()
            if (!child.waitFor(5, TimeUnit.SECONDS)) child.destroyForcibly()
            runCatching { child.waitFor(3, TimeUnit.SECONDS) }
        }
        val deadline = System.currentTimeMillis() + 20_000
        while (portOpen() && System.currentTimeMillis() < deadline) {
            delay(250)
        }
        check(!portOpen()) {
            "Port $PORT is still in use after stop. Force-close any old 9Router and try again."
        }
    }

    private suspend fun startServer() {
        check(hostServer.isFile) { "Install 9Router first." }
        if (process?.isAlive == true && portOpen()) return
        if (portOpen()) {
            // Orphan holds the port — cannot safely attach; require stop first.
            stopServer()
        }
        ensureAuthFiles()
        val env = mapOf(
            "PORT" to "$PORT",
            "HOSTNAME" to "127.0.0.1",
            "NODE_ENV" to "production",
            "NEXT_TELEMETRY_DISABLED" to "1",
            "NODE_PATH" to "$guestRoot/node_modules",
            // Guest path inside proot (same layout AndroidHarness uses under its rootfs).
            "DATA_DIR" to guestData,
            "HOME" to "/root",
        )
        val runtime = installer.installedRuntime()
        val child = installer.process(
            runtime.proot,
            runtime.rootfs,
            appContext.filesDir,
            env,
            listOf(
                "node",
                "--dns-result-order=ipv4first",
                "--max-old-space-size=1024",
                guestServer,
            ),
            guestWorkspacePath = "/workspace",
        )
        process = child
        val deadline = System.currentTimeMillis() + 90_000
        while (!portOpen()) {
            check(child.isAlive) { "9Router exited during startup." }
            check(System.currentTimeMillis() < deadline) { "9Router startup timed out." }
            delay(300)
        }
        // Let dashboardGuard load machine-id/cli-secret into its in-memory cache.
        delay(800)
    }

    private fun work(
        phase: NineRouterPhase,
        onDone: (Boolean, String?) -> Unit,
        block: suspend () -> Unit,
    ) {
        if (!working.compareAndSet(false, true)) {
            onDone(false, "Another 9Router action is already running.")
            return
        }
        mutable.update { it.copy(phase = phase, detail = "", error = null, notice = null) }
        scope.launch {
            try {
                block()
                mutable.update { refreshState().copy(notice = it.notice, error = null) }
                onDone(true, null)
            } catch (e: Exception) {
                Log.e(TAG, "9Router action failed", e)
                mutable.update {
                    // Prefer keeping a useful phase: if binary is present keep READY, else ERROR
                    val base = refreshState()
                    val failPhase = when {
                        base.phase == NineRouterPhase.RUNNING || base.phase == NineRouterPhase.READY -> base.phase
                        base.phase == NineRouterPhase.NEED_RUNTIME -> NineRouterPhase.NEED_RUNTIME
                        else -> NineRouterPhase.ERROR
                    }
                    base.copy(
                        phase = failPhase,
                        error = e.message ?: "9Router action failed",
                        notice = null,
                    )
                }
                onDone(false, e.message)
            } finally {
                working.set(false)
            }
        }
    }

    private fun runGuest(command: String, timeoutSec: Long): Pair<Int, String> {
        val runtime = installer.installedRuntime()
        val outFile = File(appContext.cacheDir, "9router-cmd-${System.nanoTime()}.log")
        val process = installer.process(
            runtime.proot,
            runtime.rootfs,
            appContext.filesDir,
            emptyMap(),
            listOf("sh", "-c", command),
            guestWorkspacePath = "/workspace",
            outputFile = outFile,
        )
        val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return -1 to "Command timed out after ${timeoutSec}s"
        }
        val text = outFile.readText().takeLast(4000)
        outFile.delete()
        return process.exitValue() to text
    }

    private fun portOpen(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 400); true }
    }.getOrDefault(false)

    companion object {
        const val PORT = 20128
        const val API = "http://127.0.0.1:$PORT/v1"
        const val DASHBOARD = "http://127.0.0.1:$PORT"
        private const val TAG = "NineRouter"
    }
}
