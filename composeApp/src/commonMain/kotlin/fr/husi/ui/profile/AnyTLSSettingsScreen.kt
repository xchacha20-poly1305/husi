package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.DurationTextField
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.UIntegerTextField
import fr.husi.ktx.contentOrUnset
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import fr.husi.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnyTLSSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: AnyTLSSettingsViewModel = viewModel { AnyTLSSettingsViewModel() }

    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
    ) { scope, uiState, _ ->
        scope.anyTlsSettings(uiState as AnyTLSUiState, viewModel)
    }
}

private fun LazyListScope.anyTlsSettings(
    uiState: AnyTLSUiState,
    viewModel: AnyTLSSettingsViewModel,
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
    item("password") {
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
        )
    }
    item("idle_session_check_interval") {
        TextFieldPreference(
            value = uiState.idleSessionCheckInterval,
            onValueChange = { viewModel.setIdleSessionCheckInterval(it) },
            title = { Text(stringResource(Res.string.idle_session_check_interval)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.timelapse), null) },
            summary = { Text(contentOrUnset(uiState.idleSessionCheckInterval)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                DurationTextField(value, onValueChange, onOk)
            },
        )
    }
    item("idle_session_timeout") {
        TextFieldPreference(
            value = uiState.idleSessionTimeout,
            onValueChange = { viewModel.setIdleSessionTimeout(it) },
            title = { Text(stringResource(Res.string.idle_session_timeout)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.timer), null) },
            summary = { Text(contentOrUnset(uiState.idleSessionTimeout)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                DurationTextField(value, onValueChange, onOk)
            },
        )
    }
    item("min_idle_session") {
        TextFieldPreference(
            value = uiState.minIdleSession,
            onValueChange = { viewModel.setMinIdleSession(it) },
            title = { Text(stringResource(Res.string.min_idle_session)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = { Icon(vectorResource(Res.drawable.gesture), null) },
            summary = {
                val text = if (uiState.minIdleSession == 0) {
                    stringResource(Res.string.not_set)
                } else {
                    uiState.minIdleSession.toString()
                }
                Text(text)
            },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
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
            summary = { Text(contentOrUnset(uiState.utlsFingerprint)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
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
    item("tls_fragment") {
        SwitchPreference(
            value = uiState.tlsFragment,
            onValueChange = { viewModel.setTlsFragment(it) },
            title = { Text(stringResource(Res.string.tls_fragment)) },
            enabled = !uiState.tlsRecordFragment,
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
            enabled = uiState.tlsFragment,
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
            enabled = !uiState.tlsFragment,
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

