package fr.husi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.husi.database.DataStore
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.showToast
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.launcher_icon_restored

class SecretCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.data?.host != LauncherIcon.SECRET_CODE) return
        if (!LauncherIcon.hidden) return

        val pendingResult = goAsync()
        runOnDefaultDispatcher {
            try {
                LauncherIcon.hidden = false
                DataStore.hideLauncherIcon = false
                showToast(resolveRepository().getString(Res.string.launcher_icon_restored), true)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
