package fr.husi.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import fr.husi.compose.DurationTextField
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.PreferenceMaskColors
import fr.husi.compose.PreferenceShapes
import fr.husi.compose.MaskedIcon
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.ValidatedTextField
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.fmt.hysteria.HysteriaBean
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.ktx.readableMessage
import fr.husi.libcore.Libcore
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
import fr.husi.resources.hysteria2_gecko_max_packet_size
import fr.husi.resources.hysteria2_gecko_min_packet_size
import fr.husi.resources.hysteria2_obfs_type
import fr.husi.resources.hysteria_auth_payload
import fr.husi.resources.hysteria_auth_type
import fr.husi.resources.hysteria_bbr_profile
import fr.husi.resources.hysteria_bbr_profile_aggressive
import fr.husi.resources.hysteria_bbr_profile_conservative
import fr.husi.resources.hysteria_bbr_profile_standard
import fr.husi.resources.hysteria_hop_interval_range_hint
import fr.husi.resources.hysteria_obfs
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
import fr.husi.resources.quic
import fr.husi.resources.quic_connection_receive_window
import fr.husi.resources.quic_disable_path_mtu_discovery
import fr.husi.resources.quic_idle_timeout
import fr.husi.resources.quic_initial_packet_size
import fr.husi.resources.quic_keep_alive_period
import fr.husi.resources.quic_max_concurrent_streams
import fr.husi.resources.quic_stream_receive_window
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
import fr.husi.resources.tuic_congestion_controller
import fr.husi.resources.tuic_disable_sni
import fr.husi.resources.type_specimen
import fr.husi.resources.update
import fr.husi.resources.vpn_key
import fr.husi.resources.wb_sunny
import fr.husi.ui.NavRoutes
import fr.husi.ui.StringOrRes
import fr.husi.ui.stringOrRes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HysteriaSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: HysteriaSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        HysteriaSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        hysteriaSettings(uiState as HysteriaUiState, viewModel)
    }
}

