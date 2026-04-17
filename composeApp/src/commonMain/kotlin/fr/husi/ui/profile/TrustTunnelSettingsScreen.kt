package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.DurationTextField
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.UIntegerTextField
import fr.husi.ktx.contentOrUnset
import fr.husi.platform.PlatformInfo
import fr.husi.resources.Res
import fr.husi.resources.allow_insecure
import fr.husi.resources.alpn
import fr.husi.resources.cert_public_key_sha256
import fr.husi.resources.certificates
import fr.husi.resources.computer_cancel
import fr.husi.resources.copyright
import fr.husi.resources.directions_boat
import fr.husi.resources.domino_mask
import fr.husi.resources.ecg
import fr.husi.resources.ech
import fr.husi.resources.ech_config
import fr.husi.resources.ech_query_server_name
import fr.husi.resources.emoji_symbols
import fr.husi.resources.fast_forward
import fr.husi.resources.fingerprint
import fr.husi.resources.health_check
import fr.husi.resources.lock
import fr.husi.resources.lock_open
import fr.husi.resources.mutual_tls
import fr.husi.resources.nfc
import fr.husi.resources.person
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.proxy_cat
import fr.husi.resources.quic
import fr.husi.resources.router
import fr.husi.resources.search
import fr.husi.resources.security
import fr.husi.resources.security_settings
import fr.husi.resources.server_address
import fr.husi.resources.server_port
import fr.husi.resources.sni
import fr.husi.resources.ssh_private_key
import fr.husi.resources.texture
import fr.husi.resources.timelapse
import fr.husi.resources.tls_fragment
import fr.husi.resources.tls_fragment_fallback_delay
import fr.husi.resources.tls_record_fragment
import fr.husi.resources.tls_spoof
import fr.husi.resources.tls_spoof_method
import fr.husi.resources.toc
import fr.husi.resources.traffic
import fr.husi.resources.tuic_congestion_controller
import fr.husi.resources.username
import fr.husi.resources.utls_fingerprint
import fr.husi.resources.vpn_key
import fr.husi.resources.wb_sunny
import fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustTunnelSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: TrustTunnelSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        TrustTunnelSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        trustTunnelSettings(uiState as TrustTunnelUiState, viewModel)
    }
}

