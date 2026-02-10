package fr.husi.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.repository.androidRepo
import fr.husi.utils.AppScanner

class AppChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Logs.d("onReceive: ${intent.action}")
        runOnIoDispatcher {
            checkUpdate(intent)
        }
    }

    private fun checkUpdate(intent: Intent) {
        if (!DataStore.proxyApps) {
            Logs.d("should not check in bypass mode")
            return
        }
        if (!DataStore.updateProxyAppsWhenInstall) {
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
        val isChinaApp = AppScanner.isChinaApp(packageName, androidRepo.packageManager)
        Logs.d("scan china app result for $packageName: $isChinaApp")
        if (isChinaApp && DataStore.bypassMode) {
            DataStore.packages += packageName
        } else if (!isChinaApp && !DataStore.bypassMode) {
            DataStore.packages += packageName
        }
    }

}