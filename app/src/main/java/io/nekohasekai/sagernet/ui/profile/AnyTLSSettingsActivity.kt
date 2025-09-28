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

    override fun LazyListScope.settings(state: ProfileSettingsUiState) {
        state as AnyTLSUiState

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
        item("port") {
            TextFieldPreference(
                value = state.port,
                onValueChange = { viewModel.setPort(it) },
                title = { Text(stringResource(R.string.server_port)) },
                textToValue = { it.toIntOrNull() ?: 443 },
                icon = { Icon(Icons.Filled.DirectionsBoat, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.port)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                }
            )
        }
        item("password") {
            PasswordPreference(
                value = state.password,
                onValueChange = { viewModel.setPassword(it) },
            )
        }
        item("idle_session_check_interval") {
            TextFieldPreference(
                value = state.idleSessionCheckInterval,
                onValueChange = { viewModel.setIdleSessionCheckInterval(it) },
                title = { Text(stringResource(R.string.idle_session_check_interval)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Timelapse, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.idleSessionCheckInterval)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    DurationTextField(value, onValueChange, onOk)
                },
            )
        }
        item("idle_session_timeout") {
            TextFieldPreference(
                value = state.idleSessionTimeout,
                onValueChange = { viewModel.setIdleSessionTimeout(it) },
                title = { Text(stringResource(R.string.idle_session_timeout)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Timer, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.idleSessionTimeout)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    DurationTextField(value, onValueChange, onOk)
                },
            )
        }
        item("min_idle_session") {
            TextFieldPreference(
                value = state.minIdleSession,
                onValueChange = { viewModel.setMinIdleSession(it) },
                title = { Text(stringResource(R.string.min_idle_session)) },
                textToValue = { it.toIntOrNull() ?: 0 },
                icon = { Icon(Icons.Filled.Gesture, null) },
                summary = {
                    val text = if (state.minIdleSession == 0) {
                        stringResource(androidx.preference.R.string.not_set)
                    } else {
                        state.minIdleSession.toString()
                    }
                    Text(text)
                },
            )
        }

        item("category_tls") {
            PreferenceCategory(text = { Text(stringResource(R.string.security_settings)) })
        }
        item("server_name") {
            TextFieldPreference(
                value = state.sni,
                onValueChange = { viewModel.setSni(it) },
                title = { Text(stringResource(R.string.sni)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Copyright, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.sni)) },
                valueToText = { it },
            )
        }
        item("allow_insecure") {
            SwitchPreference(
                value = state.allowInsecure,
                onValueChange = { viewModel.setAllowInsecure(it) },
                title = { Text(stringResource(R.string.allow_insecure)) },
                icon = { Icon(Icons.Filled.LockOpen, null) },
            )
        }
        item("alpn") {
            TextFieldPreference(
                value = state.alpn,
                onValueChange = { viewModel.setAlpn(it) },
                title = { Text(stringResource(R.string.alpn)) },
                textToValue = { it },
                icon = { Icon(Icons.AutoMirrored.Filled.Toc, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.alpn)) },
                valueToText = { it },
            )
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
        item("utls_fingerprint") {
            ListPreference(
                value = state.utlsFingerprint,
                values = fingerprints,
                onValueChange = { viewModel.setUtlsFingerprint(it) },
                title = { Text(stringResource(R.string.utls_fingerprint)) },
                icon = { Icon(Icons.Filled.Fingerprint, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.utlsFingerprint)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
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
        item("tls_fragment") {
            SwitchPreference(
                value = state.tlsFragment,
                onValueChange = { viewModel.setTlsFragment(it) },
                title = { Text(stringResource(R.string.tls_fragment)) },
                icon = { Icon(Icons.Filled.Texture, null) },
            )
        }
        item("tls_fragment_fallback_delay") {
            TextFieldPreference(
                value = state.tlsFragmentFallbackDelay,
                onValueChange = { viewModel.setTlsFragmentFallbackDelay(it) },
                title = { Text(stringResource(R.string.tls_fragment_fallback_delay)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Timelapse, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.tlsFragmentFallbackDelay)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    DurationTextField(value, onValueChange, onOk)
                },
            )
        }
        item("tls_record_fragment") {
            SwitchPreference(
                value = state.tlsRecordFragment,
                onValueChange = { viewModel.setTlsRecordFragment(it) },
                title = { Text(stringResource(R.string.tls_record_fragment)) },
                icon = { Icon(Icons.Filled.WbSunny, null) },
            )
        }
        item("ech") {
            SwitchPreference(
                value = state.ech,
                onValueChange = { viewModel.setEch(it) },
                title = { Text(stringResource(R.string.ech)) },
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
                summary = { Text(LocalContext.current.contentOrUnset(state.echConfig)) },
                valueToText = { it },
            )
        }
    }
}