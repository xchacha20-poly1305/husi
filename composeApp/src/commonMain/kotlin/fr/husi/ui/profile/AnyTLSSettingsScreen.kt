package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.DurationTextField
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.PreferenceMaskColors
import fr.husi.compose.PreferenceShapes
import fr.husi.compose.MaskedIcon
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.ktx.contentOrUnset
import fr.husi.platform.PlatformInfo
import fr.husi.resources.Res
import fr.husi.resources.allow_insecure
import fr.husi.resources.alpn
import fr.husi.resources.block
import fr.husi.resources.cert_public_key_sha256
import fr.husi.resources.certificates
import fr.husi.resources.computer_cancel
import fr.husi.resources.copyright
import fr.husi.resources.directions_boat
import fr.husi.resources.domino_mask
import fr.husi.resources.ech
import fr.husi.resources.ech_config
import fr.husi.resources.ech_query_server_name
import fr.husi.resources.emoji_symbols
import fr.husi.resources.fingerprint
import fr.husi.resources.gesture
import fr.husi.resources.idle_session_check_interval
import fr.husi.resources.idle_session_timeout
import fr.husi.resources.lock
import fr.husi.resources.lock_open
import fr.husi.resources.min_idle_session
import fr.husi.resources.mutual_tls
import fr.husi.resources.nfc
import fr.husi.resources.not_set
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.proxy_cat
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
import fr.husi.resources.timer
import fr.husi.resources.tls_fragment
import fr.husi.resources.tls_fragment_fallback_delay
import fr.husi.resources.tls_record_fragment
import fr.husi.resources.tls_spoof
import fr.husi.resources.tls_spoof_method
import fr.husi.resources.toc
import fr.husi.resources.tuic_disable_sni
import fr.husi.resources.utls_fingerprint
import fr.husi.resources.vpn_key
import fr.husi.resources.wb_sunny
import fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnyTLSSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: AnyTLSSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        AnyTLSSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        anyTlsSettings(uiState as AnyTLSUiState, viewModel)
    }
}

private fun LazyListScope.anyTlsSettings(
    uiState: AnyTLSUiState,
    viewModel: AnyTLSSettingsViewModel,
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
            value = uiState.port,
            onValueChange = { viewModel.setPort(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull() ?: 443 },
            icon = {
                MaskedIcon(
                    Res.drawable.directions_boat,
                    color = PreferenceMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.idleSessionCheckInterval,
            onValueChange = { viewModel.setIdleSessionCheckInterval(it) },
            title = { Text(stringResource(Res.string.idle_session_check_interval)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.timelapse, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.idleSessionCheckInterval)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                DurationTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.idleSessionTimeout,
            onValueChange = { viewModel.setIdleSessionTimeout(it) },
            title = { Text(stringResource(Res.string.idle_session_timeout)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.timer, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.idleSessionTimeout)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                DurationTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.minIdleSession,
            onValueChange = { viewModel.setMinIdleSession(it) },
            title = { Text(stringResource(Res.string.min_idle_session)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(Res.drawable.gesture, color = PreferenceMaskColors.IconCyan)
            },
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
    preferenceGroup(key = "server_name") {
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
        PreferenceDivider()
        SwitchPreference(
            value = uiState.allowInsecure,
            onValueChange = { viewModel.setAllowInsecure(it) },
            title = { Text(stringResource(Res.string.allow_insecure)) },
            icon = {
                MaskedIcon(
                    Res.drawable.lock_open,
                    color = PreferenceMaskColors.IconCyan,
                    shape = PreferenceShapes.risk(),
                )
            },
        )
        PreferenceDivider()
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
        PreferenceDivider()
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
        ListPreference(
            value = uiState.utlsFingerprint,
            values = fingerprints,
            onValueChange = { viewModel.setUtlsFingerprint(it) },
            title = { Text(stringResource(Res.string.utls_fingerprint)) },
            icon = {
                MaskedIcon(
                    Res.drawable.fingerprint,
                    color = PreferenceMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.utlsFingerprint)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }
    if (!PlatformInfo.isAndroid) {
        preferenceGroup(key = "tls_spoof") {
            TextFieldPreference(
                value = uiState.tlsSpoof,
                onValueChange = { viewModel.setTlsSpoof(it) },
                title = { Text(stringResource(Res.string.tls_spoof)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.domino_mask,
                        color = PreferenceMaskColors.IconCyan,
                    )
                },
                summary = { Text(contentOrUnset(uiState.tlsSpoof)) },
                valueToText = { it },
            )
            PreferenceDivider()
            ListPreference(
                value = uiState.tlsSpoofMethod,
                values = tlsSpoofMethod,
                onValueChange = { viewModel.setTlsSpoofMethod(it) },
                title = { Text(stringResource(Res.string.tls_spoof_method)) },
                enabled = uiState.tlsSpoof.isNotBlank(),
                icon = {
                    MaskedIcon(
                        Res.drawable.computer_cancel,
                        color = PreferenceMaskColors.IconCyan,
                    )
                },
                summary = { Text(contentOrUnset(uiState.tlsSpoofMethod)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
        }
    }
    preferenceGroup(key = "disable_sni") {
        SwitchPreference(
            value = uiState.disableSNI,
            onValueChange = { viewModel.setDisableSNI(it) },
            title = { Text(stringResource(Res.string.tuic_disable_sni)) },
            icon = {
                MaskedIcon(Res.drawable.block, color = PreferenceMaskColors.IconCyan)
            },
        )
        PreferenceDivider()
        SwitchPreference(
            value = uiState.tlsFragment,
            onValueChange = { viewModel.setTlsFragment(it) },
            title = { Text(stringResource(Res.string.tls_fragment)) },
            enabled = !uiState.tlsRecordFragment,
            icon = {
                MaskedIcon(Res.drawable.texture, color = PreferenceMaskColors.IconCyan)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.tlsFragmentFallbackDelay,
            onValueChange = { viewModel.setTlsFragmentFallbackDelay(it) },
            title = { Text(stringResource(Res.string.tls_fragment_fallback_delay)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.timelapse, color = PreferenceMaskColors.IconCyan)
            },
            enabled = uiState.tlsFragment,
            summary = { Text(contentOrUnset(uiState.tlsFragmentFallbackDelay)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                DurationTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        SwitchPreference(
            value = uiState.tlsRecordFragment,
            onValueChange = { viewModel.setTlsRecordFragment(it) },
            title = { Text(stringResource(Res.string.tls_record_fragment)) },
            enabled = !uiState.tlsFragment,
            icon = {
                MaskedIcon(Res.drawable.wb_sunny, color = PreferenceMaskColors.IconCyan)
            },
        )
    }

    item("category_ech") {
        PreferenceCategory(text = { Text(stringResource(Res.string.ech)) })
    }
    preferenceGroup(key = "ech") {
        SwitchPreference(
            value = uiState.ech,
            onValueChange = { viewModel.setEch(it) },
            title = { Text(stringResource(Res.string.ech)) },
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
    }
}
