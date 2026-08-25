package fr.husi.ui

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import fr.husi.LauncherIcon
import fr.husi.compose.HostTextField
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.SwitchPreference
import fr.husi.compose.TextButton
import fr.husi.compose.TextFieldPreference
import fr.husi.compose.TwoTargetSwitchPreference
import fr.husi.compose.ValidatedTextField
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.ktx.findActivity
import fr.husi.ktx.getColour
import fr.husi.resources.Res
import fr.husi.resources.acquire_wake_lock
import fr.husi.resources.acquire_wake_lock_summary
import fr.husi.resources.allow_apps_bypass_vpn
import fr.husi.resources.apps
import fr.husi.resources.auto_connect
import fr.husi.resources.auto_connect_summary
import fr.husi.resources.cancel
import fr.husi.resources.data_usage
import fr.husi.resources.developer_board
import fr.husi.resources.disable_process_text
import fr.husi.resources.domain
import fr.husi.resources.format_align_left
import fr.husi.resources.hide_launcher_icon
import fr.husi.resources.hide_launcher_icon_confirm
import fr.husi.resources.hide_launcher_icon_summary
import fr.husi.resources.http_proxy_bypass
import fr.husi.resources.keyboard_tab
import fr.husi.resources.label
import fr.husi.resources.legend_toggle
import fr.husi.resources.metered
import fr.husi.resources.metered_summary
import fr.husi.resources.ok
import fr.husi.resources.phonelink_ring
import fr.husi.resources.privacy
import fr.husi.resources.privacy_mode
import fr.husi.resources.privacy_mode_summary
import fr.husi.resources.proxied_apps
import fr.husi.resources.proxied_apps_summary
import fr.husi.resources.route_opt_bypass_lan
import fr.husi.resources.show_group_in_notification
import fr.husi.resources.transform
import fr.husi.resources.update_proxy_apps_when_install
import fr.husi.resources.visibility_off
import fr.husi.resources.vpn_session_name
import fr.husi.resources.vpn_session_name_summary
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun AutoConnectPreference() {
    val value by DataStore.persistAcrossReboot.collectAsStateWithLifecycle()
    SwitchPreference(
        value = value,
        onValueChange = { DataStore.persistAcrossReboot.setBlocking(it) },
        title = { Text(stringResource(Res.string.auto_connect)) },
        icon = {
            MaskedIcon(
                Res.drawable.phonelink_ring,
                color = IconMaskColors.IconLightPink,
            )
        },
        summary = { Text(stringResource(Res.string.auto_connect_summary)) },
    )
}

@Composable
internal actual fun PlatformDaemonSettingsGroup(showMessage: (String) -> Unit) {
}

@Composable
internal actual fun rememberApplyNightMode(): (Int) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { selection ->
            AppCompatDelegate.setDefaultNightMode(
                when (selection) {
                    0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    2 -> AppCompatDelegate.MODE_NIGHT_NO
                    else -> AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                },
            )
            context.findActivity<Activity>()!!.recreate()
        }
    }
}

@Composable
internal actual fun PlatformGeneralOptions(needReload: () -> Unit) {
    val value by DataStore.vpnSessionName.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = value,
        onValueChange = {
            DataStore.vpnSessionName.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.vpn_session_name)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.label, color = IconMaskColors.IconLightBlue)
        },
        summary = {
            val text = value.ifBlank { stringResource(Res.string.vpn_session_name_summary) }
            Text(text)
        },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        ValidatedTextField(
            value = value,
            onValueChange = onValueChange,
            onOk = onOk,
            validator = { text ->
                if (text.lines().size > 1) {
                    "Unexpected new line"
                } else {
                    null
                }
            },
        )
    }

    val bypassValue by DataStore.allowAppsBypassVpn.collectAsStateWithLifecycle()
    SwitchPreference(
        value = bypassValue,
        onValueChange = {
            DataStore.allowAppsBypassVpn.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.allow_apps_bypass_vpn)) },
        icon = {
            MaskedIcon(Res.drawable.transform, color = IconMaskColors.IconCyan)
        },
    )

    val showGroupValue by DataStore.showGroupInNotification.collectAsStateWithLifecycle()
    SwitchPreference(
        value = showGroupValue,
        onValueChange = {
            DataStore.showGroupInNotification.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.show_group_in_notification)) },
        icon = {
            MaskedIcon(Res.drawable.label, color = IconMaskColors.IconLightPink)
        },
    )
}

@Composable
internal actual fun PlatformRouteOptions(needReload: () -> Unit, isVpnMode: Boolean) {
    val value by DataStore.bypassLan.collectAsStateWithLifecycle()
    SwitchPreference(
        value = value,
        onValueChange = {
            DataStore.bypassLan.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.route_opt_bypass_lan)) },
        icon = {
            MaskedIcon(
                Res.drawable.legend_toggle,
                color = IconMaskColors.IconLightGreen,
            )
        },
    )
}

