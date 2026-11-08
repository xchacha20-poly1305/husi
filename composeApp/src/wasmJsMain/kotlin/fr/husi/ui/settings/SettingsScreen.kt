package fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.Preference
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.database.DataStore
import fr.husi.resources.Res
import fr.husi.resources.info
import fr.husi.resources.menu_about
import fr.husi.resources.settings
import fr.husi.ui.LocalNavigator
import fr.husi.ui.RemoteRoute
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScreen() {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val appTheme by DataStore.appTheme.collectAsStateWithLifecycle()
    val nightTheme by DataStore.nightTheme.collectAsStateWithLifecycle()
    val appLanguage by DataStore.appLanguage.collectAsStateWithLifecycle()
    val speedInterval by DataStore.speedInterval.collectAsStateWithLifecycle()
    val logTerminalMode by DataStore.logTerminalMode.collectAsStateWithLifecycle()

    SettingsPage(title = Res.string.settings) {
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
        preferenceGroup {
            Preference(
                title = { Text(stringResource(Res.string.menu_about)) },
                icon = { MaskedIcon(Res.drawable.info, color = IconMaskColors.IconLavender) },
                onClick = { navigator.navigateTo(RemoteRoute.About) },
            )
        }
    }
}
