package io.nekohasekai.sagernet.ui.profile

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.DurationTextField
import io.nekohasekai.sagernet.compose.MultilineTextField
import io.nekohasekai.sagernet.compose.PasswordPreference
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.compose.UIntegerTextField
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import io.nekohasekai.sagernet.ktx.intListN
import io.nekohasekai.sagernet.ui.StringOrRes
import io.nekohasekai.sagernet.ui.getStringOrRes
import io.nekohasekai.sagernet.ui.stringResource
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class HysteriaSettingsActivity : ProfileSettingsActivity<HysteriaBean>() {

    override val viewModel by viewModels<HysteriaSettingsViewModel>()

    private val protocolNames = listOf(
        "UDP",
        "FakeTCP (Root Required)",
        "WeChat Video",
    )

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as HysteriaUiState

        item("name") {
            TextFieldPreference(
                value = uiState.name,
                onValueChange = { viewModel.setName(it) },
                title = { Text(stringResource(R.string.profile_name)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.emoji_symbols), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.name)) },
                valueToText = { it },
            )
        }

        item("protocol_version") {
            ListPreference(
                value = uiState.protocolVersion,
                values = listOf(HysteriaBean.PROTOCOL_VERSION_1, HysteriaBean.PROTOCOL_VERSION_2),
                onValueChange = { viewModel.setProtocolVersion(it) },
                title = { Text(stringResource(R.string.protocol_version)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.update), null) },
                summary = { Text(uiState.protocolVersion.toString()) },
                type = ListPreferenceType.DROPDOWN_MENU,
            )
        }

        item("category_proxy") {
            PreferenceCategory(text = { Text(stringResource(R.string.proxy_cat)) })
        }
        item("address") {
            TextFieldPreference(
                value = uiState.address,
                onValueChange = { viewModel.setAddress(it) },
                title = { Text(stringResource(R.string.server_address)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.router), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.address)) },
                valueToText = { it },
            )
        }
        item("ports") {
            TextFieldPreference(
                value = uiState.ports,
                onValueChange = { viewModel.setPorts(it) },
                title = { Text(stringResource(R.string.server_port)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.directions_boat), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.ports)) },
                valueToText = { it },
            )
        }
        item("hop_interval") {
            TextFieldPreference(
                value = uiState.hopInterval,
                onValueChange = { viewModel.setHopInterval(it) },
                title = { Text(stringResource(R.string.hop_interval)) },
                textToValue = { it },
                enabled = uiState.ports.toIntOrNull() == null,
                icon = { Icon(ImageVector.vectorResource(R.drawable.timelapse), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.hopInterval)) },
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
                title = { Text(stringResource(R.string.hysteria_obfs)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.texture), null) },
            )
        }
        if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
            item("auth_type") {
                fun authTypeName(type: Int) = when (type) {
                    HysteriaBean.TYPE_NONE -> StringOrRes.Res(R.string.plugin_disabled)
                    HysteriaBean.TYPE_STRING -> StringOrRes.Direct("STRING")
                    HysteriaBean.TYPE_BASE64 -> StringOrRes.Direct("BASE64")
                    else -> error("impossible")
                }
                ListPreference(
                    value = uiState.authType,
                    values = intListN(3),
                    onValueChange = { viewModel.setAuthType(it) },
                    title = { Text(stringResource(R.string.hysteria_auth_type)) },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.compare_arrows), null) },
                    summary = { Text(stringResource(authTypeName(uiState.authType))) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(getStringOrRes(authTypeName(it))) },
                )
            }
        }
        if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1 && uiState.authType != HysteriaBean.TYPE_NONE ||
            uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2
        ) {
            item("auth_payload") {
                val titleRes = if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2) {
                    R.string.password
                } else {
                    R.string.hysteria_auth_payload
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
                    title = { Text(stringResource(R.string.protocol)) },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.layers), null) },
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
                title = { Text(stringResource(R.string.sni)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.copyright), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.sni)) },
                valueToText = { it },
            )
        }
        if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
            item("alpn") {
                TextFieldPreference(
                    value = uiState.alpn,
                    onValueChange = { viewModel.setAlpn(it) },
                    title = { Text(stringResource(R.string.alpn)) },
                    textToValue = { it },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.toc), null) },
                    summary = { Text(LocalContext.current.contentOrUnset(uiState.alpn)) },
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
                title = { Text(stringResource(R.string.certificates)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.vpn_key), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.certificates)) },
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
                title = { Text(stringResource(R.string.cert_public_key_sha256)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.wb_sunny), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.certPublicKeySha256)) },
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
                title = { Text(stringResource(R.string.allow_insecure)) },
                summary = { Text(stringResource(R.string.allow_insecure_sum)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.enhanced_encryption), null) },
            )
        }
        item("disable_sni") {
            SwitchPreference(
                value = uiState.disableSNI,
                onValueChange = { viewModel.setDisableSNI(it) },
                title = { Text(stringResource(R.string.tuic_disable_sni)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.block), null) },
            )
        }
        if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
            item("stream_receive_window") {
                TextFieldPreference(
                    value = uiState.streamReceiveWindow,
                    onValueChange = { viewModel.setStreamReceiveWindow(it) },
                    title = { Text(stringResource(R.string.hysteria_stream_receive_window)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.texture), null) },
                    summary = {
                        val text = if (uiState.streamReceiveWindow == 0) {
                            stringResource(R.string.not_set)
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
                    title = { Text(stringResource(R.string.hysteria_connection_receive_window)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.transform), null) },
                    summary = {
                        val text = if (uiState.connectionReceiveWindow == 0) {
                            stringResource(R.string.not_set)
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
                    title = { Text(stringResource(R.string.hysteria_disable_mtu_discovery)) },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.multiple_stop), null) },
                )
            }
        }

        if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2) {
            item("category_mtls") {
                PreferenceCategory(text = { Text(stringResource(R.string.mutual_tls)) })
            }
            item("mtls_cert") {
                TextFieldPreference(
                    value = uiState.clientCert,
                    onValueChange = { viewModel.setClientCert(it) },
                    title = { Text(stringResource(R.string.certificates)) },
                    textToValue = { it },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.lock), null) },
                    summary = { Text(LocalContext.current.contentOrUnset(uiState.clientCert)) },
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
                    title = { Text(stringResource(R.string.ssh_private_key)) },
                    textToValue = { it },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.vpn_key), null) },
                    summary = { Text(LocalContext.current.contentOrUnset(uiState.clientKey)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }
        }

        item("category_ech") {
            PreferenceCategory(text = { Text(stringResource(R.string.ech)) })
        }
        item("ech") {
            SwitchPreference(
                value = uiState.ech,
                onValueChange = { viewModel.setEch(it) },
                title = { Text(stringResource(R.string.enable)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.security), null) },
            )
        }
        item("ech_config") {
            TextFieldPreference(
                value = uiState.echConfig,
                onValueChange = { viewModel.setEchConfig(it) },
                title = { Text(stringResource(R.string.ech_config)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.nfc), null) },
                enabled = uiState.ech,
                summary = { Text(LocalContext.current.contentOrUnset(uiState.echConfig)) },
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
                title = { Text(stringResource(R.string.ech_query_server_name)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.search), null) },
                enabled = uiState.ech,
                summary = { Text(LocalContext.current.contentOrUnset(uiState.echQueryServerName)) },
                valueToText = { it },
            )
        }
    }
}

