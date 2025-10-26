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
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.MultilineTextField
import io.nekohasekai.sagernet.compose.PasswordPreference
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.compose.UIntegerTextField
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class WireGuardSettingsActivity : ProfileSettingsActivity<WireGuardBean>() {

    override val viewModel by viewModels<WireGuardSettingsViewModel>()

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as WireGuardUiState

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
                textToValue = { it.toIntOrNull() ?: 51820 },
                icon = { Icon(ImageVector.vectorResource(R.drawable.directions_boat), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.port)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                }
            )
        }
        item("local_address") {
            TextFieldPreference(
                value = uiState.localAddress,
                onValueChange = { viewModel.setLocalAddress(it) },
                title = { Text(stringResource(R.string.wireguard_local_address)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.domain), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.localAddress)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
        item("private_key") {
            PasswordPreference(
                value = uiState.privateKey,
                onValueChange = { viewModel.setPrivateKey(it) },
                title = { Text(stringResource(R.string.ssh_private_key)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.vpn_key), null) },
            )
        }
        item("public_key") {
            TextFieldPreference(
                value = uiState.publicKey,
                onValueChange = { viewModel.setPublicKey(it) },
                title = { Text(stringResource(R.string.wireguard_public_key)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.copyright), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.publicKey)) },
                valueToText = { it },
            )
        }
        item("pre_shared_key") {
            PasswordPreference(
                value = uiState.preSharedKey,
                onValueChange = { viewModel.setPreSharedKey(it) },
                title = { Text(stringResource(R.string.wireguard_psk)) },
            )
        }
        item("mtu") {
            TextFieldPreference(
                value = uiState.mtu,
                onValueChange = { viewModel.setMtu(it) },
                title = { Text(stringResource(R.string.mtu)) },
                textToValue = { it.toIntOrNull() ?: 1420 },
                icon = { Icon(ImageVector.vectorResource(R.drawable.public_icon), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.mtu.toString())) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                }
            )
        }
        item("reserved") {
            TextFieldPreference(
                value = uiState.reserved,
                onValueChange = { viewModel.setReserved(it) },
                title = { Text(stringResource(R.string.reserved)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.fingerprint), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.reserved)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
        item("listen_port") {
            TextFieldPreference(
                value = uiState.listenPort,
                onValueChange = { viewModel.setListenPort(it) },
                title = { Text(stringResource(R.string.listen_port)) },
                textToValue = { it.toIntOrNull() ?: 0 },
                icon = { Icon(ImageVector.vectorResource(R.drawable.stream), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.listenPort)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                }
            )
        }
        item("persistent_keepalive_interval") {
            TextFieldPreference(
                value = uiState.persistentKeepaliveInterval,
                onValueChange = { viewModel.setPersistentKeepaliveInterval(it) },
                title = { Text(stringResource(R.string.persistent_keepalive_interval)) },
                textToValue = { it.toIntOrNull() ?: 0 },
                icon = { Icon(ImageVector.vectorResource(R.drawable.replay), null) },
                summary = {
                    Text(LocalContext.current.contentOrUnset(uiState.persistentKeepaliveInterval))
                },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                }
            )
        }
    }
}