package fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.Preference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.database.DataStore
import fr.husi.resources.Res
import fr.husi.remote.RemoteState
import org.koin.compose.koinInject
import fr.husi.resources.clash_providers
import fr.husi.resources.general_settings
import fr.husi.resources.info
import fr.husi.resources.menu_about
import fr.husi.resources.more
import fr.husi.resources.phonelink_ring
import fr.husi.resources.remote_control
import fr.husi.resources.settings
import fr.husi.resources.tools_network
import fr.husi.resources.wifi
import fr.husi.ui.LocalNavigator
import fr.husi.ui.RemoteRoute
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScreen() {
    val remote = koinInject<RemoteState>()
    val navigator = LocalNavigator.current
    SettingsPage(title = Res.string.settings) {
        preferenceGroup {
            Preference(
                title = { Text(stringResource(Res.string.general_settings)) },
                icon = { MaskedIcon(Res.drawable.settings, color = IconMaskColors.IconLightBlue) },
                onClick = { navigator.navigateTo(RemoteRoute.GeneralSettings) },
            )
        }
        if (remote.isClash) preferenceGroup {
            Preference(title = { Text(stringResource(Res.string.clash_providers)) },
                icon = { MaskedIcon(Res.drawable.settings, IconMaskColors.IconCyan) },
                onClick = { navigator.navigateTo(RemoteRoute.Providers) })

        }
        item { PreferenceCategory(text = { Text(stringResource(Res.string.more)) }) }
        preferenceGroup {
            Preference(
                title = { Text(stringResource(Res.string.remote_control)) },
                icon = { MaskedIcon(Res.drawable.phonelink_ring, IconMaskColors.IconCyan) },
                onClick = { navigator.navigateTo(RemoteRoute.Servers) },
            )
            Preference(
                title = { Text(stringResource(Res.string.tools_network)) },
                icon = { MaskedIcon(Res.drawable.wifi, IconMaskColors.IconLightBlue) },
                onClick = { navigator.navigateTo(RemoteRoute.Tools) },
            )
            Preference(
                title = { Text(stringResource(Res.string.menu_about)) },
                icon = { MaskedIcon(Res.drawable.info, IconMaskColors.IconLavender) },
                onClick = { navigator.navigateTo(RemoteRoute.About) },
            )
        }
    }
}

@Composable
internal fun GeneralSettingsScreen() {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val appTheme by DataStore.appTheme.collectAsStateWithLifecycle()
    val nightTheme by DataStore.nightTheme.collectAsStateWithLifecycle()
    val appLanguage by DataStore.appLanguage.collectAsStateWithLifecycle()
    val speedInterval by DataStore.speedInterval.collectAsStateWithLifecycle()
    val logTerminalMode by DataStore.logTerminalMode.collectAsStateWithLifecycle()

    SettingsPage(
        title = Res.string.general_settings,
        onBackPress = { navigator.popBackStack() },
    ) {
        preferenceGroup {
            AppearanceSettingsGroup(
                appTheme = appTheme,
                onAppThemeChange = { scope.launch { DataStore.appTheme.set(it) } },
                nightTheme = nightTheme,
                onNightThemeChange = { scope.launch { DataStore.nightTheme.set(it) } },
                appLanguage = appLanguage,
                onAppLanguageChange = { scope.launch { DataStore.appLanguage.set(it) } },
                nightModes = listOf(0, 1, 2),
            )
            SpeedIntervalPreference(
                value = speedInterval,
                onValueChange = { scope.launch { DataStore.speedInterval.set(it) } },
            )
            LogTerminalModePreference(
                value = logTerminalMode,
                onValueChange = { scope.launch { DataStore.logTerminalMode.set(it) } },
            )
        }
    }
}
