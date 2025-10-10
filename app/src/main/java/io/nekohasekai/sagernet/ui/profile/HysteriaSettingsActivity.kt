package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MultipleStop
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WbSunny
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

    override fun LazyListScope.settings(state: ProfileSettingsUiState) {
        state as HysteriaUiState

        item("name") {
            TextFieldPreference(
                value = state.name,
                onValueChange = { viewModel.setName(it) },
                title = { Text(stringResource(R.string.profile_name)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.EmojiSymbols, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.name)) },
                valueToText = { it },
            )
        }

        item("protocol_version") {
            ListPreference(
                value = state.protocolVersion,
                values = listOf(HysteriaBean.PROTOCOL_VERSION_1, HysteriaBean.PROTOCOL_VERSION_2),
                onValueChange = { viewModel.setProtocolVersion(it) },
                title = { Text(stringResource(R.string.protocol_version)) },
                icon = { Icon(Icons.Filled.Update, null) },
                summary = { Text(state.protocolVersion.toString()) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it.toString()) },
            )
        }

        item("category_proxy") {
            PreferenceCategory(text = { Text(stringResource(R.string.proxy_cat)) })
        }
        item("address") {
            TextFieldPreference(
                value = state.address,
                onValueChange = { viewModel.setAddress(it) },
                title = { Text(stringResource(R.string.server_address)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Router, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.address)) },
                valueToText = { it },
            )
        }
        item("ports") {
            TextFieldPreference(
                value = state.ports,
                onValueChange = { viewModel.setPorts(it) },
                title = { Text(stringResource(R.string.server_port)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.DirectionsBoat, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.ports)) },
                valueToText = { it },
            )
        }
        item("hop_interval") {
            TextFieldPreference(
                value = state.hopInterval,
                onValueChange = { viewModel.setHopInterval(it) },
                title = { Text(stringResource(R.string.hop_interval)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Timelapse, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.hopInterval)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    DurationTextField(value, onValueChange, onOk)
                },
            )
        }
        item("obfuscation") {
            PasswordPreference(
                value = state.obfuscation,
                onValueChange = { viewModel.setObfuscation(it) },
                title = { Text(stringResource(R.string.hysteria_obfs)) },
                icon = { Icon(Icons.Filled.Texture, null) },
            )
        }
        if (state.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
            item("auth_type") {
                fun authTypeName(type: Int) = when (type) {
                    HysteriaBean.TYPE_NONE -> StringOrRes.Res(R.string.plugin_disabled)
                    HysteriaBean.TYPE_STRING -> StringOrRes.Direct("STRING")
                    HysteriaBean.TYPE_BASE64 -> StringOrRes.Direct("BASE64")
                    else -> error("impossible")
                }
                ListPreference(
                    value = state.authType,
                    values = intListN(3),
                    onValueChange = { viewModel.setAuthType(it) },
                    title = { Text(stringResource(R.string.hysteria_auth_type)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, null) },
                    summary = { Text(stringResource(authTypeName(state.authType))) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(getStringOrRes(authTypeName(it))) },
                )
            }
        }
        if (state.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1 && state.authType != HysteriaBean.TYPE_NONE ||
            state.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2
        ) {
            item("auth_payload") {
                val titleRes = if (state.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2) {
                    R.string.password
                } else {
                    R.string.hysteria_auth_payload
                }
                PasswordPreference(
                    value = state.authPayload,
                    onValueChange = { viewModel.setAuthPayload(it) },
                    title = { Text(stringResource(titleRes)) },
                )
            }
        }
        if (state.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
            item("protocol") {
                ListPreference(
                    value = state.protocol,
                    values = intListN(3),
                    onValueChange = { viewModel.setProtocol(it) },
                    title = { Text(stringResource(R.string.protocol)) },
                    icon = { Icon(Icons.Filled.Layers, null) },
                    summary = { Text(protocolNames[state.protocol]) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(protocolNames[it]) },
                )
            }
        }
        item("sni") {
            TextFieldPreference(
                value = state.sni,
                onValueChange = { viewModel.setSni(it) },
                title = { Text(stringResource(R.string.sni)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Copyright, null) },
                enabled = !state.disableSNI,
                summary = { Text(LocalContext.current.contentOrUnset(state.sni)) },
                valueToText = { it },
            )
        }
        if (state.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
            item("alpn") {
                TextFieldPreference(
                    value = state.alpn,
                    onValueChange = { viewModel.setAlpn(it) },
                    title = { Text(stringResource(R.string.alpn)) },
                    textToValue = { it },
                    icon = { Icon(Icons.AutoMirrored.Filled.Toc, null) },
                    summary = { Text(LocalContext.current.contentOrUnset(state.alpn)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }
        }
        item("certificates") {
            TextFieldPreference(
                value = state.certificates,
                onValueChange = { viewModel.setCertificates(it) },
                title = { Text(stringResource(R.string.certificates)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.VpnKey, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.certificates)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
        item("cert_public_key_sha256") {
            TextFieldPreference(
                value = state.certPublicKeySha256,
                onValueChange = { viewModel.setCertPublicKeySha256(it) },
                title = { Text(stringResource(R.string.cert_public_key_sha256)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.WbSunny, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.certPublicKeySha256)) },
                valueToText = { it },
            )
        }
        item("allow_insecure") {
            SwitchPreference(
                value = state.allowInsecure,
                onValueChange = { viewModel.setAllowInsecure(it) },
                title = { Text(stringResource(R.string.allow_insecure)) },
                summary = { Text(stringResource(R.string.allow_insecure_sum)) },
                icon = { Icon(Icons.Filled.EnhancedEncryption, null) },
            )
        }
        item("disable_sni") {
            SwitchPreference(
                value = state.disableSNI,
                onValueChange = { viewModel.setDisableSNI(it) },
                title = { Text(stringResource(R.string.tuic_disable_sni)) },
                icon = { Icon(Icons.Filled.Block, null) },
            )
        }
        if (state.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
            item("stream_receive_window") {
                TextFieldPreference(
                    value = state.streamReceiveWindow,
                    onValueChange = { viewModel.setStreamReceiveWindow(it) },
                    title = { Text(stringResource(R.string.hysteria_stream_receive_window)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = { Icon(Icons.Filled.Texture, null) },
                    summary = {
                        val text = if (state.streamReceiveWindow == 0) {
                            stringResource(androidx.preference.R.string.not_set)
                        } else {
                            state.streamReceiveWindow.toString()
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
                    value = state.connectionReceiveWindow,
                    onValueChange = { viewModel.setConnectionReceiveWindow(it) },
                    title = { Text(stringResource(R.string.hysteria_connection_receive_window)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = { Icon(Icons.Filled.Transform, null) },
                    summary = {
                        val text = if (state.connectionReceiveWindow == 0) {
                            stringResource(androidx.preference.R.string.not_set)
                        } else {
                            state.connectionReceiveWindow.toString()
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
                    value = state.disableMtuDiscovery,
                    onValueChange = { viewModel.setDisableMtuDiscovery(it) },
                    title = { Text(stringResource(R.string.hysteria_disable_mtu_discovery)) },
                    icon = { Icon(Icons.Filled.MultipleStop, null) },
                )
            }
        }

        if (state.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2) {
            item("category_mtls") {
                PreferenceCategory(text = { Text(stringResource(R.string.mutual_tls)) })
            }
            item("mtls_cert") {
                TextFieldPreference(
                    value = state.mtlsCert,
                    onValueChange = { viewModel.setMtlsCert(it) },
                    title = { Text(stringResource(R.string.certificates)) },
                    textToValue = { it },
                    icon = { Icon(Icons.Filled.Lock, null) },
                    summary = { Text(LocalContext.current.contentOrUnset(state.mtlsCert)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }
            item("mtls_key") {
                TextFieldPreference(
                    value = state.mtlsKey,
                    onValueChange = { viewModel.setMtlsKey(it) },
                    title = { Text(stringResource(R.string.ssh_private_key)) },
                    textToValue = { it },
                    icon = { Icon(Icons.Filled.VpnKey, null) },
                    summary = { Text(LocalContext.current.contentOrUnset(state.mtlsKey)) },
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
                value = state.ech,
                onValueChange = { viewModel.setEch(it) },
                title = { Text(stringResource(R.string.enable)) },
                icon = { Icon(Icons.Filled.Security, null) },
            )
        }
        item("ech_config") {
            TextFieldPreference(
                value = state.echConfig,
                onValueChange = { viewModel.setEchConfig(it) },
                title = { Text(stringResource(R.string.ech_config)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Nfc, null) },
                enabled = state.ech,
                summary = { Text(LocalContext.current.contentOrUnset(state.echConfig)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
    }
}

