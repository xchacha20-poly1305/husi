package fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PortTextField
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.ktx.contentOrUnset
import fr.husi.platform.PlatformInfo
import fr.husi.resources.Res
import fr.husi.resources.allow_access
import fr.husi.resources.allow_access_sum
import fr.husi.resources.app_registration
import fr.husi.resources.append_http_proxy
import fr.husi.resources.append_http_proxy_sum
import fr.husi.resources.apps
import fr.husi.resources.directions_boat
import fr.husi.resources.inbound_password
import fr.husi.resources.inbound_username
import fr.husi.resources.nat
import fr.husi.resources.person
import fr.husi.resources.port_local_dns
import fr.husi.resources.port_proxy
import fr.husi.resources.wifi
import fr.husi.ui.HttpProxyBypassPreference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun InboundSettingsGroup(
    needReload: () -> Unit,
) {
    val isExpertState by DataStore.configurationStore
        .booleanFlow(Key.APP_EXPERT, false)
        .collectAsStateWithLifecycle(false)

    val mixedPortValue by DataStore.configurationStore
        .stringFlow(Key.MIXED_PORT, "2080")
        .collectAsStateWithLifecycle("2080")
    TextFieldPreference(
        value = mixedPortValue,
        onValueChange = {
            DataStore.mixedPort = it.toIntOrNull() ?: 2080
            needReload()
        },
        title = { Text(stringResource(Res.string.port_proxy)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.directions_boat,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(contentOrUnset(mixedPortValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        PortTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    val localDnsPortValue by DataStore.configurationStore
        .stringFlow(Key.LOCAL_DNS_PORT, "0")
        .collectAsStateWithLifecycle("0")
    TextFieldPreference(
        value = localDnsPortValue,
        onValueChange = {
            DataStore.localDNSPort = it.toIntOrNull() ?: 0
            needReload()
        },
        title = { Text(stringResource(Res.string.port_local_dns)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.apps, color = IconMaskColors.IconWarmGray)
        },
        summary = { Text(contentOrUnset(localDnsPortValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        PortTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    val appendHttpProxyValue by DataStore.configurationStore
        .booleanFlow(Key.APPEND_HTTP_PROXY, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = appendHttpProxyValue,
        onValueChange = {
            DataStore.appendHttpProxy = it
            needReload()
        },
        title = { Text(stringResource(Res.string.append_http_proxy)) },
        icon = {
            MaskedIcon(
                Res.drawable.app_registration,
                color = IconMaskColors.IconLightGreen,
            )
        },
        summary = {
            if (PlatformInfo.isAndroid) {
                Text(stringResource(Res.string.append_http_proxy_sum))
            }
        },
    )
    PreferenceDivider()

    HttpProxyBypassPreference(appendHttpProxyValue, needReload)
    PreferenceDivider()

    val allowAccessValue by DataStore.configurationStore
        .booleanFlow(Key.ALLOW_ACCESS, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = allowAccessValue,
        onValueChange = {
            DataStore.allowAccess = it
            needReload()
        },
        title = { Text(stringResource(Res.string.allow_access)) },
        icon = {
            MaskedIcon(Res.drawable.nat, color = IconMaskColors.IconCoral)
        },
        summary = { Text(stringResource(Res.string.allow_access_sum)) },
    )
    PreferenceDivider()

    val inboundUsernameValue by DataStore.configurationStore
        .stringFlow(Key.INBOUND_USERNAME, "")
        .collectAsStateWithLifecycle("")
    TextFieldPreference(
        value = inboundUsernameValue,
        onValueChange = {
            DataStore.inboundUsername = it
            needReload()
        },
        title = { Text(stringResource(Res.string.inbound_username)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.person, color = IconMaskColors.IconCyan)
        },
        summary = { Text(contentOrUnset(inboundUsernameValue)) },
        valueToText = { it },
    )
    PreferenceDivider()

    val inboundPasswordValue by DataStore.configurationStore
        .stringFlow(Key.INBOUND_PASSWORD, "")
        .collectAsStateWithLifecycle("")
    PasswordPreference(
        value = inboundPasswordValue,
        onValueChange = {
            DataStore.inboundPassword = it
            needReload()
        },
        title = { Text(stringResource(Res.string.inbound_password)) },
    )
    if (isExpertState) {
        PreferenceDivider()
        val anchorSSIDValue by DataStore.configurationStore
            .stringFlow(Key.ANCHOR_SSID, "")
            .collectAsStateWithLifecycle("")
        TextFieldPreference(
            value = anchorSSIDValue,
            onValueChange = {
                DataStore.anchorSSID = it
                needReload()
            },
            title = { Text("Anchor SSIDs") },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.wifi, color = IconMaskColors.IconCoral)
            },
            summary = { Text(contentOrUnset(anchorSSIDValue)) },
            valueToText = { it },
        )
    }
}
