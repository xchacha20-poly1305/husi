package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.DurationTextField
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.UIntegerTextField
import fr.husi.fmt.hysteria.HysteriaBean
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.resources.Res
import fr.husi.resources.allow_insecure
import fr.husi.resources.allow_insecure_sum
import fr.husi.resources.alpn
import fr.husi.resources.block
import fr.husi.resources.cert_public_key_sha256
import fr.husi.resources.certificates
import fr.husi.resources.compare_arrows
import fr.husi.resources.copyright
import fr.husi.resources.directions_boat
import fr.husi.resources.ech
import fr.husi.resources.ech_config
import fr.husi.resources.ech_query_server_name
import fr.husi.resources.emoji_symbols
import fr.husi.resources.enable
import fr.husi.resources.enhanced_encryption
import fr.husi.resources.hop_interval
import fr.husi.resources.hysteria_auth_payload
import fr.husi.resources.hysteria_auth_type
import fr.husi.resources.hysteria_connection_receive_window
import fr.husi.resources.hysteria_disable_mtu_discovery
import fr.husi.resources.hysteria_obfs
import fr.husi.resources.hysteria_stream_receive_window
import fr.husi.resources.layers
import fr.husi.resources.lock
import fr.husi.resources.multiple_stop
import fr.husi.resources.mutual_tls
import fr.husi.resources.nfc
import fr.husi.resources.not_set
import fr.husi.resources.password
import fr.husi.resources.plugin_disabled
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.protocol
import fr.husi.resources.protocol_version
import fr.husi.resources.proxy_cat
import fr.husi.resources.router
import fr.husi.resources.search
import fr.husi.resources.security
import fr.husi.resources.server_address
import fr.husi.resources.server_port
import fr.husi.resources.sni
import fr.husi.resources.ssh_private_key
import fr.husi.resources.texture
import fr.husi.resources.timelapse
import fr.husi.resources.toc
import fr.husi.resources.transform
import fr.husi.resources.tuic_disable_sni
import fr.husi.resources.update
import fr.husi.resources.vpn_key
import fr.husi.resources.wb_sunny
import fr.husi.ui.StringOrRes
import fr.husi.ui.getStringOrRes
import fr.husi.ui.stringOrRes
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HysteriaSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: HysteriaSettingsViewModel = viewModel { HysteriaSettingsViewModel() }
    val protocolNames = listOf(
        "UDP",
        "FakeTCP (Root Required)",
        "WeChat Video",
    )

    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
    ) { scope, uiState, _ ->
        scope.hysteriaSettings(uiState as HysteriaUiState, viewModel, protocolNames)
    }
}

