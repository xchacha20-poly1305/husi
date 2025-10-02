package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.PasswordPreference
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.compose.UIntegerTextField
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class JuicitySettingsActivity : ProfileSettingsActivity<JuicityBean>() {

    override val viewModel by viewModels<JuicitySettingsViewModel>()

    override fun LazyListScope.settings(state: ProfileSettingsUiState) {
        state as JuicityUiState

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
                },
            )
        }
        item("uuid") {
            TextFieldPreference(
                value = state.uuid,
                onValueChange = { viewModel.setUuid(it) },
                title = { Text(stringResource(R.string.uuid)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Password, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.uuid)) },
                valueToText = { it },
            )
        }
        item("password") {
            PasswordPreference(
                value = state.password,
                onValueChange = { viewModel.setPassword(it) },
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
        item("pin_cert_hash") {
            TextFieldPreference(
                value = state.pinSha256,
                onValueChange = { viewModel.setPinSha256(it) },
                title = { Text(stringResource(R.string.pinned_peer_certificate_chain_sha256)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.PushPin, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.pinSha256)) },
                valueToText = { it },
            )
        }
    }
}
