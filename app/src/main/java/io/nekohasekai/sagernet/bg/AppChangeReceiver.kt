package io.nekohasekai.sagernet.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.ui.AppManagerActivity

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
        val isChinaApp = AppManagerActivity.isChinaApp(packageName)
        Logs.d("scan china app result for $packageName: $isChinaApp")
        if (isChinaApp && DataStore.bypassMode) {
            DataStore.packages += packageName
        } else if (!isChinaApp && !DataStore.bypassMode) {
            DataStore.packages += packageName
        }
    }

}