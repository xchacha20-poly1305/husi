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
import io.nekohasekai.sagernet.fmt.trusttunnel.TrustTunnelBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class TrustTunnelSettingsActivity : ProfileSettingsActivity<TrustTunnelBean>() {

    private val congestionControls = listOf(
        "",
        "bbr",
        "cubic",
        "reno",
        "bbr_standard",
        "bbr2",
        "bbr_variant",
    )

    override val viewModel by viewModels<TrustTunnelSettingsViewModel>()

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as TrustTunnelUiState

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
        item("port") {
            TextFieldPreference(
                value = uiState.port,
                onValueChange = { viewModel.setPort(it) },
                title = { Text(stringResource(R.string.server_port)) },
                textToValue = { it.toIntOrNull() ?: 443 },
                icon = { Icon(ImageVector.vectorResource(R.drawable.directions_boat), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.port)) },
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
                title = { Text(stringResource(R.string.username)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.person), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.username)) },
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
                title = { Text(stringResource(R.string.health_check)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.ecg), null) },
            )
        }
        item("quic") {
            SwitchPreference(
                value = uiState.quic,
                onValueChange = { viewModel.setQuic(it) },
                title = { Text(stringResource(R.string.quic)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.fast_forward), null) },
            )
        }
        item("quic_congestion_control") {
            ListPreference(
                value = uiState.quicCongestionControl,
                values = congestionControls,
                onValueChange = { viewModel.setQuicCongestionControl(it) },
                title = { Text(stringResource(R.string.tuic_congestion_controller)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.traffic), null) },
                enabled = uiState.quic,
                summary = { Text(LocalContext.current.contentOrUnset(uiState.quicCongestionControl)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
        }

        item("category_tls") {
            PreferenceCategory(text = { Text(stringResource(R.string.security_settings)) })
        }
        item("server_name") {
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
        item("allow_insecure") {
            SwitchPreference(
                value = uiState.allowInsecure,
                onValueChange = { viewModel.setAllowInsecure(it) },
                title = { Text(stringResource(R.string.allow_insecure)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.lock_open), null) },
            )
        }
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
        item("utls_fingerprint") {
            ListPreference(
                value = uiState.utlsFingerprint,
                values = fingerprints,
                onValueChange = { viewModel.setUtlsFingerprint(it) },
                title = { Text(stringResource(R.string.utls_fingerprint)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.fingerprint), null) },
                enabled = !uiState.quic,
                summary = { Text(LocalContext.current.contentOrUnset(uiState.utlsFingerprint)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
        }
        item("tls_fragment") {
            SwitchPreference(
                value = uiState.tlsFragment,
                onValueChange = { viewModel.setTlsFragment(it) },
                title = { Text(stringResource(R.string.tls_fragment)) },
                enabled = !uiState.tlsRecordFragment && !uiState.quic,
                icon = { Icon(ImageVector.vectorResource(R.drawable.texture), null) },
            )
        }
        item("tls_fragment_fallback_delay") {
            TextFieldPreference(
                value = uiState.tlsFragmentFallbackDelay,
                onValueChange = { viewModel.setTlsFragmentFallbackDelay(it) },
                title = { Text(stringResource(R.string.tls_fragment_fallback_delay)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.timelapse), null) },
                enabled = uiState.tlsFragment && !uiState.quic,
                summary = { Text(LocalContext.current.contentOrUnset(uiState.tlsFragmentFallbackDelay)) },
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
                title = { Text(stringResource(R.string.tls_record_fragment)) },
                enabled = !uiState.tlsFragment && !uiState.quic,
                icon = { Icon(ImageVector.vectorResource(R.drawable.wb_sunny), null) },
            )
        }

        item("category_ech") {
            PreferenceCategory(text = { Text(stringResource(R.string.ech)) })
        }
        item("ech") {
            SwitchPreference(
                value = uiState.ech,
                onValueChange = { viewModel.setEch(it) },
                title = { Text(stringResource(R.string.ech)) },
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
}
