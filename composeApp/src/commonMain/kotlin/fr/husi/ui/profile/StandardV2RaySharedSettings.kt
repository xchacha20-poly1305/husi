package fr.husi.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PortTextField
import fr.husi.compose.PreferenceCategory
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.contentOrNotSet
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.repository.repo
import fr.husi.resources.Res
import fr.husi.resources.allow_insecure
import fr.husi.resources.allow_insecure_sum
import fr.husi.resources.alpn
import fr.husi.resources.assistant_direction
import fr.husi.resources.block
import fr.husi.resources.bolt
import fr.husi.resources.border_inner
import fr.husi.resources.cag_ws
import fr.husi.resources.cert_public_key_sha256
import fr.husi.resources.certificates
import fr.husi.resources.code
import fr.husi.resources.compare_arrows
import fr.husi.resources.copyright
import fr.husi.resources.directions_boat
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
import fr.husi.resources.ssh_private_key
import fr.husi.resources.stream
import fr.husi.resources.texture
import fr.husi.resources.timer
import fr.husi.resources.tls_camouflage_settings
import fr.husi.resources.tls_fragment
import fr.husi.resources.tls_fragment_fallback_delay
import fr.husi.resources.tls_record_fragment
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
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

private const val KEY_SECURITY = "security"

internal fun LazyListScope.headSettings(
    state: StandardV2RayUiState,
    viewModel: StandardV2RaySettingsViewModel<*>,
) {
    item("name") {
        TextFieldPreference(
            value = state.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.emoji_symbols), null) },
            summary = { Text(contentOrUnset(state.name)) },
            valueToText = { it },
        )
    }
    item("category_basic") {
        PreferenceCategory(text = { Text(stringResource(Res.string.proxy_cat)) })
    }
    item("address") {
        TextFieldPreference(
            value = state.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.router), null) },
            summary = { Text(contentOrUnset(state.address)) },
            valueToText = { it },
        )
    }
    item("port") {
        TextFieldPreference(
            value = state.port,
            onValueChange = { viewModel.setPort(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull() ?: 443 },
            icon = { Icon(vectorResource(Res.drawable.directions_boat), null) },
            summary = { Text(contentOrUnset(state.port)) },
            textField = { value, onValueChange, onOk ->
                PortTextField(value, onValueChange, onOk)
            },
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
    item(KEY_SECURITY) {
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
            icon = { Icon(vectorResource(Res.drawable.layers), null) },
            summary = { Text(contentOrUnset(state.security)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }

    item("security_fields") {
        AnimatedVisibility(visible = isTls) {
            Column {
                TextFieldPreference(
                    value = state.sni,
                    onValueChange = { viewModel.setSni(it) },
                    title = { Text(stringResource(Res.string.sni)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.copyright), null) },
                    summary = { Text(contentOrUnset(state.sni)) },
                    valueToText = { it },
                )
                TextFieldPreference(
                    value = state.alpn,
                    onValueChange = { viewModel.setAlpn(it) },
                    title = { Text(stringResource(Res.string.alpn)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.toc), null) },
                    summary = { Text(contentOrUnset(state.alpn)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                TextFieldPreference(
                    value = state.certificate,
                    onValueChange = { viewModel.setCertificate(it) },
                    title = { Text(stringResource(Res.string.certificates)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.vpn_key), null) },
                    summary = { Text(contentOrUnset(state.certificate)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                TextFieldPreference(
                    value = state.certPublicKeySha256,
                    onValueChange = { viewModel.setCertPublicKeySha256(it) },
                    title = { Text(stringResource(Res.string.cert_public_key_sha256)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.wb_sunny), null) },
                    summary = { Text(contentOrUnset(state.certPublicKeySha256)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                SwitchPreference(
                    value = state.allowInsecure,
                    onValueChange = { viewModel.setAllowInsecure(it) },
                    title = { Text(stringResource(Res.string.allow_insecure)) },
                    summary = { Text(stringResource(Res.string.allow_insecure_sum)) },
                    icon = {
                        Icon(
                            vectorResource(Res.drawable.enhanced_encryption),
                            null,
                        )
                    },
                )
                if (!isReality) {
                    SwitchPreference(
                        value = state.disableSNI,
                        onValueChange = { viewModel.setDisableSNI(it) },
                        title = { Text(stringResource(Res.string.tuic_disable_sni)) },
                        icon = { Icon(vectorResource(Res.drawable.block), null) },
                    )
                }
                SwitchPreference(
                    value = state.tlsFragment,
                    onValueChange = { viewModel.setTlsFragment(it) },
                    title = { Text(stringResource(Res.string.tls_fragment)) },
                    enabled = !state.tlsRecordFragment,
                    icon = { Icon(vectorResource(Res.drawable.texture), null) },
                )
                TextFieldPreference(
                    value = state.tlsFragmentFallbackDelay,
                    onValueChange = { viewModel.setTlsFragmentFallbackDelay(it) },
                    title = { Text(stringResource(Res.string.tls_fragment_fallback_delay)) },
                    textToValue = { it },
                    enabled = state.tlsFragment,
                    icon = { Icon(vectorResource(Res.drawable.timer), null) },
                    summary = { Text(contentOrUnset(state.tlsFragmentFallbackDelay)) },
                    valueToText = { it },
                )
                SwitchPreference(
                    value = state.tlsRecordFragment,
                    onValueChange = { viewModel.setTlsRecordFragment(it) },
                    title = { Text(stringResource(Res.string.tls_record_fragment)) },
                    enabled = !state.tlsFragment,
                    icon = { Icon(vectorResource(Res.drawable.wb_sunny), null) },
                )

                PreferenceCategory(text = { Text(stringResource(Res.string.tls_camouflage_settings)) })
                ListPreference(
                    value = state.utlsFingerprint,
                    values = fingerprints,
                    onValueChange = { viewModel.setUtlsFingerprint(it) },
                    title = { Text(stringResource(Res.string.utls_fingerprint)) },
                    icon = { Icon(vectorResource(Res.drawable.security), null) },
                    summary = { Text(contentOrUnset(state.utlsFingerprint)) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(it) },
                )
                TextFieldPreference(
                    value = state.realityPublicKey,
                    onValueChange = { viewModel.setRealityPublicKey(it) },
                    title = { Text(stringResource(Res.string.reality_public_key)) },
                    textToValue = { it },
                    enabled = state.utlsFingerprint.isNotBlank(),
                    icon = { Icon(vectorResource(Res.drawable.vpn_key), null) },
                    summary = { Text(contentOrUnset(state.realityPublicKey)) },
                    valueToText = { it },
                )
                TextFieldPreference(
                    value = state.realityShortID,
                    onValueChange = { viewModel.setRealityShortID(it) },
                    title = { Text(stringResource(Res.string.reality_short_id)) },
                    textToValue = { it },
                    enabled = isReality,
                    icon = { Icon(vectorResource(Res.drawable.texture), null) },
                    summary = { Text(contentOrUnset(state.realityShortID)) },
                    valueToText = { it },
                )

                PreferenceCategory(text = { Text(stringResource(Res.string.ech)) })
                SwitchPreference(
                    value = state.ech,
                    onValueChange = { viewModel.setEch(it) },
                    title = { Text(stringResource(Res.string.enable)) },
                    icon = { Icon(vectorResource(Res.drawable.security), null) },
                )
                TextFieldPreference(
                    value = state.echConfig,
                    onValueChange = { viewModel.setEchConfig(it) },
                    title = { Text(stringResource(Res.string.ech_config)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.nfc), null) },
                    enabled = state.ech,
                    summary = { Text(contentOrUnset(state.echConfig)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                TextFieldPreference(
                    value = state.echQueryServerName,
                    onValueChange = { viewModel.setEchQueryServerName(it) },
                    title = { Text(stringResource(Res.string.ech_query_server_name)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.search), null) },
                    enabled = state.ech,
                    summary = { Text(contentOrUnset(state.echQueryServerName)) },
                    valueToText = { it },
                )

                PreferenceCategory(text = { Text(stringResource(Res.string.mutual_tls)) })
                TextFieldPreference(
                    value = state.clientCert,
                    onValueChange = { viewModel.setClientCert(it) },
                    title = { Text(stringResource(Res.string.certificates)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.lock), null) },
                    summary = { Text(contentOrUnset(state.clientCert)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                TextFieldPreference(
                    value = state.clientKey,
                    onValueChange = { viewModel.setClientKey(it) },
                    title = { Text(stringResource(Res.string.ssh_private_key)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.vpn_key), null) },
                    summary = { Text(contentOrUnset(state.clientKey)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }
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
    item("enable_mux") {
        SwitchPreference(
            value = state.enableMux,
            onValueChange = { viewModel.setEnableMux(it) },
            title = { Text(stringResource(Res.string.enable)) },
            icon = { Icon(vectorResource(Res.drawable.multiple_stop), null) },
        )
    }
    item("mux_settings") {
        AnimatedVisibility(visible = state.enableMux) {
            Column {
                SwitchPreference(
                    value = state.brutal,
                    onValueChange = { viewModel.setBrutal(it) },
                    title = { Text(stringResource(Res.string.enable_brutal)) },
                    icon = { Icon(vectorResource(Res.drawable.bolt), null) },
                )
                ListPreference(
                    value = state.muxType,
                    values = intListN(muxTypes.size),
                    onValueChange = { viewModel.setMuxType(it) },
                    title = { Text(stringResource(Res.string.mux_type)) },
                    icon = { Icon(vectorResource(Res.drawable.type_specimen), null) },
                    summary = { Text(muxTypes[state.muxType]) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(muxTypes[it]) },
                )
                ListPreference(
                    value = state.muxStrategy,
                    values = intListN(muxStrategies.size),
                    onValueChange = { viewModel.setMuxStrategy(it) },
                    title = { Text(stringResource(Res.string.mux_strategy)) },
                    icon = { Icon(vectorResource(Res.drawable.view_in_ar), null) },
                    summary = { Text(stringResource(muxStrategies[state.muxStrategy])) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = {
                        val text = runBlocking { repo.getString(muxStrategies[it]) }
                        AnnotatedString(text)
                    },
                    enabled = !state.brutal,
                )
                TextFieldPreference(
                    value = state.muxNumber,
                    onValueChange = { viewModel.setMuxNumber(it) },
                    title = { Text(stringResource(Res.string.mux_number)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = { Icon(vectorResource(Res.drawable.numbers), null) },
                    summary = { Text(state.muxNumber.toString()) },
                    valueToText = { it.toString() },
                    enabled = !state.brutal,
                )
                SwitchPreference(
                    value = state.muxPadding,
                    onValueChange = { viewModel.setMuxPadding(it) },
                    title = { Text(stringResource(Res.string.padding)) },
                    icon = { Icon(vectorResource(Res.drawable.border_inner), null) },
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
    item("v2ray_transport") {
        ListPreference(
            value = state.v2rayTransport,
            values = listOf(
                "",
                SingBoxOptions.TRANSPORT_WS,
                SingBoxOptions.TRANSPORT_HTTP,
                SingBoxOptions.TRANSPORT_GRPC,
                SingBoxOptions.TRANSPORT_HTTPUPGRADE,
                SingBoxOptions.TRANSPORT_QUIC,
            ),
            onValueChange = { viewModel.setTransport(it) },
            title = { Text(stringResource(Res.string.v2ray_transport)) },
            icon = { Icon(vectorResource(Res.drawable.route), null) },
            summary = { Text(contentOrUnset(state.v2rayTransport)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                val text = runBlocking { contentOrNotSet(it) }
                AnnotatedString(text)
            },
        )
    }

    when (state.v2rayTransport) {
        "", "tcp" -> {}

        SingBoxOptions.TRANSPORT_HTTP -> {
            item("host") {
                TextFieldPreference(
                    value = state.host,
                    onValueChange = { viewModel.setHost(it) },
                    title = { Text(stringResource(Res.string.http_host)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.language), null) },
                    summary = { Text(contentOrUnset(state.host)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }
            item("path") {
                TextFieldPreference(
                    value = state.path,
                    onValueChange = { viewModel.setPath(it) },
                    title = { Text(stringResource(Res.string.http_path)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.assistant_direction), null) },
                    summary = { Text(contentOrUnset(state.path)) },
                    valueToText = { it },
                )
            }
            item("headers") {
                TextFieldPreference(
                    value = state.headers,
                    onValueChange = { viewModel.setHeaders(it) },
                    title = { Text(stringResource(Res.string.http_headers)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.code), null) },
                    summary = { Text(contentOrUnset(state.headers)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }
        }

        SingBoxOptions.TRANSPORT_WS -> {
            item("host") {
                TextFieldPreference(
                    value = state.host,
                    onValueChange = { viewModel.setHost(it) },
                    title = { Text(stringResource(Res.string.ws_host)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.language), null) },
                    summary = { Text(contentOrUnset(state.host)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }
            item("path") {
                TextFieldPreference(
                    value = state.path,
                    onValueChange = { viewModel.setPath(it) },
                    title = { Text(stringResource(Res.string.ws_path)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.assistant_direction), null) },
                    summary = { Text(contentOrUnset(state.path)) },
                    valueToText = { it },
                )
            }
            item("headers") {
                TextFieldPreference(
                    value = state.headers,
                    onValueChange = { viewModel.setHeaders(it) },
                    title = { Text(stringResource(Res.string.http_headers)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.code), null) },
                    summary = { Text(contentOrUnset(state.headers)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }

            item("category_ws_options") {
                PreferenceCategory(text = { Text(stringResource(Res.string.cag_ws)) })
            }
            item("ws_max_early_data") {
                TextFieldPreference(
                    value = state.wsMaxEarlyData,
                    onValueChange = { viewModel.setWsMaxEarlyData(it) },
                    title = { Text(stringResource(Res.string.ws_max_early_data)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = { Icon(vectorResource(Res.drawable.compare_arrows), null) },
                    summary = { Text(contentOrUnset(state.wsMaxEarlyData)) },
                )
            }
            item("ws_early_data_name") {
                TextFieldPreference(
                    value = state.wsEarlyDataHeaderName,
                    onValueChange = { viewModel.setWsEarlyDataHeaderName(it) },
                    title = { Text(stringResource(Res.string.early_data_header_name)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.stream), null) },
                    summary = { Text(contentOrUnset(state.wsEarlyDataHeaderName)) },
                    valueToText = { it },
                )
            }
        }

        SingBoxOptions.TRANSPORT_GRPC -> {
            item("path") {
                TextFieldPreference(
                    value = state.path,
                    onValueChange = { viewModel.setPath(it) },
                    title = { Text(stringResource(Res.string.grpc_service_name)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.assistant_direction), null) },
                    summary = { Text(contentOrUnset(state.path)) },
                    valueToText = { it },
                )
            }
        }

        SingBoxOptions.TRANSPORT_HTTPUPGRADE -> {
            item("host") {
                TextFieldPreference(
                    value = state.host,
                    onValueChange = { viewModel.setHost(it) },
                    title = { Text(stringResource(Res.string.http_upgrade_host)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.language), null) },
                    summary = { Text(contentOrUnset(state.host)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }
            item("path") {
                TextFieldPreference(
                    value = state.path,
                    onValueChange = { viewModel.setPath(it) },
                    title = { Text(stringResource(Res.string.http_upgrade_path)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.assistant_direction), null) },
                    summary = { Text(contentOrUnset(state.path)) },
                    valueToText = { it },
                )
            }
            item("headers") {
                TextFieldPreference(
                    value = state.headers,
                    onValueChange = { viewModel.setHeaders(it) },
                    title = { Text(stringResource(Res.string.http_headers)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.code), null) },
                    summary = { Text(contentOrUnset(state.headers)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
            }
        }

        SingBoxOptions.TRANSPORT_QUIC -> {}
    }
}