private fun LazyListScope.hysteriaSettings(
    uiState: HysteriaUiState,
    viewModel: HysteriaSettingsViewModel,
) {
    preferenceGroup(key = "name") {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.emoji_symbols,
                    color = PreferenceMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.name)) },
            valueToText = { it },
        )
        PreferenceDivider()
        ListPreference(
            value = uiState.protocolVersion,
            values = listOf(HysteriaBean.PROTOCOL_VERSION_1, HysteriaBean.PROTOCOL_VERSION_2),
            onValueChange = { viewModel.setProtocolVersion(it) },
            title = { Text(stringResource(Res.string.protocol_version)) },
            icon = {
                MaskedIcon(Res.drawable.update, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(uiState.protocolVersion.toString()) },
            type = ListPreferenceType.DROPDOWN_MENU,
        )
    }

    item("category_proxy") {
        PreferenceCategory(text = { Text(stringResource(Res.string.proxy_cat)) })
    }
    preferenceGroup(key = "address") {
        TextFieldPreference(
            value = uiState.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.router, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.address)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.ports,
            onValueChange = { viewModel.setPorts(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.directions_boat,
                    color = PreferenceMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.ports)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.hopInterval,
            onValueChange = { viewModel.setHopInterval(it) },
            title = { Text(stringResource(Res.string.hop_interval)) },
            textToValue = { it },
            enabled = uiState.ports.toIntOrNull() == null,
            icon = {
                MaskedIcon(Res.drawable.timelapse, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.hopInterval)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                HopIntervalTextField(
                    value = value,
                    onValueChange = onValueChange,
                    onOk = onOk,
                    supportRange = uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2,
                )
            },
        )
    }
    if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2) {
        preferenceGroup(key = "obfs_type") {
            fun obfsTypeName(type: String): StringOrRes = when (type) {
                HysteriaBean.OBFS_TYPE_NONE -> StringOrRes.Res(Res.string.plugin_disabled)
                HysteriaBean.OBFS_TYPE_SALAMANDER -> StringOrRes.Direct("Salamander")
                HysteriaBean.OBFS_TYPE_GECKO -> StringOrRes.Direct("Gecko")
                else -> StringOrRes.Direct(type)
            }
            ListPreference(
                value = uiState.obfsType,
                values = listOf(
                    HysteriaBean.OBFS_TYPE_NONE,
                    HysteriaBean.OBFS_TYPE_SALAMANDER,
                    HysteriaBean.OBFS_TYPE_GECKO,
                ),
                onValueChange = { viewModel.setObfsType(it) },
                title = { Text(stringResource(Res.string.hysteria2_obfs_type)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.type_specimen,
                        color = PreferenceMaskColors.IconCyan,
                    )
                },
                summary = { Text(stringOrRes(obfsTypeName(uiState.obfsType))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(stringOrRes(obfsTypeName(it))) },
            )
        }
        if (uiState.obfsType == HysteriaBean.OBFS_TYPE_GECKO) {
            preferenceGroup(key = "gecko_min_packet_size") {
                TextFieldPreference(
                    value = uiState.geckoMinPacketSize,
                    onValueChange = { viewModel.setGeckoMinPacketSize(it) },
                    title = { Text(stringResource(Res.string.hysteria2_gecko_min_packet_size)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = {
                        MaskedIcon(
                            Res.drawable.texture,
                            color = PreferenceMaskColors.IconCyan,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.geckoMinPacketSize)) },
                    valueToText = { it.toString() },
                    textField = { value, onValueChange, onOk ->
                        UIntegerTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = uiState.geckoMaxPacketSize,
                    onValueChange = { viewModel.setGeckoMaxPacketSize(it) },
                    title = { Text(stringResource(Res.string.hysteria2_gecko_max_packet_size)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = { Spacer(Modifier.size(24.dp)) },
                    summary = { Text(contentOrUnset(uiState.geckoMaxPacketSize)) },
                    valueToText = { it.toString() },
                    textField = { value, onValueChange, onOk ->
                        UIntegerTextField(value, onValueChange, onOk)
                    },
                )
            }
        }
    }
    preferenceGroup(key = "obfuscation") {
        PasswordPreference(
            value = uiState.obfsPassword,
            onValueChange = { viewModel.setObfsPassword(it) },
            title = { Text(stringResource(Res.string.hysteria_obfs)) },
            enabled = uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1
                    || uiState.obfsType != HysteriaBean.OBFS_TYPE_NONE,
            icon = {
                MaskedIcon(Res.drawable.texture, color = PreferenceMaskColors.IconCyan)
            },
        )
    }
    if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
        preferenceGroup(key = "auth_type") {
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
                icon = {
                    MaskedIcon(
                        Res.drawable.compare_arrows,
                        color = PreferenceMaskColors.IconCyan,
                    )
                },
                summary = { Text(stringOrRes(authTypeName(uiState.authType))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(stringOrRes(authTypeName(it))) },
            )
        }
    }
    if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1 && uiState.authType != HysteriaBean.TYPE_NONE ||
        uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2
    ) {
        preferenceGroup(key = "auth_payload") {
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
        preferenceGroup(key = "protocol") {
            val protocolNames = remember {
                listOf(
                    "UDP",
                    "FakeTCP (Root Required)",
                    "WeChat Video",
                )
            }
            ListPreference(
                value = uiState.protocol,
                values = intListN(3),
                onValueChange = { viewModel.setProtocol(it) },
                title = { Text(stringResource(Res.string.protocol)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.layers,
                        color = PreferenceMaskColors.IconCyan,
                    )
                },
                summary = { Text(protocolNames[uiState.protocol]) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(protocolNames[it]) },
            )
        }
    }
    preferenceGroup(key = "sni") {
        TextFieldPreference(
            value = uiState.sni,
            onValueChange = { viewModel.setSni(it) },
            title = { Text(stringResource(Res.string.sni)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.copyright, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.sni)) },
            valueToText = { it },
        )
    }
    if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
        preferenceGroup(key = "alpn") {
            TextFieldPreference(
                value = uiState.alpn,
                onValueChange = { viewModel.setAlpn(it) },
                title = { Text(stringResource(Res.string.alpn)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(Res.drawable.toc, color = PreferenceMaskColors.IconCyan)
                },
                summary = { Text(contentOrUnset(uiState.alpn)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
    }
    preferenceGroup(key = "certificates") {
        TextFieldPreference(
            value = uiState.certificates,
            onValueChange = { viewModel.setCertificates(it) },
            title = { Text(stringResource(Res.string.certificates)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.vpn_key,
                    color = PreferenceMaskColors.IconCyan,
                    shape = PreferenceShapes.credential(),
                )
            },
            summary = { Text(contentOrUnset(uiState.certificates)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.certPublicKeySha256,
            onValueChange = { viewModel.setCertPublicKeySha256(it) },
            title = { Text(stringResource(Res.string.cert_public_key_sha256)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.wb_sunny, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.certPublicKeySha256)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        SwitchPreference(
            value = uiState.allowInsecure,
            onValueChange = { viewModel.setAllowInsecure(it) },
            title = { Text(stringResource(Res.string.allow_insecure)) },
            summary = { Text(stringResource(Res.string.allow_insecure_sum)) },
            icon = {
                MaskedIcon(
                    Res.drawable.enhanced_encryption,
                    color = PreferenceMaskColors.IconCyan,
                    shape = PreferenceShapes.risk(),
                )
            },
        )
        PreferenceDivider()
        SwitchPreference(
            value = uiState.disableSNI,
            onValueChange = { viewModel.setDisableSNI(it) },
            title = { Text(stringResource(Res.string.tuic_disable_sni)) },
            icon = {
                MaskedIcon(Res.drawable.block, color = PreferenceMaskColors.IconCyan)
            },
        )
    }
    item("category_quic") {
        PreferenceCategory(text = { Text(stringResource(Res.string.quic)) })
    }
    preferenceGroup(key = "stream_receive_window") {
        TextFieldPreference(
            value = uiState.streamReceiveWindow,
            onValueChange = { viewModel.setStreamReceiveWindow(it) },
            title = { Text(stringResource(Res.string.quic_stream_receive_window)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(Res.drawable.texture, color = PreferenceMaskColors.IconCyan)
            },
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
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.connectionReceiveWindow,
            onValueChange = { viewModel.setConnectionReceiveWindow(it) },
            title = { Text(stringResource(Res.string.quic_connection_receive_window)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(Res.drawable.transform, color = PreferenceMaskColors.IconCyan)
            },
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
        PreferenceDivider()
        SwitchPreference(
            value = uiState.disableMtuDiscovery,
            onValueChange = { viewModel.setDisableMtuDiscovery(it) },
            title = { Text(stringResource(Res.string.quic_disable_path_mtu_discovery)) },
            icon = {
                MaskedIcon(
                    Res.drawable.multiple_stop,
                    color = PreferenceMaskColors.IconCyan,
                )
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.idleTimeout,
            onValueChange = { viewModel.setIdleTimeout(it) },
            title = { Text(stringResource(Res.string.quic_idle_timeout)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.timelapse, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.idleTimeout)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                DurationTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.keepAlivePeriod,
            onValueChange = { viewModel.setKeepAlivePeriod(it) },
            title = { Text(stringResource(Res.string.quic_keep_alive_period)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.timelapse, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.keepAlivePeriod)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                DurationTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.maxConcurrentStreams,
            onValueChange = { viewModel.setMaxConcurrentStreams(it) },
            title = { Text(stringResource(Res.string.quic_max_concurrent_streams)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(Res.drawable.transform, color = PreferenceMaskColors.IconCyan)
            },
            summary = {
                val text = if (uiState.maxConcurrentStreams == 0) {
                    stringResource(Res.string.not_set)
                } else {
                    uiState.maxConcurrentStreams.toString()
                }
                Text(text)
            },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.initialPacketSize,
            onValueChange = { viewModel.setInitialPacketSize(it) },
            title = { Text(stringResource(Res.string.quic_initial_packet_size)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(Res.drawable.texture, color = PreferenceMaskColors.IconCyan)
            },
            summary = {
                val text = if (uiState.initialPacketSize == 0) {
                    stringResource(Res.string.not_set)
                } else {
                    uiState.initialPacketSize.toString()
                }
                Text(text)
            },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }

    if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2) {
        item("category_mtls") {
            PreferenceCategory(text = { Text(stringResource(Res.string.mutual_tls)) })
        }
        preferenceGroup(key = "mtls_cert") {
            TextFieldPreference(
                value = uiState.clientCert,
                onValueChange = { viewModel.setClientCert(it) },
                title = { Text(stringResource(Res.string.certificates)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.lock,
                        color = PreferenceMaskColors.IconCyan,
                        shape = PreferenceShapes.credential(),
                    )
                },
                summary = { Text(contentOrUnset(uiState.clientCert)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.clientKey,
                onValueChange = { viewModel.setClientKey(it) },
                title = { Text(stringResource(Res.string.ssh_private_key)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.vpn_key,
                        color = PreferenceMaskColors.IconCyan,
                        shape = PreferenceShapes.credential(),
                    )
                },
                summary = { Text(contentOrUnset(uiState.clientKey)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            val hysteriaCongestionControls = remember {
                listOf(
                    HysteriaBean.CONGESTION_CONTROL_BBR,
                    HysteriaBean.CONGESTION_CONTROL_RENO,
                )
            }

            fun congestionControlName(control: String): String = when (control) {
                HysteriaBean.CONGESTION_CONTROL_BBR -> "BBR"
                HysteriaBean.CONGESTION_CONTROL_RENO -> "Reno"
                else -> error("impossible")
            }
            ListPreference(
                value = uiState.congestionControl,
                values = hysteriaCongestionControls,
                onValueChange = { viewModel.setCongestionControl(it) },
                title = { Text(stringResource(Res.string.tuic_congestion_controller)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.compare_arrows,
                        color = PreferenceMaskColors.IconCyan,
                    )
                },
                summary = { Text(congestionControlName(uiState.congestionControl)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(congestionControlName(it)) },
            )
        }
        if (uiState.congestionControl == HysteriaBean.CONGESTION_CONTROL_BBR) {
            fun bbrProfileName(profile: Int): StringResource = when (profile) {
                HysteriaBean.BBR_PROFILE_CONSERVATIVE -> Res.string.hysteria_bbr_profile_conservative
                HysteriaBean.BBR_PROFILE_STANDARD -> Res.string.hysteria_bbr_profile_standard
                HysteriaBean.BBR_PROFILE_AGGRESSIVE -> Res.string.hysteria_bbr_profile_aggressive
                else -> error("impossible")
            }
            preferenceGroup(key = "bbr_profile") {
                ListPreference(
                    value = uiState.bbrProfile,
                    values = intListN(3),
                    onValueChange = { viewModel.setBBRProfile(it) },
                    title = { Text(stringResource(Res.string.hysteria_bbr_profile)) },
                    icon = {
                        MaskedIcon(
                            Res.drawable.transform,
                            color = PreferenceMaskColors.IconCyan,
                        )
                    },
                    summary = { Text(stringResource(bbrProfileName(uiState.bbrProfile))) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(stringResource(bbrProfileName(it))) },
                )
            }
        }
    }

    item("category_ech") {
        PreferenceCategory(text = { Text(stringResource(Res.string.ech)) })
    }
    preferenceGroup(key = "ech") {
        SwitchPreference(
            value = uiState.ech,
            onValueChange = { viewModel.setEch(it) },
            title = { Text(stringResource(Res.string.enable)) },
            icon = {
                MaskedIcon(Res.drawable.security, color = PreferenceMaskColors.IconCyan)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.echConfig,
            onValueChange = { viewModel.setEchConfig(it) },
            title = { Text(stringResource(Res.string.ech_config)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.nfc, color = PreferenceMaskColors.IconCyan)
            },
            enabled = uiState.ech,
            summary = { Text(contentOrUnset(uiState.echConfig)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.echQueryServerName,
            onValueChange = { viewModel.setEchQueryServerName(it) },
            title = { Text(stringResource(Res.string.ech_query_server_name)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.search, color = PreferenceMaskColors.IconCyan)
            },
            enabled = uiState.ech,
            summary = { Text(contentOrUnset(uiState.echQueryServerName)) },
            valueToText = { it },
        )
    }
}

@Composable
private fun HopIntervalTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onOk: () -> Unit,
    supportRange: Boolean,
) {
    if (!supportRange) {
        DurationTextField(value, onValueChange, onOk)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(Res.string.hysteria_hop_interval_range_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        ValidatedTextField(
            value = value,
            onValueChange = onValueChange,
            onOk = onOk,
            validator = { text ->
                when {
                    text.isBlank() -> null
                    text.lines().size > 1 -> "Unexpected new line"
                    text.count { it == '-' } > 1 -> "Only one '-' is allowed"
                    else -> {
                        val parts = text.split("-", limit = 2)
                        if (parts.any { it.isBlank() }) {
                            "Duration range is incomplete"
                        } else try {
                            for (part in parts) {
                                Libcore.parseDuration(part)
                            }
                            null
                        } catch (e: Exception) {
                            e.readableMessage
                        }
                    }
                }
            },
        )
    }
}
