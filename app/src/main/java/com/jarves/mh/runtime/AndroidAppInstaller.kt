package com.jarves.mh.runtime

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.FileProvider
import com.jarves.mh.R
import com.jarves.mh.ui.AppStrings
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class AndroidInstallEvent(
    val operationId: String,
    val packageName: String?,
    val success: Boolean,
    val launched: Boolean,
    val message: String? = null,
)

/**
 * Installs a locally-built APK through Android's package manager, without ADB.
 *
 * Some OEMs (especially MIUI) never deliver PackageInstaller status callbacks even when
 * the system installer succeeds. We always track the pending APK and complete the
 * operation when the host Activity resumes and the package is present.
 */
object AndroidAppInstaller {
    private val _events = MutableSharedFlow<AndroidInstallEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    const val ACTION_INSTALL_RESULT = "com.jarves.mh.action.ANDROID_INSTALL_RESULT"
    const val EXTRA_OPERATION_ID = "operation_id"
    const val EXTRA_LAUNCH_AFTER = "launch_after"
    const val EXTRA_APK_FINGERPRINT = "apk_fingerprint"

    private data class PendingInstall(
        val operationId: String,
        val apkPath: String,
        val packageName: String?,
        val fingerprint: String,
        val launchAfterInstall: Boolean,
        val archiveVersionCode: Long,
    )

    @Volatile
    private var pendingInstall: PendingInstall? = null

    fun openIfAlreadyInstalled(context: Context, apk: File): Boolean {
        val packageName = archivePackageName(context, apk) ?: return false
        if (installedFingerprint(context, packageName) != fingerprint(apk)) return false
        return launch(context, packageName)
    }

