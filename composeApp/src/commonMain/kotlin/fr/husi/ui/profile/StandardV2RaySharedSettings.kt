package fr.husi.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.MaskedIcon
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PortTextField
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.platform.PlatformInfo
import fr.husi.resources.Res
import fr.husi.resources.allow_insecure
import fr.husi.resources.allow_insecure_sum
import fr.husi.resources.alpn
import fr.husi.resources.assistant_direction
import fr.husi.resources.block
import fr.husi.resources.bolt
import fr.husi.resources.border_inner
import fr.husi.resources.cert_public_key_sha256
import fr.husi.resources.certificates
import fr.husi.resources.client_certificate
import fr.husi.resources.client_key
import fr.husi.resources.code
import fr.husi.resources.compare_arrows
import fr.husi.resources.computer_cancel
import fr.husi.resources.copyright
import fr.husi.resources.directions_boat
import fr.husi.resources.domino_mask
import fr.husi.resources.early_data_header_name
import fr.husi.resources.ech
import fr.husi.resources.ech_config
import fr.husi.resources.ech_query_server_name
import fr.husi.resources.emoji_symbols
import fr.husi.resources.enable
import fr.husi.resources.enable_brutal
import fr.husi.resources.enhanced_encryption
import fr.husi.resources.grpc_service_name
import fr.husi.resources.http_headers
import fr.husi.resources.http_host
import fr.husi.resources.http_path
import fr.husi.resources.http_upgrade_host
import fr.husi.resources.http_upgrade_path
import fr.husi.resources.language
import fr.husi.resources.layers
import fr.husi.resources.lock
import fr.husi.resources.multiple_stop
import fr.husi.resources.mutual_tls
import fr.husi.resources.mux_number
import fr.husi.resources.mux_preference
import fr.husi.resources.mux_strategy
import fr.husi.resources.mux_type
import fr.husi.resources.nfc
import fr.husi.resources.numbers
import fr.husi.resources.padding
import fr.husi.resources.profile_name
import fr.husi.resources.proxy_cat
import fr.husi.resources.reality_public_key
import fr.husi.resources.reality_short_id
import fr.husi.resources.route
import fr.husi.resources.router
import fr.husi.resources.search
import fr.husi.resources.security
import fr.husi.resources.security_settings
import fr.husi.resources.server_address
import fr.husi.resources.server_port
import fr.husi.resources.sni
import fr.husi.resources.stream
import fr.husi.resources.texture
import fr.husi.resources.timer
import fr.husi.resources.tls_camouflage_settings
import fr.husi.resources.tls_fragment
import fr.husi.resources.tls_fragment_fallback_delay
import fr.husi.resources.tls_record_fragment
import fr.husi.resources.tls_spoof
import fr.husi.resources.tls_spoof_method
import fr.husi.resources.toc
import fr.husi.resources.tuic_disable_sni
import fr.husi.resources.type_specimen
import fr.husi.resources.utls_fingerprint
import fr.husi.resources.v2ray_transport
import fr.husi.resources.view_in_ar
import fr.husi.resources.vpn_key
import fr.husi.resources.wb_sunny
import fr.husi.resources.ws_host
import fr.husi.resources.ws_max_early_data
import fr.husi.resources.ws_path
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

private const val KEY_SECURITY = "security"

