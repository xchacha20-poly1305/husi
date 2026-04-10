package fr.husi.ui

import androidx.compose.foundation.lazy.LazyListScope
import fr.husi.DesktopAutoStart
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.compose.PreferenceType
import fr.husi.compose.ValidatedTextField
import fr.husi.compose.validateTunInterfaceName
import fr.husi.database.DataStore
import fr.husi.platform.PlatformInfo
import fr.husi.resources.Res
import fr.husi.resources.auto_connect_desktop
import fr.husi.resources.auto_connect_summary_desktop
import fr.husi.resources.arrow_and_edge
import fr.husi.resources.phonelink_ring
import fr.husi.resources.tun_interface_name
import fr.husi.resources.tun_interface_name_summary
import fr.husi.resources.tun_auto_redirect
import fr.husi.resources.tun_strict_route
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
            onValueChange = {
                if (DesktopAutoStart.setEnabled(it)) {
                    DataStore.persistAcrossReboot = it
                }
            },
            title = { Text(stringResource(Res.string.auto_connect_desktop)) },
            icon = { Icon(vectorResource(Res.drawable.phonelink_ring), null) },
            summary = { Text(stringResource(Res.string.auto_connect_summary_desktop)) },
        )
    }
}

@Composable
internal actual fun rememberApplyNightMode(): (Int) -> Unit = {}

internal actual fun LazyListScope.platformGeneralOptions(needReload: () -> Unit) {
}

internal actual fun LazyListScope.platformSecurityOptions() {
}

internal actual fun LazyListScope.meteredNetworkSetting(needReload: () -> Unit) {
}

internal actual fun LazyListScope.platformRouteOptions(needReload: () -> Unit, isVpnMode: Boolean) {
    item(Key.TUN_INTERFACE_NAME, PreferenceType.TEXT_FIELD) {
        val value by DataStore.configurationStore
            .stringFlow(Key.TUN_INTERFACE_NAME, "")
            .collectAsStateWithLifecycle("")
        TextFieldPreference(
            value = value,
            onValueChange = {
                DataStore.tunInterfaceName = it
                needReload()
            },
            title = { Text(stringResource(Res.string.tun_interface_name)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.arrow_and_edge), null) },
            summary = {
                val text = value.ifBlank { stringResource(Res.string.tun_interface_name_summary) }
                Text(text)
            },
            valueToText = { it },
            enabled = isVpnMode,
        ) { value, onValueChange, onOk ->
            ValidatedTextField(
                value = value,
                onValueChange = onValueChange,
                onOk = onOk,
                validator = ::validateTunInterfaceName,
            )
        }
    }
    item(Key.TUN_STRICT_ROUTE, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.TUN_STRICT_ROUTE, true)
            .collectAsStateWithLifecycle(true)
        SwitchPreference(
            value = value,
            onValueChange = {
                DataStore.tunStrictRoute = it
                needReload()
            },
            title = { Text(stringResource(Res.string.tun_strict_route)) },
            icon = { Icon(vectorResource(Res.drawable.arrow_and_edge), null) },
            enabled = isVpnMode,
        )
    }
    if (PlatformInfo.isLinux) { // TODO Windows
        item(Key.TUN_AUTO_REDIRECT, PreferenceType.SWITCH) {
            val value by DataStore.configurationStore
                .booleanFlow(Key.TUN_AUTO_REDIRECT, true)
                .collectAsStateWithLifecycle(true)
            SwitchPreference(
                value = value,
                onValueChange = {
                    DataStore.tunAutoRedirect = it
                    needReload()
                },
                title = { Text(stringResource(Res.string.tun_auto_redirect)) },
                icon = { Icon(vectorResource(Res.drawable.arrow_and_edge), null) },
                enabled = isVpnMode,
            )
        }
    }
}

internal actual fun LazyListScope.platformMiscOptions(needReload: () -> Unit) {
}

@Composable
internal actual fun rememberThemeExtraColors(): List<Color> = emptyList()

@Composable
internal actual fun rememberAppLanguageController(defaultTag: String): AppLanguageController {
    val flow = DataStore.configurationStore
        .stringFlow(Key.APP_LANGUAGE, defaultTag)
    val state by flow.collectAsStateWithLifecycle(defaultTag)
    return object : AppLanguageController {
        override var value: String
            get() = state.ifBlank { defaultTag }
            set(value) {
                DataStore.appLanguage = value
            }
        override val flow = flow
    }
}

internal actual fun LazyListScope.disableProcessText() {
}

internal actual fun LazyListScope.httpProxyBypass(enabled: Boolean, needReload: () -> Unit) {
}
