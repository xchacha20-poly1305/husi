package fr.husi

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import fr.husi.database.DataStore
import fr.husi.repository.resolveAndroidRepository
import fr.husi.repository.resolveRepository

class BootReceiver : BroadcastReceiver() {
    companion object {
        private val componentName by lazy { ComponentName(resolveAndroidRepository().context, BootReceiver::class.java) }
        var enabled: Boolean
            get() = resolveAndroidRepository().packageManager.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            set(value) = resolveAndroidRepository().packageManager.setComponentEnabledSetting(
                componentName,
                if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!DataStore.persistAcrossReboot.getBlocking()) {   // sanity check
            enabled = false
            return
        }

        val doStart = when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> false // DataStore.directBootAware
            else -> resolveAndroidRepository().user.isUserUnlocked
        } && DataStore.selectedProxy.getBlocking() > 0

        if (doStart) resolveRepository().startService()
    }
}
