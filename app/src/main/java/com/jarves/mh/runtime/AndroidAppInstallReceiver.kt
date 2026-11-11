package com.jarves.mh.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import com.jarves.mh.R
import com.jarves.mh.ui.AppStrings

class AndroidAppInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != AndroidAppInstaller.ACTION_INSTALL_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val operationId = intent.getStringExtra(AndroidAppInstaller.EXTRA_OPERATION_ID) ?: return
        val launchAfterInstall = intent.getBooleanExtra(AndroidAppInstaller.EXTRA_LAUNCH_AFTER, true)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirm)
            return
        }

        if (status != PackageInstaller.STATUS_SUCCESS) {
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                ?: AppStrings.get(context, R.string.rt_apk_install_failed)
            AndroidAppInstaller.completeFromCallback(
                operationId = operationId,
                packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME),
                success = false,
                launched = false,
                message = message,
            )
            return
        }

        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        intent.getStringExtra(AndroidAppInstaller.EXTRA_APK_FINGERPRINT)?.let { fingerprint ->
            if (packageName != null) {
                AndroidAppInstaller.rememberInstalled(context, packageName, fingerprint)
            }
        }
        val launched = launchAfterInstall && packageName != null && AndroidAppInstaller.launch(context, packageName)
        AndroidAppInstaller.completeFromCallback(
            operationId = operationId,
            packageName = packageName,
            success = true,
            launched = launched,
        )
        if (launchAfterInstall && packageName != null && !launched) {
            Toast.makeText(
                context,
                AppStrings.get(context, R.string.rt_apk_installed_open_launcher, packageName),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
