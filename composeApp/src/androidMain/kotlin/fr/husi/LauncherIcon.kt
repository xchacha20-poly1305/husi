package fr.husi

import android.content.ComponentName
import android.content.pm.PackageManager
import fr.husi.repository.resolveAndroidRepository

object LauncherIcon {

    private const val ALIAS = "fr.husi.ui.LauncherActivityAlias"

    /** Husi's first version release date! */
    const val SECRET_CODE = "231230"

    const val DIAL_CODE = "*#*#$SECRET_CODE#*#*"

    var hidden: Boolean
        get() {
            val repository = resolveAndroidRepository()
            val state = repository.packageManager.getComponentEnabledSetting(
                ComponentName(repository.context.packageName, ALIAS),
            )
            return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        set(value) {
            val repository = resolveAndroidRepository()
            repository.packageManager.setComponentEnabledSetting(
                ComponentName(repository.context.packageName, ALIAS),
                if (value) {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        }
}
