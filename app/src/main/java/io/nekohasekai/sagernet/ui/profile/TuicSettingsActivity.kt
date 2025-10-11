package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.AddRoad
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
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
import io.nekohasekai.sagernet.compose.PasswordPreference
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.compose.UIntegerTextField
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class TuicSettingsActivity : ProfileSettingsActivity<TuicBean>() {

    override val viewModel by viewModels<TuicSettingsViewModel>()

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as TuicUiState

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
        item("uuid") {
            TextFieldPreference(
                value = uiState.uuid,
                onValueChange = { viewModel.setUuid(it) },
                title = { Text(stringResource(R.string.uuid)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Person, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.uuid)) },
                valueToText = { it },
            )
        }
        item("token") {
            PasswordPreference(
                value = uiState.token,
                onValueChange = { viewModel.setToken(it) },
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
            )
        }
        item("udp_relay_mode") {
            ListPreference(
                value = uiState.udpRelayMode,
                values = listOf("native", "quic", "UDP over Stream"),
                onValueChange = { viewModel.setUdpRelayMode(it) },
                title = { Text(stringResource(R.string.tuic_udp_relay_mode)) },
                icon = { Icon(Icons.Filled.AddRoad, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.udpRelayMode)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
        }
        item("congestion_controller") {
            ListPreference(
                value = uiState.congestionController,
                values = congestionControls,
                onValueChange = { viewModel.setCongestionController(it) },
                title = { Text(stringResource(R.string.tuic_congestion_controller)) },
                icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.congestionController)) },
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
        item("sni") {
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
        item("zero_rtt") {
            SwitchPreference(
                value = uiState.zeroRTT,
                onValueChange = { viewModel.setZeroRTT(it) },
                title = { Text(stringResource(R.string.tuic_reduce_rtt)) },
                icon = { Icon(Icons.Filled.FlightTakeoff, null) },
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

        item("category_ech") {
            PreferenceCategory(text = { Text(stringResource(R.string.ech)) })
        }
        item("ech") {
            SwitchPreference(
                value = uiState.ech,
                onValueChange = { viewModel.setEch(it) },
                title = { Text(stringResource(R.string.enable)) },
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