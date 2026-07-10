package fr.husi.ui

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.compose.PreferenceMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.ValidatedTextField
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.ktx.findActivity
import fr.husi.ktx.getColour
import fr.husi.resources.Res
import fr.husi.resources.allow_apps_bypass_vpn
import fr.husi.resources.auto_connect
import fr.husi.resources.auto_connect_summary
import fr.husi.resources.disable_process_text
import fr.husi.resources.format_align_left
import fr.husi.resources.label
import fr.husi.resources.legend_toggle
import fr.husi.resources.phonelink_ring
import fr.husi.resources.route_opt_bypass_lan
import fr.husi.resources.show_group_in_notification
import fr.husi.resources.transform
import fr.husi.resources.vpn_session_name
import fr.husi.resources.vpn_session_name_summary
import kotlinx.coroutines.flow.flowOf
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun AutoConnectPreference() {
    val value by DataStore.configurationStore
        .booleanFlow(Key.PERSIST_ACROSS_REBOOT, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = value,
        onValueChange = { DataStore.persistAcrossReboot = it },
        title = { Text(stringResource(Res.string.auto_connect)) },
        icon = {
            MaskedIcon(
                Res.drawable.phonelink_ring,
                color = PreferenceMaskColors.IconLightPink,
            )
        },
        summary = { Text(stringResource(Res.string.auto_connect_summary)) },
    )
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
    val value by DataStore.configurationStore
        .stringFlow(Key.VPN_SESSION_NAME, "")
        .collectAsStateWithLifecycle("")
    TextFieldPreference(
        value = value,
        onValueChange = {
            DataStore.vpnSessionName = it
            needReload()
        },
        title = { Text(stringResource(Res.string.vpn_session_name)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.label, color = PreferenceMaskColors.IconLightBlue)
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

    val bypassValue by DataStore.configurationStore
        .booleanFlow(Key.ALLOW_APPS_BYPASS_VPN, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = bypassValue,
        onValueChange = {
            DataStore.allowAppsBypassVpn = it
            needReload()
        },
        title = { Text(stringResource(Res.string.allow_apps_bypass_vpn)) },
        icon = {
            MaskedIcon(Res.drawable.transform, color = PreferenceMaskColors.IconCyan)
        },
    )

    val showGroupValue by DataStore.configurationStore
        .booleanFlow(Key.SHOW_GROUP_IN_NOTIFICATION, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = showGroupValue,
        onValueChange = {
            DataStore.showGroupInNotification = it
            needReload()
        },
        title = { Text(stringResource(Res.string.show_group_in_notification)) },
        icon = {
            MaskedIcon(Res.drawable.label, color = PreferenceMaskColors.IconLightPink)
        },
    )
}

@Composable
internal actual fun PlatformRouteOptions(needReload: () -> Unit, isVpnMode: Boolean) {
    val value by DataStore.configurationStore
        .booleanFlow(Key.BYPASS_LAN, true)
        .collectAsStateWithLifecycle(true)
    SwitchPreference(
        value = value,
        onValueChange = {
            DataStore.bypassLan = it
            needReload()
        },
        title = { Text(stringResource(Res.string.route_opt_bypass_lan)) },
        icon = {
            MaskedIcon(
                Res.drawable.legend_toggle,
                color = PreferenceMaskColors.IconLightGreen,
            )
        },
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
    val value by DataStore.configurationStore
        .booleanFlow(Key.DISABLE_PROCESS_TEXT, false)
        .collectAsStateWithLifecycle(false)
    val context = LocalContext.current
    SwitchPreference(
        value = value,
        onValueChange = {
            DataStore.disableProcessText = it
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
                color = PreferenceMaskColors.IconWarmGray,
            )
        },
    )
}
