package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Nat
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.PasswordPreference
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.compose.UIntegerTextField
import io.nekohasekai.sagernet.fmt.shadowquic.ShadowQUICBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class ShadowQUICSettingsActivity : ProfileSettingsActivity<ShadowQUICBean>() {

    override val viewModel by viewModels<ShadowQUICSettingsViewModel>()

    override fun LazyListScope.settings(state: ProfileSettingsUiState) {
        state as ShadowQUICUiState

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
        item("username") {
            TextFieldPreference(
                value = state.username,
                onValueChange = { viewModel.setUsername(it) },
                title = { Text(stringResource(R.string.username)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Texture, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.username)) },
                valueToText = { it },
            )
        }
        item("password") {
            PasswordPreference(
                value = state.password,
                onValueChange = { viewModel.setPassword(it) },
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
        item("congestion_control") {
            ListPreference(
                value = state.congestionControl,
                values = congestionControls,
                onValueChange = { viewModel.setCongestionControl(it) },
                title = { Text(stringResource(R.string.tuic_congestion_controller)) },
                icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, null) },
                summary = { Text(state.congestionControl) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
        }
        item("sni") {
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
        item("zero_rtt") {
            SwitchPreference(
                value = state.zeroRTT,
                onValueChange = { viewModel.setZeroRTT(it) },
                title = { Text(stringResource(R.string.tuic_reduce_rtt)) },
                icon = { Icon(Icons.Filled.FlightTakeoff, null) },
            )
        }
        item("initial_mtu") {
            TextFieldPreference(
                value = state.initialMtu,
                onValueChange = { viewModel.setInitialMtu(it) },
                title = { Text(stringResource(R.string.initial_mtu)) },
                textToValue = { it.toIntOrNull() ?: 1300 },
                icon = { Icon(Icons.Filled.Public, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.initialMtu)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                }
            )
        }
        item("minimum_mtu") {
            TextFieldPreference(
                value = state.minMtu,
                onValueChange = { viewModel.setMinMtu(it) },
                title = { Text(stringResource(R.string.minimum_mtu)) },
                textToValue = { it.toIntOrNull() ?: 1290 },
                icon = { Icon(Icons.Filled.DeveloperBoard, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.minMtu)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                }
            )
        }
        item("udp_over_stream") {
            SwitchPreference(
                value = state.udpOverStream,
                onValueChange = { viewModel.setUdpOverStream(it) },
                title = { Text(stringResource(R.string.udp_over_stream)) },
                icon = { Icon(Icons.Filled.Nat, null) },
            )
        }
    }
}