internal fun LazyListScope.headSettings(
    state: StandardV2RayUiState,
    viewModel: StandardV2RaySettingsViewModel<*>,
) {
    item("category_basic") {
        PreferenceCategory(text = { Text(stringResource(Res.string.proxy_cat)) })
    }
    preferenceGroup {
        TextFieldPreference(
            value = state.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.emoji_symbols,
                    color = IconMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(state.name)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.router,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(state.address)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = state.port,
            onValueChange = { viewModel.setPort(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull() ?: 443 },
            icon = {
                MaskedIcon(
                    Res.drawable.directions_boat,
                    color = IconMaskColors.IconLightOrange,
                )
            },
            summary = { Text(contentOrUnset(state.port)) },
            textField = { value, onValueChange, onOk -> PortTextField(value, onValueChange, onOk) },
        )
    }
}

internal fun LazyListScope.tlsSettings(
    state: StandardV2RayUiState,
    viewModel: StandardV2RaySettingsViewModel<*>,
    scrollTo: (key: String) -> Unit,
) {
    val isTls = state.security == "tls"
    val isReality = state.realityPublicKey.isNotBlank()

    item("category_security") {
        PreferenceCategory(text = { Text(stringResource(Res.string.security_settings)) })
    }
    preferenceGroup(key = KEY_SECURITY) {
        ListPreference(
            value = state.security,
            values = listOf("", "tls"),
            onValueChange = {
                viewModel.setSecurity(it)
                if (it == "tls") {
                    scrollTo(KEY_SECURITY)
                }
            },
            title = { Text(stringResource(Res.string.security)) },
            icon = {
                MaskedIcon(
                    Res.drawable.layers,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = { Text(contentOrUnset(state.security)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }

    if (isTls) {
        preferenceGroup {
            TextFieldPreference(
                value = state.sni,
                onValueChange = { viewModel.setSni(it) },
                title = { Text(stringResource(Res.string.sni)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.copyright,
                        color = IconMaskColors.IconCyan,
                    )
                },
                summary = { Text(contentOrUnset(state.sni)) },
                valueToText = { it },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.alpn,
                onValueChange = { viewModel.setAlpn(it) },
                title = { Text(stringResource(Res.string.alpn)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.toc,
                        color = IconMaskColors.IconLightBlue,
                    )
                },
                summary = { Text(contentOrUnset(state.alpn)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.certificate,
                onValueChange = { viewModel.setCertificate(it) },
                title = { Text(stringResource(Res.string.certificates)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.vpn_key,
                        color = IconMaskColors.IconLightOrange,
                        shape = IconMaskShapes.credential(),
                    )
                },
                summary = { Text(contentOrUnset(state.certificate)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.certPublicKeySha256,
                onValueChange = { viewModel.setCertPublicKeySha256(it) },
                title = { Text(stringResource(Res.string.cert_public_key_sha256)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.wb_sunny,
                        color = IconMaskColors.IconLightYellow,
                    )
                },
                summary = { Text(contentOrUnset(state.certPublicKeySha256)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            SwitchPreference(
                value = state.allowInsecure,
                onValueChange = { viewModel.setAllowInsecure(it) },
                title = { Text(stringResource(Res.string.allow_insecure)) },
                summary = { Text(stringResource(Res.string.allow_insecure_sum)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.enhanced_encryption,
                        color = IconMaskColors.IconCoral,
                        shape = IconMaskShapes.risk(),
                    )
                },
            )
            if (!isReality) {
                PreferenceDivider()
                SwitchPreference(
                    value = state.disableSNI,
                    onValueChange = { viewModel.setDisableSNI(it) },
                    title = { Text(stringResource(Res.string.tuic_disable_sni)) },
                    icon = {
                        MaskedIcon(
                            Res.drawable.block,
                            color = IconMaskColors.IconWarmGray,
                        )
                    },
                )
            }
            PreferenceDivider()
            SwitchPreference(
                value = state.tlsFragment,
                onValueChange = { viewModel.setTlsFragment(it) },
                title = { Text(stringResource(Res.string.tls_fragment)) },
                enabled = !state.tlsRecordFragment,
                icon = {
                    MaskedIcon(
                        Res.drawable.texture,
                        color = IconMaskColors.IconLightBlue,
                    )
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.tlsFragmentFallbackDelay,
                onValueChange = { viewModel.setTlsFragmentFallbackDelay(it) },
                title = { Text(stringResource(Res.string.tls_fragment_fallback_delay)) },
                textToValue = { it },
                enabled = state.tlsFragment,
                icon = {
                    MaskedIcon(
                        Res.drawable.timer,
                        color = IconMaskColors.IconLightOrange,
                    )
                },
                summary = { Text(contentOrUnset(state.tlsFragmentFallbackDelay)) },
                valueToText = { it },
            )
            PreferenceDivider()
            SwitchPreference(
                value = state.tlsRecordFragment,
                onValueChange = { viewModel.setTlsRecordFragment(it) },
                title = { Text(stringResource(Res.string.tls_record_fragment)) },
                enabled = !state.tlsFragment,
                icon = {
                    MaskedIcon(
                        Res.drawable.wb_sunny,
                        color = IconMaskColors.IconLavender,
                    )
                },
            )
        }

        item("category_tls_camouflage") {
            PreferenceCategory(text = { Text(stringResource(Res.string.tls_camouflage_settings)) })
        }
        preferenceGroup {
            ListPreference(
                value = state.utlsFingerprint,
                values = fingerprints,
                onValueChange = { viewModel.setUtlsFingerprint(it) },
                title = { Text(stringResource(Res.string.utls_fingerprint)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.security,
                        color = IconMaskColors.IconCyan,
                    )
                },
                summary = { Text(contentOrUnset(state.utlsFingerprint)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.realityPublicKey,
                onValueChange = { viewModel.setRealityPublicKey(it) },
                title = { Text(stringResource(Res.string.reality_public_key)) },
                textToValue = { it },
                enabled = state.utlsFingerprint.isNotBlank(),
                icon = {
                    MaskedIcon(
                        Res.drawable.vpn_key,
                        color = IconMaskColors.IconLightBlue,
                    )
                },
                summary = { Text(contentOrUnset(state.realityPublicKey)) },
                valueToText = { it },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.realityShortID,
                onValueChange = { viewModel.setRealityShortID(it) },
                title = { Text(stringResource(Res.string.reality_short_id)) },
                textToValue = { it },
                enabled = isReality,
                icon = {
                    MaskedIcon(
                        Res.drawable.texture,
                        color = IconMaskColors.IconLightOrange,
                    )
                },
                summary = { Text(contentOrUnset(state.realityShortID)) },
                valueToText = { it },
            )
            if (!PlatformInfo.isAndroid) {
                PreferenceDivider()
                TextFieldPreference(
                    value = state.tlsSpoof,
                    onValueChange = { viewModel.setTlsSpoof(it) },
                    title = { Text(stringResource(Res.string.tls_spoof)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.domino_mask,
                            color = IconMaskColors.IconWarmGray,
                        )
                    },
                    summary = { Text(contentOrUnset(state.tlsSpoof)) },
                    valueToText = { it },
                )
                PreferenceDivider()
                ListPreference(
                    value = state.tlsSpoofMethod,
                    values = tlsSpoofMethod,
                    onValueChange = { viewModel.setTlsSpoofMethod(it) },
                    title = { Text(stringResource(Res.string.tls_spoof_method)) },
                    enabled = state.tlsSpoof.isNotBlank(),
                    icon = {
                        MaskedIcon(
                            Res.drawable.computer_cancel,
                            color = IconMaskColors.IconLightYellow,
                        )
                    },
                    summary = { Text(contentOrUnset(state.tlsSpoofMethod)) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(it) },
                )
            }
        }

        item("category_ech") { PreferenceCategory(text = { Text(stringResource(Res.string.ech)) }) }
        preferenceGroup {
            SwitchPreference(
                value = state.ech,
                onValueChange = { viewModel.setEch(it) },
                title = { Text(stringResource(Res.string.enable)) },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.security,
                        color = IconMaskColors.IconCoral,
                        shape = IconMaskShapes.risk(),
                    )
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.echConfig,
                onValueChange = { viewModel.setEchConfig(it) },
                title = { Text(stringResource(Res.string.ech_config)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.nfc,
                        color = IconMaskColors.IconLightBlue,
                        shape = IconMaskShapes.credential(),
                    )
                },
                enabled = state.ech,
                summary = { Text(contentOrUnset(state.echConfig)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.echQueryServerName,
                onValueChange = { viewModel.setEchQueryServerName(it) },
                title = { Text(stringResource(Res.string.ech_query_server_name)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.search,
                        color = IconMaskColors.IconLightOrange,
                    )
                },
                enabled = state.ech,
                summary = { Text(contentOrUnset(state.echQueryServerName)) },
                valueToText = { it },
            )
        }

        item("category_mutual_tls") {
            PreferenceCategory(text = { Text(stringResource(Res.string.mutual_tls)) })
        }
        preferenceGroup {
            TextFieldPreference(
                value = state.clientCert,
                onValueChange = { viewModel.setClientCert(it) },
                title = { Text(stringResource(Res.string.client_certificate)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.lock,
                        color = IconMaskColors.IconCyan,
                        shape = IconMaskShapes.credential(),
                    )
                },
                summary = { Text(contentOrUnset(state.clientCert)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = state.clientKey,
                onValueChange = { viewModel.setClientKey(it) },
                title = { Text(stringResource(Res.string.client_key)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.vpn_key,
                        color = IconMaskColors.IconLavender,
                        shape = IconMaskShapes.credential(),
                    )
                },
                summary = { Text(contentOrUnset(state.clientKey)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
    }
}

internal fun LazyListScope.muxSettings(
    state: StandardV2RayUiState,
    viewModel: StandardV2RaySettingsViewModel<*>,
) {
    item("category_mux") {
        PreferenceCategory(text = { Text(stringResource(Res.string.mux_preference)) })
    }
    preferenceGroup {
        SwitchPreference(
            value = state.enableMux,
            onValueChange = { viewModel.setEnableMux(it) },
            title = { Text(stringResource(Res.string.enable)) },
            icon = {
                MaskedIcon(
                    resource = Res.drawable.multiple_stop,
                    color = IconMaskColors.IconLightPink,
                )
            },
        )
        AnimatedVisibility(visible = state.enableMux) {
            Column {
                PreferenceDivider()
                SwitchPreference(
                    value = state.brutal,
                    onValueChange = { viewModel.setBrutal(it) },
                    title = { Text(stringResource(Res.string.enable_brutal)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.bolt,
                            color = IconMaskColors.IconCoral,
                            shape = IconMaskShapes.risk(),
                        )
                    },
                )
                PreferenceDivider()
                ListPreference(
                    value = state.muxType,
                    values = intListN(muxTypes.size),
                    onValueChange = { viewModel.setMuxType(it) },
                    title = { Text(stringResource(Res.string.mux_type)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.type_specimen,
                            color = IconMaskColors.IconLightGreen,
                        )
                    },
                    summary = { Text(muxTypes[state.muxType]) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(muxTypes[it]) },
                )
                PreferenceDivider()
                ListPreference(
                    value = state.muxStrategy,
                    values = intListN(muxStrategies.size),
                    onValueChange = { viewModel.setMuxStrategy(it) },
                    title = { Text(stringResource(Res.string.mux_strategy)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.view_in_ar,
                            color = IconMaskColors.IconWarmGray,
                        )
                    },
                    summary = { Text(stringResource(muxStrategies[state.muxStrategy])) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(stringResource(muxStrategies[it])) },
                    enabled = !state.brutal,
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.muxNumber,
                    onValueChange = { viewModel.setMuxNumber(it) },
                    title = { Text(stringResource(Res.string.mux_number)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.numbers,
                            color = IconMaskColors.IconLightYellow,
                            shape = IconMaskShapes.route(),
                        )
                    },
                    summary = { Text(state.muxNumber.toString()) },
                    valueToText = { it.toString() },
                    enabled = !state.brutal,
                )
                PreferenceDivider()
                SwitchPreference(
                    value = state.muxPadding,
                    onValueChange = { viewModel.setMuxPadding(it) },
                    title = { Text(stringResource(Res.string.padding)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.border_inner,
                            color = IconMaskColors.IconLightBlue,
                        )
                    },
                )
            }
        }
    }
}

internal fun LazyListScope.transportSettings(
    state: StandardV2RayUiState,
    viewModel: StandardV2RaySettingsViewModel<*>,
) {
    item("category_transport") {
        PreferenceCategory(text = { Text(stringResource(Res.string.v2ray_transport)) })
    }
    preferenceGroup {
        ListPreference(
            value = state.v2rayTransport,
            values =
                listOf(
                    "",
                    SingBoxOptions.TRANSPORT_WS,
                    SingBoxOptions.TRANSPORT_HTTP,
                    SingBoxOptions.TRANSPORT_GRPC,
                    SingBoxOptions.TRANSPORT_HTTPUPGRADE,
                    SingBoxOptions.TRANSPORT_QUIC,
                ),
            onValueChange = { viewModel.setTransport(it) },
            title = { Text(stringResource(Res.string.v2ray_transport)) },
            icon = {
                MaskedIcon(
                    Res.drawable.route,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(state.v2rayTransport)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(contentOrUnset(it)) },
        )
        when (state.v2rayTransport) {
            "",
            "tcp",
                -> Unit

            SingBoxOptions.TRANSPORT_HTTP -> {
                PreferenceDivider()
                TextFieldPreference(
                    value = state.host,
                    onValueChange = { viewModel.setHost(it) },
                    title = { Text(stringResource(Res.string.http_host)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.language,
                            color = IconMaskColors.IconCyan,
                        )
                    },
                    summary = { Text(contentOrUnset(state.host)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.path,
                    onValueChange = { viewModel.setPath(it) },
                    title = { Text(stringResource(Res.string.http_path)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.assistant_direction,
                            color = IconMaskColors.IconLightOrange,
                            shape = IconMaskShapes.route(),
                        )
                    },
                    summary = { Text(contentOrUnset(state.path)) },
                    valueToText = { it },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.headers,
                    onValueChange = { viewModel.setHeaders(it) },
                    title = { Text(stringResource(Res.string.http_headers)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.code,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                    summary = { Text(contentOrUnset(state.headers)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }

            SingBoxOptions.TRANSPORT_WS -> {
                PreferenceDivider()
                TextFieldPreference(
                    value = state.host,
                    onValueChange = { viewModel.setHost(it) },
                    title = { Text(stringResource(Res.string.ws_host)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.language,
                            color = IconMaskColors.IconCyan,
                        )
                    },
                    summary = { Text(contentOrUnset(state.host)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.path,
                    onValueChange = { viewModel.setPath(it) },
                    title = { Text(stringResource(Res.string.ws_path)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.assistant_direction,
                            color = IconMaskColors.IconLightOrange,
                            shape = IconMaskShapes.route(),
                        )
                    },
                    summary = { Text(contentOrUnset(state.path)) },
                    valueToText = { it },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.headers,
                    onValueChange = { viewModel.setHeaders(it) },
                    title = { Text(stringResource(Res.string.http_headers)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.code,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                    summary = { Text(contentOrUnset(state.headers)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.wsMaxEarlyData,
                    onValueChange = { viewModel.setWsMaxEarlyData(it) },
                    title = { Text(stringResource(Res.string.ws_max_early_data)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = {
                        MaskedIcon(
                            Res.drawable.compare_arrows,
                            color = IconMaskColors.IconLightGreen,
                        )
                    },
                    summary = { Text(contentOrUnset(state.wsMaxEarlyData)) },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.wsEarlyDataHeaderName,
                    onValueChange = { viewModel.setWsEarlyDataHeaderName(it) },
                    title = { Text(stringResource(Res.string.early_data_header_name)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.stream,
                            color = IconMaskColors.IconWarmGray,
                        )
                    },
                    summary = { Text(contentOrUnset(state.wsEarlyDataHeaderName)) },
                    valueToText = { it },
                )
            }

            SingBoxOptions.TRANSPORT_GRPC -> {
                PreferenceDivider()
                TextFieldPreference(
                    value = state.path,
                    onValueChange = { viewModel.setPath(it) },
                    title = { Text(stringResource(Res.string.grpc_service_name)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.assistant_direction,
                            color = IconMaskColors.IconLightGreen,
                            shape = IconMaskShapes.route(),
                        )
                    },
                    summary = { Text(contentOrUnset(state.path)) },
                    valueToText = { it },
                )
            }

            SingBoxOptions.TRANSPORT_HTTPUPGRADE -> {
                PreferenceDivider()
                TextFieldPreference(
                    value = state.host,
                    onValueChange = { viewModel.setHost(it) },
                    title = { Text(stringResource(Res.string.http_upgrade_host)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.language,
                            color = IconMaskColors.IconCyan,
                            shape = IconMaskShapes.route(),
                        )
                    },
                    summary = { Text(contentOrUnset(state.host)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.path,
                    onValueChange = { viewModel.setPath(it) },
                    title = { Text(stringResource(Res.string.http_upgrade_path)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.assistant_direction,
                            color = IconMaskColors.IconLightOrange,
                        )
                    },
                    summary = { Text(contentOrUnset(state.path)) },
                    valueToText = { it },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = state.headers,
                    onValueChange = { viewModel.setHeaders(it) },
                    title = { Text(stringResource(Res.string.http_headers)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.code,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                    summary = { Text(contentOrUnset(state.headers)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }

            SingBoxOptions.TRANSPORT_QUIC -> Unit
        }
    }
}
