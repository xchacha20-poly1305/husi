package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Timer
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
import io.nekohasekai.sagernet.fmt.anytls.AnyTLSBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class AnyTLSSettingsActivity : ProfileSettingsActivity<AnyTLSBean>() {

    override val viewModel by viewModels<AnyTLSSettingsViewModel>()

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as AnyTLSUiState

        item("name") {
            TextFieldPreference(
                value = uiState.name,
                onValueChange = { viewModel.setName(it) },
                title = { Text(stringResource(R.string.profile_name)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.EmojiSymbols, null) },
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
                icon = { Icon(Icons.Filled.Router, null) },
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
                icon = { Icon(Icons.Filled.DirectionsBoat, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.port)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                }
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
                title = { Text(stringResource(R.string.idle_session_check_interval)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Timelapse, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.idleSessionCheckInterval)) },
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
                title = { Text(stringResource(R.string.idle_session_timeout)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Timer, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.idleSessionTimeout)) },
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
                title = { Text(stringResource(R.string.min_idle_session)) },
                textToValue = { it.toIntOrNull() ?: 0 },
                icon = { Icon(Icons.Filled.Gesture, null) },
                summary = {
                    val text = if (uiState.minIdleSession == 0) {
                        stringResource(R.string.not_set)
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
            PreferenceCategory(text = { Text(stringResource(R.string.security_settings)) })
        }
        item("server_name") {
            TextFieldPreference(
                value = uiState.sni,
                onValueChange = { viewModel.setSni(it) },
                title = { Text(stringResource(R.string.sni)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Copyright, null) },
                enabled = !uiState.disableSNI,
                summary = { Text(LocalContext.current.contentOrUnset(uiState.sni)) },
                valueToText = { it },
            )
        }
        item("allow_insecure") {
            SwitchPreference(
                value = uiState.allowInsecure,
                onValueChange = { viewModel.setAllowInsecure(it) },
                title = { Text(stringResource(R.string.allow_insecure)) },
                icon = { Icon(Icons.Filled.LockOpen, null) },
            )
        }
        item("alpn") {
            TextFieldPreference(
                value = uiState.alpn,
                onValueChange = { viewModel.setAlpn(it) },
                title = { Text(stringResource(R.string.alpn)) },
                textToValue = { it },
                icon = { Icon(Icons.AutoMirrored.Filled.Toc, null) },
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
                icon = { Icon(Icons.Filled.VpnKey, null) },
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
                icon = { Icon(Icons.Filled.WbSunny, null) },
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
                icon = { Icon(Icons.Filled.Fingerprint, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.utlsFingerprint)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
        }
        item("disable_sni") {
            SwitchPreference(
                value = uiState.disableSNI,
                onValueChange = { viewModel.setDisableSNI(it) },
                title = { Text(stringResource(R.string.tuic_disable_sni)) },
                icon = { Icon(Icons.Filled.Block, null) },
            )
        }
        item("tls_fragment") {
            SwitchPreference(
                value = uiState.tlsFragment,
                onValueChange = { viewModel.setTlsFragment(it) },
                title = { Text(stringResource(R.string.tls_fragment)) },
                enabled = !uiState.tlsRecordFragment,
                icon = { Icon(Icons.Filled.Texture, null) },
            )
        }
        item("tls_fragment_fallback_delay") {
            TextFieldPreference(
                value = uiState.tlsFragmentFallbackDelay,
                onValueChange = { viewModel.setTlsFragmentFallbackDelay(it) },
                title = { Text(stringResource(R.string.tls_fragment_fallback_delay)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Timelapse, null) },
                enabled = uiState.tlsFragment,
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
                enabled = !uiState.tlsFragment,
                icon = { Icon(Icons.Filled.WbSunny, null) },
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
                icon = { Icon(Icons.Filled.Security, null) },
            )
        }
        item("ech_config") {
            TextFieldPreference(
                value = uiState.echConfig,
                onValueChange = { viewModel.setEchConfig(it) },
                title = { Text(stringResource(R.string.ech_config)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Nfc, null) },
                enabled = uiState.ech,
                summary = { Text(LocalContext.current.contentOrUnset(uiState.echConfig)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
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
                icon = { Icon(Icons.Filled.Lock, null) },
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
                icon = { Icon(Icons.Filled.VpnKey, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.clientKey)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
    }
}