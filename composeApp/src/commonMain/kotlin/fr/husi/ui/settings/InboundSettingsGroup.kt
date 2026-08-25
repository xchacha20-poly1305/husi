package fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import fr.husi.compose.IconMaskColors
import fr.husi.compose.collectAsStateWithLifecycle
import fr.husi.compose.MaskedIcon
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PortTextField
import fr.husi.compose.SwitchPreference
import fr.husi.compose.TextFieldPreference
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
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun InboundSettingsGroup(
    needReload: () -> Unit,
) {
    val isExpertState by DataStore.isExpert.collectAsStateWithLifecycle()

    val mixedPort by DataStore.mixedPort.collectAsStateWithLifecycle()
    val mixedPortValue = mixedPort.toString()
    TextFieldPreference(
        value = mixedPortValue,
        onValueChange = {
            DataStore.mixedPort.setBlocking(it.toIntOrNull() ?: 2080)
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

    val localDnsPort by DataStore.localDNSPort.collectAsStateWithLifecycle()
    val localDnsPortValue = localDnsPort.toString()
    TextFieldPreference(
        value = localDnsPortValue,
        onValueChange = {
            DataStore.localDNSPort.setBlocking(it.toIntOrNull() ?: 0)
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

    val appendHttpProxyValue by DataStore.appendHttpProxy.collectAsStateWithLifecycle()
    SwitchPreference(
        value = appendHttpProxyValue,
        onValueChange = {
            DataStore.appendHttpProxy.setBlocking(it)
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

    HttpProxyBypassPreference(appendHttpProxyValue, needReload)

    val allowAccessValue by DataStore.allowAccess.collectAsStateWithLifecycle()
    SwitchPreference(
        value = allowAccessValue,
        onValueChange = {
            DataStore.allowAccess.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.allow_access)) },
        icon = {
            MaskedIcon(Res.drawable.nat, color = IconMaskColors.IconCoral)
        },
        summary = { Text(stringResource(Res.string.allow_access_sum)) },
    )

    val inboundUsernameValue by DataStore.inboundUsername.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = inboundUsernameValue,
        onValueChange = {
            DataStore.inboundUsername.setBlocking(it)
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

    val inboundPasswordValue by DataStore.inboundPassword.collectAsStateWithLifecycle()
    PasswordPreference(
        value = inboundPasswordValue,
        onValueChange = {
            DataStore.inboundPassword.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.inbound_password)) },
    )
    if (isExpertState) {
        val anchorSSIDValue by DataStore.anchorSSID.collectAsStateWithLifecycle()
        TextFieldPreference(
            value = anchorSSIDValue,
            onValueChange = {
                DataStore.anchorSSID.setBlocking(it)
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
