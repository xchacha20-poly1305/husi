package fr.husi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.DesktopAutoStart
import fr.husi.Key
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.ValidatedTextField
import fr.husi.compose.material3.Text
import fr.husi.compose.validateTunInterfaceName
import fr.husi.database.DataStore
import fr.husi.platform.PlatformInfo
import fr.husi.resources.Res
import fr.husi.resources.arrow_and_edge
import fr.husi.resources.auto_connect_desktop
import fr.husi.resources.auto_connect_summary_desktop
import fr.husi.resources.phonelink_ring
import fr.husi.resources.tun_auto_redirect
import fr.husi.resources.tun_interface_name
import fr.husi.resources.tun_interface_name_summary
import fr.husi.resources.tun_strict_route
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
        onValueChange = {
            if (DesktopAutoStart.setEnabled(it)) {
                DataStore.persistAcrossReboot = it
            }
        },
        title = { Text(stringResource(Res.string.auto_connect_desktop)) },
        icon = {
            MaskedIcon(
                Res.drawable.phonelink_ring,
                color = IconMaskColors.IconLightPink,
            )
        },
        summary = { Text(stringResource(Res.string.auto_connect_summary_desktop)) },
    )
}

@Composable
internal actual fun rememberApplyNightMode(): (Int) -> Unit = {}

@Composable
internal actual fun PlatformGeneralOptions(needReload: () -> Unit) {
}

@Composable
internal actual fun PlatformRouteOptions(needReload: () -> Unit, isVpnMode: Boolean) {
    val tunInterfaceNameValue by DataStore.configurationStore
        .stringFlow(Key.TUN_INTERFACE_NAME, "")
        .collectAsStateWithLifecycle("")
    TextFieldPreference(
        value = tunInterfaceNameValue,
        onValueChange = {
            DataStore.tunInterfaceName = it
            needReload()
        },
        title = { Text(stringResource(Res.string.tun_interface_name)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.arrow_and_edge,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = {
            val text = tunInterfaceNameValue.ifBlank {
                stringResource(Res.string.tun_interface_name_summary)
            }
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

    val strictRouteValue by DataStore.configurationStore
        .booleanFlow(Key.TUN_STRICT_ROUTE, true)
        .collectAsStateWithLifecycle(true)
    SwitchPreference(
        value = strictRouteValue,
        onValueChange = {
            DataStore.tunStrictRoute = it
            needReload()
        },
        title = { Text(stringResource(Res.string.tun_strict_route)) },
        icon = {
            MaskedIcon(
                Res.drawable.arrow_and_edge,
                color = IconMaskColors.IconCyan,
            )
        },
        enabled = isVpnMode,
    )
    if (PlatformInfo.isLinux) {
        val autoRedirectValue by DataStore.configurationStore
            .booleanFlow(Key.TUN_AUTO_REDIRECT, true)
            .collectAsStateWithLifecycle(true)
        SwitchPreference(
            value = autoRedirectValue,
            onValueChange = {
                DataStore.tunAutoRedirect = it
                needReload()
            },
            title = { Text(stringResource(Res.string.tun_auto_redirect)) },
            icon = {
                MaskedIcon(
                    Res.drawable.arrow_and_edge,
                    color = IconMaskColors.IconLightGreen,
                )
            },
            enabled = isVpnMode,
        )
    }
}

@Composable
internal actual fun ProxyAppsPreferences(openAppManager: () -> Unit) {
}

@Composable
internal actual fun PlatformSecurityOptions() {
}

@Composable
internal actual fun MeteredNetworkPreference(needReload: () -> Unit) {
}

@Composable
internal actual fun HttpProxyBypassPreference(enabled: Boolean, needReload: () -> Unit) {
}

@Composable
internal actual fun PlatformMiscOptions(needReload: () -> Unit) {
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

@Composable
internal actual fun DisableProcessTextPreference() {
}

@Composable
internal actual fun HideLauncherIconPreference() {
}