    fun install(
        context: Context,
        apk: File,
        launchAfterInstall: Boolean = true,
        operationId: String = UUID.randomUUID().toString(),
    ): String {
        require(apk.isFile && apk.extension.equals("apk", ignoreCase = true) && apk.length() > 0L) {
            AppStrings.get(context, R.string.rt_apk_invalid, apk.name)
        }
        val packageName = archivePackageName(context, apk)
        val fp = fingerprint(apk)
        val versionCode = archiveVersionCode(context, apk)
        pendingInstall = PendingInstall(
            operationId = operationId,
            apkPath = apk.absolutePath,
            packageName = packageName,
            fingerprint = fp,
            launchAfterInstall = launchAfterInstall,
            archiveVersionCode = versionCode,
        )

        // MIUI / HyperOS: system package installer UI (no reliable session callback).
        if (isMiuiDevice()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
            context.startActivity(
                Intent(Intent.ACTION_INSTALL_PACKAGE, uri).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return operationId
        }

        val installer = context.packageManager.packageInstaller
        val isUpdate = packageName?.let { isInstalled(context, it) } == true
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply {
                setSize(apk.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(
                        if (isUpdate) PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                        else PackageInstaller.SessionParams.USER_ACTION_REQUIRED,
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setInstallerPackageName(context.packageName)
                }
            }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().buffered().use { input ->
                session.openWrite("package", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = Intent(ACTION_INSTALL_RESULT).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_OPERATION_ID, operationId)
                putExtra(EXTRA_LAUNCH_AFTER, launchAfterInstall)
                putExtra(EXTRA_APK_FINGERPRINT, fp)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pi.intentSender)
        }
        return operationId
    }

    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var resumeWatchJob: Job? = null

    /**
     * Called from MainActivity.onResume.
     * Completes a pending install when the system has the package (covers MIUI and
     * any OEM where PackageInstaller status is never delivered).
     *
     * PackageManager often lags the system installer UI by a short time, so we retry
     * for several seconds instead of giving up on the first resume.
     */
    fun onHostResumed(context: Context) {
        val pending = pendingInstall ?: return
        val app = context.applicationContext
        resumeWatchJob?.cancel()
        resumeWatchJob = hostScope.launch {
            // Immediate attempt, then backoff — OEM installers often need a moment.
            val delaysMs = longArrayOf(0L, 300L, 700L, 1200L, 2000L, 3000L, 5000L)
            for (wait in delaysMs) {
                if (wait > 0L) delay(wait)
                if (pendingInstall?.operationId != pending.operationId) return@launch
                if (tryCompletePendingIfInstalled(app, pending)) return@launch
            }
        }
    }

    /**
     * Safety net while the UI is stuck on INSTALLING: call from ViewModel on a timer.
     * Returns true if the pending install was completed.
     */
    fun pollPendingInstall(context: Context): Boolean {
        val pending = pendingInstall ?: return false
        return tryCompletePendingIfInstalled(context.applicationContext, pending)
    }

    private fun tryCompletePendingIfInstalled(context: Context, pending: PendingInstall): Boolean {
        if (pendingInstall?.operationId != pending.operationId) return false
        val apk = File(pending.apkPath)
        if (!apk.isFile) return false
        val pkg = pending.packageName ?: archivePackageName(context, apk) ?: return false
        if (!isInstalled(context, pkg)) return false

        val installedVc = installedVersionCode(context, pkg)
        // Only treat as cancelled when an *older* version is still the only one installed.
        if (pending.archiveVersionCode > 0L && installedVc > 0L && installedVc < pending.archiveVersionCode) {
            return false
        }

        rememberInstalled(context, pkg, pending.fingerprint)
        pendingInstall = null
        resumeWatchJob?.cancel()
        resumeWatchJob = null
        val launched = if (pending.launchAfterInstall) launch(context, pkg) else false
        publish(
            AndroidInstallEvent(
                operationId = pending.operationId,
                packageName = pkg,
                success = true,
                launched = launched,
            ),
        )
        return true
    }

    fun cancelPendingInstall(operationId: String? = null) {
        val pending = pendingInstall ?: return
        if (operationId != null && pending.operationId != operationId) return
        pendingInstall = null
        publish(
            AndroidInstallEvent(
                operationId = pending.operationId,
                packageName = pending.packageName,
                success = false,
                launched = false,
                message = "Installation cancelled",
            ),
        )
    }

    /** Receiver / session path reports completion — clear pending and publish once. */
    fun completeFromCallback(
        operationId: String,
        packageName: String?,
        success: Boolean,
        launched: Boolean,
        message: String? = null,
    ) {
        val pending = pendingInstall
        if (pending != null && pending.operationId == operationId) {
            pendingInstall = null
        }
        publish(
            AndroidInstallEvent(
                operationId = operationId,
                packageName = packageName ?: pending?.packageName,
                success = success,
                launched = launched,
                message = message,
            ),
        )
    }

    fun publish(event: AndroidInstallEvent) {
        _events.tryEmit(event)
    }

    fun packageName(context: Context, apk: File): String? = archivePackageName(context, apk)

    fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    fun installedMatches(context: Context, apk: File): Boolean {
        val packageName = archivePackageName(context, apk) ?: return false
        return installedFingerprint(context, packageName) == fingerprint(apk)
    }

    fun apkFingerprint(apk: File): String = fingerprint(apk)

    internal fun rememberInstalled(context: Context, packageName: String, fingerprint: String) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(packageName, fingerprint)
            .apply()
    }

    internal fun launch(context: Context, packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getLaunchIntentSenderForPackage(packageName).sendIntent(
                context, 0, null, null, null,
            )
        } else {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        true
    }.getOrDefault(false)

    private fun archivePackageName(context: Context, apk: File): String? =
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName

    private fun archiveVersionCode(context: Context, apk: File): Long {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0) ?: return -1L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    private fun installedVersionCode(context: Context, packageName: String): Long = runCatching {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(-1L)

    private fun installedFingerprint(context: Context, packageName: String): String? =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(packageName, null)

    private fun fingerprint(apk: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private const val PREFERENCES = "installed_android_apks"

    private fun isMiuiDevice(): Boolean {
        val mfr = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        if (mfr in setOf("xiaomi", "redmi", "poco") || brand in setOf("xiaomi", "redmi", "poco")) return true
        // Some HyperOS builds report differently; prefer system installer path when MIUI prop exists.
        return runCatching {
            val cl = Class.forName("android.os.SystemProperties")
            val get = cl.getMethod("get", String::class.java)
            val miui = (get.invoke(null, "ro.miui.ui.version.name") as? String).orEmpty()
            miui.isNotBlank()
        }.getOrDefault(false)
    }
}

