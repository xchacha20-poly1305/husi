package fr.husi.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.repository.resolveRepository

class TaskerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskerBundle.ACTION_FIRE_SETTING) return
        val pendingResult = goAsync()
        runOnDefaultDispatcher {
            try {
                if (!DataStore.enableTasker.get()) return@runOnDefaultDispatcher

                val settings = TaskerBundle.fromIntent(intent)
                when (settings.action) {
                    TaskerBundle.ACTION_START -> {
                        var reload = false
                        if (settings.profileId > 0 && DataStore.selectedProxy.get() != settings.profileId) {
                            val profileExists = ProfileManager.getProfile(settings.profileId) != null
                            if (profileExists) {
                                DataStore.selectedProxy.set(settings.profileId)
                                reload = DataStore.currentProfile.get() != 0L
                            }
                        }
                        if (reload) {
                            resolveRepository().reloadService()
                        } else {
                            resolveRepository().startService()
                        }
                    }

                    TaskerBundle.ACTION_STOP -> {
                        resolveRepository().stopService()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
