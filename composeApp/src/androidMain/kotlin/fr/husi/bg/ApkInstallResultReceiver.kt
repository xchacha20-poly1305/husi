package fr.husi.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage

class ApkInstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = intent.confirmationIntent()
                if (confirmation == null) {
                    PendingApkInstalls.complete(
                        sessionId,
                        ApkInstallResult.Failed("the system asked for confirmation without an intent"),
                    )
                    return
                }
                try {
                    context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Logs.e("start the install confirmation", e)
                    PendingApkInstalls.complete(
                        sessionId,
                        ApkInstallResult.Failed(e.readableMessage),
                    )
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                PendingApkInstalls.complete(sessionId, ApkInstallResult.Success)
            }

            else -> {
                PendingApkInstalls.complete(
                    sessionId,
                    ApkInstallResult.Failed(message ?: "install failed with status $status"),
                )
            }
        }
    }
}

private fun Intent.confirmationIntent(): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_INTENT)
    }
