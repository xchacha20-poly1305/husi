package fr.husi.ui

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.DEFAULT_HTTP_BYPASS
import fr.husi.Key
import fr.husi.compose.HostTextField
import fr.husi.compose.PreferenceType
import fr.husi.database.DataStore
import fr.husi.ktx.findActivity
import fr.husi.ktx.getColour
import fr.husi.resources.Res
import fr.husi.resources.acquire_wake_lock
import fr.husi.resources.acquire_wake_lock_summary
import fr.husi.resources.allow_apps_bypass_vpn
import fr.husi.resources.auto_connect
import fr.husi.resources.auto_connect_summary
import fr.husi.resources.data_usage
import fr.husi.resources.developer_board
import fr.husi.resources.disable
import fr.husi.resources.disable_process_text
import fr.husi.resources.domain
import fr.husi.resources.format_align_left
import fr.husi.resources.http_proxy_bypass
import fr.husi.resources.label
import fr.husi.resources.legend_toggle
import fr.husi.resources.metered
import fr.husi.resources.metered_summary
import fr.husi.resources.phonelink_ring
import fr.husi.resources.route_opt_bypass_lan
import fr.husi.resources.show_group_in_notification
import fr.husi.resources.shutter_speed
import fr.husi.resources.speed_interval
import fr.husi.resources.transform
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

internal actual fun LazyListScope.autoConnect() {
    item(Key.PERSIST_ACROSS_REBOOT, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.PERSIST_ACROSS_REBOOT, false)
            .collectAsStateWithLifecycle(false)
        SwitchPreference(
            value = value,
            onValueChange = { DataStore.persistAcrossReboot = it },
            title = { Text(stringResource(Res.string.auto_connect)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.phonelink_ring),
                    null,
                )
            },
            summary = { Text(stringResource(Res.string.auto_connect_summary)) },
        )
    }
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

internal actual fun isMemoryLimitSettingSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

internal actual fun LazyListScope.androidGeneralOptions(needReload: () -> Unit) {
    item(Key.ALLOW_APPS_BYPASS_VPN, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.ALLOW_APPS_BYPASS_VPN, false)
            .collectAsStateWithLifecycle(false)
        SwitchPreference(
            value = value,
            onValueChange = {
                DataStore.allowAppsBypassVpn = it
                needReload()
            },
            title = { Text(stringResource(Res.string.allow_apps_bypass_vpn)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.transform),
                    null,
                )
            },
        )
    }
    item(Key.SPEED_INTERVAL, PreferenceType.LIST) {
        fun speedIntervalText(ms: Int): StringOrRes = when (ms) {
            0 -> StringOrRes.Res(Res.string.disable)
            500 -> StringOrRes.Direct("500ms")
            1000 -> StringOrRes.Direct("1s")
            3000 -> StringOrRes.Direct("3s")
            10000 -> StringOrRes.Direct("10s")
            else -> StringOrRes.Direct("1s")
        }

        val values = listOf(0, 500, 1000, 3000, 10000)
        val value by DataStore.configurationStore
            .intFlow(Key.SPEED_INTERVAL, 1000)
            .collectAsStateWithLifecycle(1000)

        ListPreference(
            value = value,
            onValueChange = { DataStore.speedInterval = it },
            values = values,
            title = { Text(stringResource(Res.string.speed_interval)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.shutter_speed),
                    null,
                )
            },
            summary = { Text(stringOrRes(speedIntervalText(value))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                val text = runBlocking { getStringOrRes(speedIntervalText(it)) }
                AnnotatedString(text)
            },
        )
    }
    item(Key.SHOW_GROUP_IN_NOTIFICATION, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.SHOW_GROUP_IN_NOTIFICATION, false)
            .collectAsStateWithLifecycle(false)
        SwitchPreference(
            value = value,
            onValueChange = {
                DataStore.showGroupInNotification = it
                needReload()
            },
            title = { Text(stringResource(Res.string.show_group_in_notification)) },
            icon = { Icon(vectorResource(Res.drawable.label), null) },
        )
    }
}

internal actual fun LazyListScope.meteredNetworkSetting(needReload: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    item(Key.METERED_NETWORK, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.METERED_NETWORK, false)
            .collectAsStateWithLifecycle(false)
        SwitchPreference(
            value = value,
            onValueChange = {
                DataStore.meteredNetwork = it
                needReload()
            },
            title = { Text(stringResource(Res.string.metered)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.data_usage),
                    null,
                )
            },
            summary = { Text(stringResource(Res.string.metered_summary)) },
        )
    }
}

internal actual fun LazyListScope.androidRouteOptions(needReload: () -> Unit) {
    item(Key.BYPASS_LAN, PreferenceType.SWITCH) {
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
                Icon(
                    vectorResource(Res.drawable.legend_toggle),
                    null,
                )
            },
        )
    }
}

internal actual fun LazyListScope.androidMiscOptions(needReload: () -> Unit) {
    item(Key.ACQUIRE_WAKE_LOCK, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.ACQUIRE_WAKE_LOCK, true)
            .collectAsStateWithLifecycle(true)
        SwitchPreference(
            value = value,
            onValueChange = {
                DataStore.acquireWakeLock = it
                needReload()
            },
            title = { Text(stringResource(Res.string.acquire_wake_lock)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.developer_board),
                    null,
                )
            },
            summary = { Text(stringResource(Res.string.acquire_wake_lock_summary)) },
        )
    }
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

internal actual fun LazyListScope.disableProcessText() {
    item(Key.DISABLE_PROCESS_TEXT, PreferenceType.SWITCH) {
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
                Icon(
                    vectorResource(Res.drawable.format_align_left),
                    null,
                )
            },
        )
    }
}

internal actual fun LazyListScope.httpProxyBypass(enabled: Boolean, needReload: () -> Unit) {
    item(Key.HTTP_PROXY_BYPASS, PreferenceType.TEXT_FIELD) {
        val value by DataStore.configurationStore
            .stringFlow(Key.HTTP_PROXY_BYPASS, DEFAULT_HTTP_BYPASS)
            .collectAsStateWithLifecycle(DEFAULT_HTTP_BYPASS)
        TextFieldPreference(
            value = value,
            onValueChange = {
                DataStore.httpProxyBypass = it
                needReload()
            },
            title = { Text(stringResource(Res.string.http_proxy_bypass)) },
            textToValue = { it },
            icon = {
                Icon(
                    vectorResource(Res.drawable.domain),
                    null,
                )
            },
            valueToText = { it },
            enabled = enabled,
        ) { value, onValueChange, onOk ->
            HostTextField(value, onValueChange, onOk)
        }
    }
}