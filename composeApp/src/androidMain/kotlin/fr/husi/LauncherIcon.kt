package fr.husi

import android.content.ComponentName
import android.content.pm.PackageManager
import fr.husi.repository.resolveAndroidRepository

/**
 * Controls the visibility of the app entry in the system launcher.
 *
 * The launcher intent filters live on an `activity-alias` instead of [fr.husi.ui.MainActivity],
 * so the icon can be hidden by disabling that alias while the activity itself stays usable for
 * deep links, shortcuts and the quick settings tile.
 */
object LauncherIcon {

    /** Class name of the `activity-alias` holding the launcher intent filters. */
    private const val ALIAS = "fr.husi.ui.LauncherActivityAlias"

    /** Digits of the dialer secret code. Keep in sync with the manifest intent filter. */
    const val SECRET_CODE = "231230"

    /** What the user has to dial to bring the icon back. */
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
