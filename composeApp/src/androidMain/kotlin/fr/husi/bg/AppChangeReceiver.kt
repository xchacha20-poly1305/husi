package fr.husi.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.utils.AppScanner
import fr.husi.utils.PackageCache

class AppChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Logs.d("onReceive: ${intent.action}")
        // Keep the process alive until the scan finishes, which a bare coroutine cannot do.
        val pendingResult = goAsync()
        runOnIoDispatcher {
            try {
                checkUpdate(intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun checkUpdate(intent: Intent) {
        if (!DataStore.proxyApps.getBlocking()) {
            Logs.d("should not check in bypass mode")
            return
        }
        if (!DataStore.updateProxyAppsWhenInstall.getBlocking()) {
            Logs.d("per app proxy disabled")
            return
        }
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            Logs.d("skip app update because of EXTRA_REPLACING")
            return
        }
        val packageName = intent.dataString?.substringAfter("package:")
        if (packageName.isNullOrBlank()) {
            Logs.d("missing package name in intent")
            return
        }
        val isChinaApp = AppScanner.isChinaApp(packageName, PackageCache.packageManager)
        Logs.d("scan china app result for $packageName: $isChinaApp")
        val bypassMode = DataStore.bypassMode.getBlocking()
        if (isChinaApp && bypassMode) {
            DataStore.packages.updateBlocking { it + packageName }
        } else if (!isChinaApp && !bypassMode) {
            DataStore.packages.updateBlocking { it + packageName }
        }
    }

}
