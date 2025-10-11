package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssistantDirection
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BorderInner
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MultipleStop
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TypeSpecimen
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.MultilineTextField
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import io.nekohasekai.sagernet.ktx.intListN
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
abstract class StandardV2RaySettingsActivity<T : StandardV2RayBean> : ProfileSettingsActivity<T>() {

    companion object {
        private const val KEY_SECURITY = "security"
    }

    override val viewModel by viewModels<StandardV2RaySettingsViewModel<T>>()

    internal fun LazyListScope.headSettings(state: StandardV2RayUiState) {
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
        item("category_basic") {
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
        item("port") {
            TextFieldPreference(
                value = state.port,
                onValueChange = { viewModel.setPort(it) },
                title = { Text(stringResource(R.string.server_port)) },
                textToValue = { it.toIntOrNull() ?: 443 },
                icon = { Icon(Icons.Filled.DirectionsBoat, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.port)) },
            )
        }
    }

    internal fun LazyListScope.tlsSettings(
        state: StandardV2RayUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        val isTls = state.security == "tls"
        val isReality = state.realityPublicKey.isNotBlank()

        item("category_security") {
            PreferenceCategory(text = { Text(stringResource(R.string.security_settings)) })
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
                title = { Text(stringResource(R.string.security)) },
                icon = { Icon(Icons.Filled.Layers, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.security)) },
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
                        title = { Text(stringResource(R.string.sni)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Copyright, null) },
                        enabled = !state.disableSNI,
                        summary = { Text(LocalContext.current.contentOrUnset(state.sni)) },
                        valueToText = { it },
                    )
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
                    TextFieldPreference(
                        value = state.certificate,
                        onValueChange = { viewModel.setCertificate(it) },
                        title = { Text(stringResource(R.string.certificates)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.VpnKey, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.certificate)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                    TextFieldPreference(
                        value = state.certPublicKeySha256,
                        onValueChange = { viewModel.setCertPublicKeySha256(it) },
                        title = { Text(stringResource(R.string.cert_public_key_sha256)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.WbSunny, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.certPublicKeySha256)) },
                        valueToText = { it },
                    )
                    SwitchPreference(
                        value = state.allowInsecure,
                        onValueChange = { viewModel.setAllowInsecure(it) },
                        title = { Text(stringResource(R.string.allow_insecure)) },
                        summary = { Text(stringResource(R.string.allow_insecure_sum)) },
                        icon = { Icon(Icons.Filled.EnhancedEncryption, null) },
                    )
                    if (!isReality) {
                        SwitchPreference(
                            value = state.disableSNI,
                            onValueChange = { viewModel.setDisableSNI(it) },
                            title = { Text(stringResource(R.string.tuic_disable_sni)) },
                            icon = { Icon(Icons.Filled.Block, null) },
                        )
                    }
                    SwitchPreference(
                        value = state.tlsFragment,
                        onValueChange = { viewModel.setTlsFragment(it) },
                        title = { Text(stringResource(R.string.tls_fragment)) },
                        enabled = !state.tlsRecordFragment,
                        icon = { Icon(Icons.Filled.Texture, null) },
                    )
                    TextFieldPreference(
                        value = state.tlsFragmentFallbackDelay,
                        onValueChange = { viewModel.setTlsFragmentFallbackDelay(it) },
                        title = { Text(stringResource(R.string.tls_fragment_fallback_delay)) },
                        textToValue = { it },
                        enabled = state.tlsFragment,
                        icon = { Icon(Icons.Filled.Timer, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.tlsFragmentFallbackDelay)) },
                        valueToText = { it },
                    )
                    SwitchPreference(
                        value = state.tlsRecordFragment,
                        onValueChange = { viewModel.setTlsRecordFragment(it) },
                        title = { Text(stringResource(R.string.tls_record_fragment)) },
                        enabled = !state.tlsFragment,
                        icon = { Icon(Icons.Filled.WbSunny, null) },
                    )

