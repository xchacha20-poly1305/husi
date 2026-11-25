package fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.bg.BrowserNotificationClient
import fr.husi.bg.BrowserNotificationPermission
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.MaskedIcon
import fr.husi.compose.Preference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.SwitchPreference
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.database.DataStore
import fr.husi.remote.RemoteState
import fr.husi.resources.Res
import fr.husi.resources.backup
import fr.husi.resources.clash
import fr.husi.resources.clash_providers
import fr.husi.resources.clash_rules
import fr.husi.resources.clash_smart
import fr.husi.resources.file_export
import fr.husi.resources.general_settings
import fr.husi.resources.info
import fr.husi.resources.layers
import fr.husi.resources.legend_toggle
import fr.husi.resources.menu_about
import fr.husi.resources.more
import fr.husi.resources.notifications
import fr.husi.resources.phonelink_ring
import fr.husi.resources.remote_control
import fr.husi.resources.route
import fr.husi.resources.settings
import fr.husi.resources.tools_network
import fr.husi.resources.web_browser_notifications
import fr.husi.resources.web_browser_notifications_denied
import fr.husi.resources.web_browser_notifications_disabled
import fr.husi.resources.web_browser_notifications_enabled
import fr.husi.resources.web_browser_notifications_unsupported
import fr.husi.resources.wifi
import fr.husi.ui.LocalNavigator
import fr.husi.ui.RemoteRoute
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

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
        if (remote.isClash) {
            item {
                PreferenceCategory(
                    text = { Text(stringResource(Res.string.clash)) },
                )
            }
            preferenceGroup {
                Preference(
                    title = { Text(stringResource(Res.string.clash_rules)) },
                    icon = {
                        MaskedIcon(
                            resource =  Res.drawable.route,
                            color = IconMaskColors.IconLightBlue,
                            shape = IconMaskShapes.route(),
                        )
                    },
                    onClick = { navigator.navigateTo(RemoteRoute.Rules) },
                )
                Preference(
                    title = { Text(stringResource(Res.string.clash_providers)) },
                    icon = { MaskedIcon(Res.drawable.layers, IconMaskColors.IconCyan) },
                    onClick = { navigator.navigateTo(RemoteRoute.Providers) },
                )
                Preference(
                    title = { Text(stringResource(Res.string.clash_smart)) },
                    icon = { MaskedIcon(Res.drawable.legend_toggle, IconMaskColors.IconLightGreen) },
                    onClick = { navigator.navigateTo(RemoteRoute.Smart) },
                )
            }
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
                title = { Text(stringResource(Res.string.backup)) },
                icon = { MaskedIcon(Res.drawable.file_export, IconMaskColors.IconLightYellow) },
                onClick = { navigator.navigateTo(RemoteRoute.Backup) },
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
    val notifications = koinInject<BrowserNotificationClient>()
    val appTheme by DataStore.appTheme.collectAsStateWithLifecycle()
    val nightTheme by DataStore.nightTheme.collectAsStateWithLifecycle()
    val appLanguage by DataStore.appLanguage.collectAsStateWithLifecycle()
    val speedInterval by DataStore.speedInterval.collectAsStateWithLifecycle()
    val logTerminalMode by DataStore.logTerminalMode.collectAsStateWithLifecycle()
    val browserNotificationsEnabled by DataStore.browserNotifications.collectAsStateWithLifecycle()
    val notificationPermission by notifications.permission.collectAsStateWithLifecycle()

    LaunchedEffect(notifications) {
        notifications.refreshPermission()
    }

    val notificationsChecked =
        browserNotificationsEnabled && notificationPermission == BrowserNotificationPermission.GRANTED
    val notificationsAvailable =
        notificationPermission != BrowserNotificationPermission.UNSUPPORTED &&
            notificationPermission != BrowserNotificationPermission.DENIED
    val notificationSummary = when {
        notificationPermission == BrowserNotificationPermission.UNSUPPORTED ->
            Res.string.web_browser_notifications_unsupported

        notificationPermission == BrowserNotificationPermission.DENIED ->
            Res.string.web_browser_notifications_denied

        notificationsChecked -> Res.string.web_browser_notifications_enabled
        else -> Res.string.web_browser_notifications_disabled
    }

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
            SwitchPreference(
                value = notificationsChecked,
                onValueChange = { target ->
                    if (!target) {
                        scope.launch { DataStore.browserNotifications.set(false) }
                    } else if (notificationPermission == BrowserNotificationPermission.GRANTED) {
                        scope.launch { DataStore.browserNotifications.set(true) }
                    } else {
                        notifications.requestPermission { result ->
                            scope.launch {
                                DataStore.browserNotifications.set(
                                    result == BrowserNotificationPermission.GRANTED,
                                )
                            }
                        }
                    }
                },
                enabled = notificationsAvailable,
                title = { Text(stringResource(Res.string.web_browser_notifications)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.notifications,
                        color = IconMaskColors.IconLightYellow,
                    )
                },
                summary = { Text(stringResource(notificationSummary)) },
            )
        }
    }
}
