package com.jarves.mh.router

import android.content.Context
import android.util.Log
import com.jarves.mh.runtime.RuntimeInstaller
import java.io.File
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
        check(hostServer.isFile && hostMarker.isFile) { "Install 9Router first." }
        if (process?.isAlive == true) return@work
        check(!portOpen()) { "Port $PORT is already in use." }
        hostData.mkdirs()
        val env = mapOf(
            "PORT" to "$PORT",
            "HOSTNAME" to "127.0.0.1",
            "NODE_ENV" to "production",
            "NEXT_TELEMETRY_DISABLED" to "1",
            "NODE_PATH" to "$guestRoot/node_modules",
            "DATA_DIR" to guestData,
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
        ensurePreferredDataDir()
        nudgeServerInit()
        mutable.update { it.copy(notice = "9Router is running on port $PORT.") }
    }

    fun stop(onDone: (Boolean, String?) -> Unit = { _, _ -> }) = work(NineRouterPhase.STOPPING, onDone) {
        val child = process ?: return@work
        child.destroy()
        if (!child.waitFor(5, TimeUnit.SECONDS)) child.destroyForcibly()
        process = null
    }

    fun connectProvider(onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        work(NineRouterPhase.CONNECTING, onDone) {
            if (process?.isAlive != true && !portOpen()) {
                check(hostServer.isFile) { "Install 9Router first." }
                hostData.mkdirs()
                val env = mapOf(
                    "PORT" to "$PORT",
                    "HOSTNAME" to "127.0.0.1",
                    "NODE_ENV" to "production",
                    "NEXT_TELEMETRY_DISABLED" to "1",
                    "NODE_PATH" to "$guestRoot/node_modules",
                    "DATA_DIR" to guestData,
                )
                val runtime = installer.installedRuntime()
                process = installer.process(
                    runtime.proot,
                    runtime.rootfs,
                    appContext.filesDir,
                    env,
                    listOf("node", "--dns-result-order=ipv4first", "--max-old-space-size=1024", guestServer),
                    guestWorkspacePath = "/workspace",
                )
                val deadline = System.currentTimeMillis() + 90_000
                while (!portOpen()) {
                    check(process?.isAlive == true) { "9Router exited during startup." }
                    check(System.currentTimeMillis() < deadline) { "9Router startup timed out." }
                    delay(300)
                }
            }
            ensurePreferredDataDir()
            nudgeServerInit()
            val deadlineAuth = System.currentTimeMillis() + 60_000
            var dataDir: File? = null
            while (dataDir == null) {
                dataDir = resolveAuthDataDir()
                if (dataDir != null) break
                check(System.currentTimeMillis() < deadlineAuth) {
                    val probed = authDataDirCandidates().joinToString("; ") { d ->
                        val kids = d.listFiles()?.map { it.name }?.joinToString(",") ?: "missing"
                        "${d.absolutePath}[$kids]"
                    }
                    "9Router auth files not ready. Probed: $probed"
                }
                nudgeServerInit()
                delay(500)
            }
            val resolved = dataDir!!
            // Try control API; on 401 try other candidate dirs (server may have switched DATA_DIR)
            var key: String? = null
            var lastErr: Exception? = null
            val dirsToTry = listOf(resolved) + authDataDirCandidates().filter { it != resolved && hasAuthFiles(it) }
            for (dir in dirsToTry) {
                try {
                    key = NineRouterControl(dir).integrationKey()
                    break
                } catch (e: Exception) {
                    lastErr = e
                    Log.w(TAG, "9Router control failed for ${dir.absolutePath}: ${e.message}")
                }
            }
            check(key != null) {
                lastErr?.message ?: "9Router control failed for all DATA_DIR candidates"
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
     * 9Router resolves DATA_DIR at module load:
     *   env DATA_DIR if set and writable, else ~/.9router (/root/.9router with HOME=/root).
     * Auth files (machine-id, auth/cli-secret) live under whichever dir the process actually used.
     * Probe both host paths and pick the one that has complete auth files.
     */
    private fun authDataDirCandidates(): List<File> {
        val rootfs = installer.installedRuntime().rootfs
        return listOf(
            hostData, // /root/9router/data (our DATA_DIR env)
            File(rootfs, "root/.9router"), // upstream default ~/.9router
            File(rootfs, "root/9router/data"),
        ).distinctBy { it.absolutePath }
    }

    private fun hasAuthFiles(dir: File): Boolean {
        val machine = File(dir, "machine-id")
        val secret = File(dir, "auth/cli-secret")
        return machine.isFile && machine.length() > 0L && secret.isFile && secret.length() > 0L
    }

    private fun resolveAuthDataDir(): File? =
        authDataDirCandidates().firstOrNull { hasAuthFiles(it) }

    /** Ensure preferred DATA_DIR exists so env DATA_DIR=/root/9router/data is writable. */
    private fun ensurePreferredDataDir() {
        hostData.mkdirs()
        File(hostData, "auth").mkdirs()
    }

    /**
     * Hit a public endpoint so 9Router initializes and writes machine-id / cli-secret
     * under its effective DATA_DIR (may be ~/.9router if DATA_DIR failed).
     */
    private fun nudgeServerInit() {
        runCatching {
            val c = URL("http://127.0.0.1:$PORT/api/health").openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 3_000
            c.readTimeout = 3_000
            c.requestMethod = "GET"
            c.responseCode
            c.disconnect()
        }
        runCatching {
            val c = URL("http://127.0.0.1:$PORT/api/init").openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 3_000
            c.readTimeout = 3_000
            c.requestMethod = "GET"
            c.responseCode
            c.disconnect()
        }
        runCatching {
            val c = URL("http://127.0.0.1:$PORT/").openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 3_000
            c.readTimeout = 3_000
            c.requestMethod = "GET"
            c.responseCode
            c.disconnect()
        }
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