private fun LazyListScope.hysteriaSettings(
    uiState: HysteriaUiState,
    viewModel: HysteriaSettingsViewModel,
    protocolNames: List<String>,
) {
    item("name") {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.emoji_symbols), null) },
            summary = { Text(contentOrUnset(uiState.name)) },
            valueToText = { it },
        )
    }

    item("protocol_version") {
        ListPreference(
            value = uiState.protocolVersion,
            values = listOf(HysteriaBean.PROTOCOL_VERSION_1, HysteriaBean.PROTOCOL_VERSION_2),
            onValueChange = { viewModel.setProtocolVersion(it) },
            title = { Text(stringResource(Res.string.protocol_version)) },
            icon = { Icon(vectorResource(Res.drawable.update), null) },
            summary = { Text(uiState.protocolVersion.toString()) },
            type = ListPreferenceType.DROPDOWN_MENU,
        )
    }

    item("category_proxy") {
        PreferenceCategory(text = { Text(stringResource(Res.string.proxy_cat)) })
    }
    item("address") {
        TextFieldPreference(
            value = uiState.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.router), null) },
            summary = { Text(contentOrUnset(uiState.address)) },
            valueToText = { it },
        )
    }
    item("ports") {
        TextFieldPreference(
            value = uiState.ports,
            onValueChange = { viewModel.setPorts(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.directions_boat), null) },
            summary = { Text(contentOrUnset(uiState.ports)) },
            valueToText = { it },
        )
    }
    item("hop_interval") {
        TextFieldPreference(
            value = uiState.hopInterval,
            onValueChange = { viewModel.setHopInterval(it) },
            title = { Text(stringResource(Res.string.hop_interval)) },
            textToValue = { it },
            enabled = uiState.ports.toIntOrNull() == null,
            icon = { Icon(vectorResource(Res.drawable.timelapse), null) },
            summary = { Text(contentOrUnset(uiState.hopInterval)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                DurationTextField(value, onValueChange, onOk)
            },
        )
    }
    item("obfuscation") {
        PasswordPreference(
            value = uiState.obfuscation,
            onValueChange = { viewModel.setObfuscation(it) },
            title = { Text(stringResource(Res.string.hysteria_obfs)) },
            icon = { Icon(vectorResource(Res.drawable.texture), null) },
        )
    }
    if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
        item("auth_type") {
            fun authTypeName(type: Int): StringOrRes = when (type) {
                HysteriaBean.TYPE_NONE -> StringOrRes.Res(Res.string.plugin_disabled)
                HysteriaBean.TYPE_STRING -> StringOrRes.Direct("STRING")
                HysteriaBean.TYPE_BASE64 -> StringOrRes.Direct("BASE64")
                else -> error("impossible")
            }
            ListPreference(
                value = uiState.authType,
                values = intListN(3),
                onValueChange = { viewModel.setAuthType(it) },
                title = { Text(stringResource(Res.string.hysteria_auth_type)) },
                icon = { Icon(vectorResource(Res.drawable.compare_arrows), null) },
                summary = { Text(stringOrRes(authTypeName(uiState.authType))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = {
                    val text = runBlocking { getStringOrRes(authTypeName(it)) }
                    AnnotatedString(text)
                },
            )
        }
    }
    if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1 && uiState.authType != HysteriaBean.TYPE_NONE ||
        uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2
    ) {
        item("auth_payload") {
            val titleRes = if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2) {
                Res.string.password
            } else {
                Res.string.hysteria_auth_payload
            }
            PasswordPreference(
                value = uiState.authPayload,
                onValueChange = { viewModel.setAuthPayload(it) },
                title = { Text(stringResource(titleRes)) },
            )
        }
    }
    if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
        item("protocol") {
            ListPreference(
                value = uiState.protocol,
                values = intListN(3),
                onValueChange = { viewModel.setProtocol(it) },
                title = { Text(stringResource(Res.string.protocol)) },
                icon = { Icon(vectorResource(Res.drawable.layers), null) },
                summary = { Text(protocolNames[uiState.protocol]) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(protocolNames[it]) },
            )
        }
    }
    item("sni") {
        TextFieldPreference(
            value = uiState.sni,
            onValueChange = { viewModel.setSni(it) },
            title = { Text(stringResource(Res.string.sni)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.copyright), null) },
            summary = { Text(contentOrUnset(uiState.sni)) },
            valueToText = { it },
        )
    }
    if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
        item("alpn") {
            TextFieldPreference(
                value = uiState.alpn,
                onValueChange = { viewModel.setAlpn(it) },
                title = { Text(stringResource(Res.string.alpn)) },
                textToValue = { it },
                icon = { Icon(vectorResource(Res.drawable.toc), null) },
                summary = { Text(contentOrUnset(uiState.alpn)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
    }
    item("certificates") {
        TextFieldPreference(
            value = uiState.certificates,
            onValueChange = { viewModel.setCertificates(it) },
            title = { Text(stringResource(Res.string.certificates)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.vpn_key), null) },
            summary = { Text(contentOrUnset(uiState.certificates)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
    item("cert_public_key_sha256") {
        TextFieldPreference(
            value = uiState.certPublicKeySha256,
            onValueChange = { viewModel.setCertPublicKeySha256(it) },
            title = { Text(stringResource(Res.string.cert_public_key_sha256)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.wb_sunny), null) },
            summary = { Text(contentOrUnset(uiState.certPublicKeySha256)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
    item("allow_insecure") {
        SwitchPreference(
            value = uiState.allowInsecure,
            onValueChange = { viewModel.setAllowInsecure(it) },
            title = { Text(stringResource(Res.string.allow_insecure)) },
            summary = { Text(stringResource(Res.string.allow_insecure_sum)) },
            icon = { Icon(vectorResource(Res.drawable.enhanced_encryption), null) },
        )
    }
    item("disable_sni") {
        SwitchPreference(
            value = uiState.disableSNI,
            onValueChange = { viewModel.setDisableSNI(it) },
            title = { Text(stringResource(Res.string.tuic_disable_sni)) },
            icon = { Icon(vectorResource(Res.drawable.block), null) },
        )
    }
    if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
        item("stream_receive_window") {
            TextFieldPreference(
                value = uiState.streamReceiveWindow,
                onValueChange = { viewModel.setStreamReceiveWindow(it) },
                title = { Text(stringResource(Res.string.hysteria_stream_receive_window)) },
                textToValue = { it.toIntOrNull() ?: 0 },
                icon = { Icon(vectorResource(Res.drawable.texture), null) },
                summary = {
                    val text = if (uiState.streamReceiveWindow == 0) {
                        stringResource(Res.string.not_set)
                    } else {
                        uiState.streamReceiveWindow.toString()
                    }
                    Text(text)
                },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                },
            )
        }
        item("connection_receive_window") {
            TextFieldPreference(
                value = uiState.connectionReceiveWindow,
                onValueChange = { viewModel.setConnectionReceiveWindow(it) },
                title = { Text(stringResource(Res.string.hysteria_connection_receive_window)) },
                textToValue = { it.toIntOrNull() ?: 0 },
                icon = { Icon(vectorResource(Res.drawable.transform), null) },
                summary = {
                    val text = if (uiState.connectionReceiveWindow == 0) {
                        stringResource(Res.string.not_set)
                    } else {
                        uiState.connectionReceiveWindow.toString()
                    }
                    Text(text)
                },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                },
            )
        }
        item("disable_mtu_discovery") {
            SwitchPreference(
                value = uiState.disableMtuDiscovery,
                onValueChange = { viewModel.setDisableMtuDiscovery(it) },
                title = { Text(stringResource(Res.string.hysteria_disable_mtu_discovery)) },
                icon = { Icon(vectorResource(Res.drawable.multiple_stop), null) },
            )
        }
    }

    if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2) {
        item("category_mtls") {
            PreferenceCategory(text = { Text(stringResource(Res.string.mutual_tls)) })
        }
        item("mtls_cert") {
            TextFieldPreference(
                value = uiState.clientCert,
                onValueChange = { viewModel.setClientCert(it) },
                title = { Text(stringResource(Res.string.certificates)) },
                textToValue = { it },
                icon = { Icon(vectorResource(Res.drawable.lock), null) },
                summary = { Text(contentOrUnset(uiState.clientCert)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
        item("mtls_key") {
            TextFieldPreference(
                value = uiState.clientKey,
                onValueChange = { viewModel.setClientKey(it) },
                title = { Text(stringResource(Res.string.ssh_private_key)) },
                textToValue = { it },
                icon = { Icon(vectorResource(Res.drawable.vpn_key), null) },
                summary = { Text(contentOrUnset(uiState.clientKey)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
    }

    item("category_ech") {
        PreferenceCategory(text = { Text(stringResource(Res.string.ech)) })
    }
    item("ech") {
        SwitchPreference(
            value = uiState.ech,
            onValueChange = { viewModel.setEch(it) },
            title = { Text(stringResource(Res.string.enable)) },
            icon = { Icon(vectorResource(Res.drawable.security), null) },
        )
    }
    item("ech_config") {
        TextFieldPreference(
            value = uiState.echConfig,
            onValueChange = { viewModel.setEchConfig(it) },
            title = { Text(stringResource(Res.string.ech_config)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.nfc), null) },
            enabled = uiState.ech,
            summary = { Text(contentOrUnset(uiState.echConfig)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
    item("ech_query_server_name") {
        TextFieldPreference(
            value = uiState.echQueryServerName,
            onValueChange = { viewModel.setEchQueryServerName(it) },
            title = { Text(stringResource(Res.string.ech_query_server_name)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.search), null) },
            enabled = uiState.ech,
            summary = { Text(contentOrUnset(uiState.echQueryServerName)) },
            valueToText = { it },
        )
    }
}