                    PreferenceCategory(text = { Text(stringResource(R.string.tls_camouflage_settings)) })
                    ListPreference(
                        value = state.utlsFingerprint,
                        values = fingerprints,
                        onValueChange = { viewModel.setUtlsFingerprint(it) },
                        title = { Text(stringResource(R.string.utls_fingerprint)) },
                        icon = { Icon(Icons.Filled.Security, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.utlsFingerprint)) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(it) },
                    )
                    TextFieldPreference(
                        value = state.realityPublicKey,
                        onValueChange = { viewModel.setRealityPublicKey(it) },
                        title = { Text(stringResource(R.string.reality_public_key)) },
                        textToValue = { it },
                        enabled = state.utlsFingerprint.isNotBlank(),
                        icon = { Icon(Icons.Filled.VpnKey, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.realityPublicKey)) },
                        valueToText = { it },
                    )
                    TextFieldPreference(
                        value = state.realityShortID,
                        onValueChange = { viewModel.setRealityShortID(it) },
                        title = { Text(stringResource(R.string.reality_public_key)) },
                        textToValue = { it },
                        enabled = isReality,
                        icon = { Icon(Icons.Filled.Texture, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.realityShortID)) },
                        valueToText = { it },
                    )

                    PreferenceCategory(text = { Text(stringResource(R.string.ech)) })
                    SwitchPreference(
                        value = state.ech,
                        onValueChange = { viewModel.setEch(it) },
                        title = { Text(stringResource(R.string.enable)) },
                        icon = { Icon(Icons.Filled.Security, null) },
                    )
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

                    PreferenceCategory(text = { Text(stringResource(R.string.mutual_tls)) })
                    TextFieldPreference(
                        value = state.clientCert,
                        onValueChange = { viewModel.setClientCert(it) },
                        title = { Text(stringResource(R.string.certificates)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Lock, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.clientCert)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                    TextFieldPreference(
                        value = state.clientKey,
                        onValueChange = { viewModel.setClientKey(it) },
                        title = { Text(stringResource(R.string.ssh_private_key)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.VpnKey, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.clientKey)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                }
            }
        }
    }

    internal fun LazyListScope.muxSettings(state: StandardV2RayUiState) {
        item("category_mux") {
            PreferenceCategory(text = { Text(stringResource(R.string.mux_preference)) })
        }
        item("enable_mux") {
            SwitchPreference(
                value = state.enableMux,
                onValueChange = { viewModel.setEnableMux(it) },
                title = { Text(stringResource(R.string.enable)) },
                icon = { Icon(Icons.Filled.MultipleStop, null) },
            )
        }
        item("mux_settings") {
            AnimatedVisibility(visible = state.enableMux) {
                Column {
                    SwitchPreference(
                        value = state.brutal,
                        onValueChange = { viewModel.setBrutal(it) },
                        title = { Text(stringResource(R.string.enable_brutal)) },
                        icon = { Icon(Icons.Filled.Bolt, null) },
                    )
                    ListPreference(
                        value = state.muxType,
                        values = intListN(muxTypes.size),
                        onValueChange = { viewModel.setMuxType(it) },
                        title = { Text(stringResource(R.string.mux_type)) },
                        icon = { Icon(Icons.Filled.TypeSpecimen, null) },
                        summary = { Text(muxTypes[state.muxType]) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(muxTypes[it]) },
                    )
                    ListPreference(
                        value = state.muxStrategy,
                        values = intListN(muxStrategies.size),
                        onValueChange = { viewModel.setMuxStrategy(it) },
                        title = { Text(stringResource(R.string.mux_strategy)) },
                        icon = { Icon(Icons.Filled.ViewInAr, null) },
                        summary = { Text(stringResource(muxStrategies[state.muxStrategy])) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(getString(muxStrategies[it])) },
                        enabled = !state.brutal,
                    )
                    TextFieldPreference(
                        value = state.muxNumber,
                        onValueChange = { viewModel.setMuxNumber(it) },
                        title = { Text(stringResource(R.string.mux_number)) },
                        textToValue = { it.toIntOrNull() ?: 0 },
                        icon = { Icon(Icons.Filled.Numbers, null) },
                        summary = { Text(state.muxNumber.toString()) },
                        valueToText = { it.toString() },
                        enabled = !state.brutal,
                    )
                    SwitchPreference(
                        value = state.muxPadding,
                        onValueChange = { viewModel.setMuxPadding(it) },
                        title = { Text(stringResource(R.string.padding)) },
                        icon = { Icon(Icons.Filled.BorderInner, null) },
                    )
                }
            }
        }

    }

    internal fun LazyListScope.transportSettings(state: StandardV2RayUiState) {
        item("category_transport") {
            PreferenceCategory(text = { Text(stringResource(R.string.v2ray_transport)) })
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
                title = { Text(stringResource(R.string.v2ray_transport)) },
                icon = { Icon(Icons.Filled.Route, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.v2rayTransport)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(contentOrUnset(it)) },
            )
        }

        when (state.v2rayTransport) {
            "", "tcp" -> {}

            SingBoxOptions.TRANSPORT_HTTP -> {
                item("host") {
                    TextFieldPreference(
                        value = state.host,
                        onValueChange = { viewModel.setHost(it) },
                        title = { Text(stringResource(R.string.http_host)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Language, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.host)) },
                        valueToText = { it },
                    )
                }
                item("path") {
                    TextFieldPreference(
                        value = state.path,
                        onValueChange = { viewModel.setPath(it) },
                        title = { Text(stringResource(R.string.http_path)) },
                        textToValue = { it },
                        icon = { Icon(Icons.AutoMirrored.Filled.AssistantDirection, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.path)) },
                        valueToText = { it },
                    )
                }
                item("headers") {
                    TextFieldPreference(
                        value = state.headers,
                        onValueChange = { viewModel.setHeaders(it) },
                        title = { Text(stringResource(R.string.http_headers)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Code, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.headers)) },
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
                        title = { Text(stringResource(R.string.ws_host)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Language, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.host)) },
                        valueToText = { it },
                    )
                }
                item("path") {
                    TextFieldPreference(
                        value = state.path,
                        onValueChange = { viewModel.setPath(it) },
                        title = { Text(stringResource(R.string.ws_path)) },
                        textToValue = { it },
                        icon = { Icon(Icons.AutoMirrored.Filled.AssistantDirection, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.path)) },
                        valueToText = { it },
                    )
                }
                item("headers") {
                    TextFieldPreference(
                        value = state.headers,
                        onValueChange = { viewModel.setHeaders(it) },
                        title = { Text(stringResource(R.string.http_headers)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Code, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.headers)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                }

                item("category_ws_options") {
                    PreferenceCategory(text = { Text(stringResource(R.string.cag_ws)) })
                }
                item("ws_max_early_data") {
                    TextFieldPreference(
                        value = state.wsMaxEarlyData,
                        onValueChange = { viewModel.setWsMaxEarlyData(it) },
                        title = { Text(stringResource(R.string.ws_max_early_data)) },
                        textToValue = { it.toIntOrNull() ?: 0 },
                        icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.wsMaxEarlyData)) },
                    )
                }
                item("ws_early_data_name") {
                    TextFieldPreference(
                        value = state.wsEarlyDataHeaderName,
                        onValueChange = { viewModel.setWsEarlyDataHeaderName(it) },
                        title = { Text(stringResource(R.string.early_data_header_name)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Stream, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.wsEarlyDataHeaderName)) },
                        valueToText = { it },
                    )
                }
            }

            SingBoxOptions.TRANSPORT_GRPC -> {
                item("path") {
                    TextFieldPreference(
                        value = state.path,
                        onValueChange = { viewModel.setPath(it) },
                        title = { Text(stringResource(R.string.grpc_service_name)) },
                        textToValue = { it },
                        icon = { Icon(Icons.AutoMirrored.Filled.AssistantDirection, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.path)) },
                        valueToText = { it },
                    )
                }
            }

            SingBoxOptions.TRANSPORT_HTTPUPGRADE -> {
                item("host") {
                    TextFieldPreference(
                        value = state.host,
                        onValueChange = { viewModel.setHost(it) },
                        title = { Text(stringResource(R.string.http_upgrade_host)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Language, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.host)) },
                        valueToText = { it },
                    )
                }
                item("path") {
                    TextFieldPreference(
                        value = state.path,
                        onValueChange = { viewModel.setPath(it) },
                        title = { Text(stringResource(R.string.http_upgrade_path)) },
                        textToValue = { it },
                        icon = { Icon(Icons.AutoMirrored.Filled.AssistantDirection, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.path)) },
                        valueToText = { it },
                    )
                }
                item("headers") {
                    TextFieldPreference(
                        value = state.headers,
                        onValueChange = { viewModel.setHeaders(it) },
                        title = { Text(stringResource(R.string.http_headers)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Code, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(state.headers)) },
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
}