private fun LazyListScope.trustTunnelSettings(
    uiState: TrustTunnelUiState,
    viewModel: TrustTunnelSettingsViewModel,
) {
    val congestionControls = listOf(
        "",
        "bbr",
        "cubic",
        "reno",
        "bbr_standard",
        "bbr2",
        "bbr_variant",
    )

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
    item("port") {
        TextFieldPreference(
            value = uiState.port,
            onValueChange = { viewModel.setPort(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull() ?: 443 },
            icon = { Icon(vectorResource(Res.drawable.directions_boat), null) },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }
    item("username") {
        TextFieldPreference(
            value = uiState.username,
            onValueChange = { viewModel.setUsername(it) },
            title = { Text(stringResource(Res.string.username)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.person), null) },
            summary = { Text(contentOrUnset(uiState.username)) },
            valueToText = { it },
        )
    }
    item("password") {
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
        )
    }
    item("health_check") {
        SwitchPreference(
            value = uiState.healthCheck,
            onValueChange = { viewModel.setHealthCheck(it) },
            title = { Text(stringResource(Res.string.health_check)) },
            icon = { Icon(vectorResource(Res.drawable.ecg), null) },
        )
    }
    item("quic") {
        SwitchPreference(
            value = uiState.quic,
            onValueChange = { viewModel.setQuic(it) },
            title = { Text(stringResource(Res.string.quic)) },
            icon = { Icon(vectorResource(Res.drawable.fast_forward), null) },
        )
    }
    item("quic_congestion_control") {
        ListPreference(
            value = uiState.quicCongestionControl,
            values = congestionControls,
            onValueChange = { viewModel.setQuicCongestionControl(it) },
            title = { Text(stringResource(Res.string.tuic_congestion_controller)) },
            icon = { Icon(vectorResource(Res.drawable.traffic), null) },
            enabled = uiState.quic,
            summary = { Text(contentOrUnset(uiState.quicCongestionControl)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }

    item("category_tls") {
        PreferenceCategory(text = { Text(stringResource(Res.string.security_settings)) })
    }
    item("server_name") {
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
    item("allow_insecure") {
        SwitchPreference(
            value = uiState.allowInsecure,
            onValueChange = { viewModel.setAllowInsecure(it) },
            title = { Text(stringResource(Res.string.allow_insecure)) },
            icon = { Icon(vectorResource(Res.drawable.lock_open), null) },
        )
    }
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
    item("utls_fingerprint") {
        ListPreference(
            value = uiState.utlsFingerprint,
            values = fingerprints,
            onValueChange = { viewModel.setUtlsFingerprint(it) },
            title = { Text(stringResource(Res.string.utls_fingerprint)) },
            icon = { Icon(vectorResource(Res.drawable.fingerprint), null) },
            enabled = !uiState.quic,
            summary = { Text(contentOrUnset(uiState.utlsFingerprint)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }
    if (!PlatformInfo.isAndroid) {
        item("tls_spoof") {
            TextFieldPreference(
                value = uiState.tlsSpoof,
                onValueChange = { viewModel.setTlsSpoof(it) },
                title = { Text(stringResource(Res.string.tls_spoof)) },
                textToValue = { it },
                enabled = !uiState.quic,
                icon = { Icon(vectorResource(Res.drawable.domino_mask), null) },
                summary = { Text(contentOrUnset(uiState.tlsSpoof)) },
                valueToText = { it },
            )
        }
        item("tls_spoof_method") {
            ListPreference(
                value = uiState.tlsSpoofMethod,
                values = listOf("wrong-sequence", "wrong-checksum"),
                onValueChange = { viewModel.setTlsSpoofMethod(it) },
                title = { Text(stringResource(Res.string.tls_spoof_method)) },
                enabled = !uiState.quic && uiState.tlsSpoof.isNotBlank(),
                icon = { Icon(vectorResource(Res.drawable.computer_cancel), null) },
                summary = { Text(contentOrUnset(uiState.tlsSpoofMethod)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
        }
    }
    item("tls_fragment") {
        SwitchPreference(
            value = uiState.tlsFragment,
            onValueChange = { viewModel.setTlsFragment(it) },
            title = { Text(stringResource(Res.string.tls_fragment)) },
            enabled = !uiState.tlsRecordFragment && !uiState.quic,
            icon = { Icon(vectorResource(Res.drawable.texture), null) },
        )
    }
    item("tls_fragment_fallback_delay") {
        TextFieldPreference(
            value = uiState.tlsFragmentFallbackDelay,
            onValueChange = { viewModel.setTlsFragmentFallbackDelay(it) },
            title = { Text(stringResource(Res.string.tls_fragment_fallback_delay)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.timelapse), null) },
            enabled = uiState.tlsFragment && !uiState.quic,
            summary = { Text(contentOrUnset(uiState.tlsFragmentFallbackDelay)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                DurationTextField(value, onValueChange, onOk)
            },
        )
    }
    item("tls_record_fragment") {
        SwitchPreference(
            value = uiState.tlsRecordFragment,
            onValueChange = { viewModel.setTlsRecordFragment(it) },
            title = { Text(stringResource(Res.string.tls_record_fragment)) },
            enabled = !uiState.tlsFragment && !uiState.quic,
            icon = { Icon(vectorResource(Res.drawable.wb_sunny), null) },
        )
    }

    item("category_ech") {
        PreferenceCategory(text = { Text(stringResource(Res.string.ech)) })
    }
    item("ech") {
        SwitchPreference(
            value = uiState.ech,
            onValueChange = { viewModel.setEch(it) },
            title = { Text(stringResource(Res.string.ech)) },
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