@Composable
internal actual fun ProxyAppsPreferences(openAppManager: () -> Unit) {
    val value by DataStore.proxyApps.collectAsStateWithLifecycle()
    TwoTargetSwitchPreference(
        value = value,
        onValueChange = {
            DataStore.proxyApps.setBlocking(it)
            if (it) {
                openAppManager()
            }
        },
        title = { Text(stringResource(Res.string.proxied_apps)) },
        icon = {
            MaskedIcon(Res.drawable.apps, color = IconMaskColors.IconCyan)
        },
        summary = { Text(stringResource(Res.string.proxied_apps_summary)) },
        onClick = {
            if (!value) {
                DataStore.proxyApps.setBlocking(true)
            }
            openAppManager()
        },
    )
    val updateValue by DataStore.updateProxyAppsWhenInstall.collectAsStateWithLifecycle()
    SwitchPreference(
        value = updateValue,
        onValueChange = { DataStore.updateProxyAppsWhenInstall.setBlocking(it) },
        title = { Text(stringResource(Res.string.update_proxy_apps_when_install)) },
        icon = {
            MaskedIcon(
                Res.drawable.keyboard_tab,
                color = IconMaskColors.IconLavender,
            )
        },
    )
}

@Composable
internal actual fun PlatformSecurityOptions() {
    val value by DataStore.privacyMode.collectAsStateWithLifecycle()
    SwitchPreference(
        value = value,
        onValueChange = { DataStore.privacyMode.setBlocking(it) },
        title = { Text(stringResource(Res.string.privacy_mode)) },
        icon = {
            MaskedIcon(Res.drawable.privacy, color = IconMaskColors.IconCoral)
        },
        summary = { Text(stringResource(Res.string.privacy_mode_summary)) },
    )
}

@Composable
internal actual fun MeteredNetworkPreference(needReload: () -> Unit) {
    val value by DataStore.meteredNetwork.collectAsStateWithLifecycle()
    SwitchPreference(
        value = value,
        onValueChange = {
            DataStore.meteredNetwork.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.metered)) },
        icon = {
            MaskedIcon(
                Res.drawable.data_usage,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(stringResource(Res.string.metered_summary)) },
    )
}

@Composable
internal actual fun HttpProxyBypassPreference(enabled: Boolean, needReload: () -> Unit) {
    val value by DataStore.httpProxyBypass.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = value,
        onValueChange = {
            DataStore.httpProxyBypass.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.http_proxy_bypass)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.domain, color = IconMaskColors.IconCyan)
        },
        valueToText = { it },
        enabled = enabled,
    ) { value, onValueChange, onOk ->
        HostTextField(value, onValueChange, onOk)
    }
}

@Composable
internal actual fun PlatformMiscOptions(needReload: () -> Unit) {
    val value by DataStore.acquireWakeLock.collectAsStateWithLifecycle()
    SwitchPreference(
        value = value,
        onValueChange = {
            DataStore.acquireWakeLock.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.acquire_wake_lock)) },
        icon = {
            MaskedIcon(
                Res.drawable.developer_board,
                color = IconMaskColors.IconLightGreen,
            )
        },
        summary = { Text(stringResource(Res.string.acquire_wake_lock_summary)) },
    )
}

@Composable
internal actual fun rememberThemeExtraColors(): List<Color> {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Color(context.getColour(android.R.color.system_accent1_600)))
        } else {
            emptyList()
        }
    }
}

@Composable
internal actual fun rememberAppLanguageController(defaultTag: String): AppLanguageController {
    val initialValue = remember(defaultTag) {
        AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { defaultTag }
    }
    return remember {
        object : AppLanguageController {
            override var value: String = initialValue
                set(value) {
                    field = value
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(value),
                    )
                }
            override val flow = flowOf(initialValue)
        }
    }
}

@Composable
internal actual fun DisableProcessTextPreference() {
    val value by DataStore.disableProcessText.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SwitchPreference(
        value = value,
        onValueChange = {
            DataStore.disableProcessText.setBlocking(it)
            context.packageManager.setComponentEnabledSetting(
                ComponentName(
                    context,
                    "fr.husi.ui.ProcessTextActivityAlias",
                ),
                if (it) {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        },
        title = { Text(stringResource(Res.string.disable_process_text)) },
        icon = {
            MaskedIcon(
                Res.drawable.format_align_left,
                color = IconMaskColors.IconWarmGray,
            )
        },
    )
}

@Composable
internal actual fun HideLauncherIconPreference() {
    val value by DataStore.hideLauncherIcon.collectAsStateWithLifecycle()
    var showConfirm by rememberSaveable { mutableStateOf(false) }

    fun setHidden(hidden: Boolean) {
        DataStore.hideLauncherIcon.setBlocking(hidden)
        LauncherIcon.hidden = hidden
    }

    SwitchPreference(
        value = value,
        onValueChange = { hide ->
            if (hide) {
                showConfirm = true
            } else {
                setHidden(false)
            }
        },
        title = { Text(stringResource(Res.string.hide_launcher_icon)) },
        icon = {
            MaskedIcon(
                Res.drawable.visibility_off,
                color = IconMaskColors.IconLavender,
            )
        },
        summary = {
            Text(stringResource(Res.string.hide_launcher_icon_summary, LauncherIcon.DIAL_CODE))
        },
    )

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(Res.string.hide_launcher_icon)) },
            text = {
                Text(stringResource(Res.string.hide_launcher_icon_confirm, LauncherIcon.DIAL_CODE))
            },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    showConfirm = false
                    setHidden(true)
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    showConfirm = false
                }
            },
        )
    }
}
