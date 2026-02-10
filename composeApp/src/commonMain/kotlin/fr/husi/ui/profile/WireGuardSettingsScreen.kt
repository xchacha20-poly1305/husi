package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.UIntegerTextField
import fr.husi.ktx.contentOrUnset
import me.zhanghai.compose.preference.TextFieldPreference
import fr.husi.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WireGuardSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: WireGuardSettingsViewModel = viewModel { WireGuardSettingsViewModel() }

    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
    ) { scope, uiState, _ ->
        scope.wireGuardSettings(uiState as WireGuardUiState, viewModel)
    }
}

private fun LazyListScope.wireGuardSettings(
    uiState: WireGuardUiState,
    viewModel: WireGuardSettingsViewModel,
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
            textToValue = { it.toIntOrNull() ?: 51820 },
            icon = { Icon(vectorResource(Res.drawable.directions_boat), null) },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }
    item("local_address") {
        TextFieldPreference(
            value = uiState.localAddress,
            onValueChange = { viewModel.setLocalAddress(it) },
            title = { Text(stringResource(Res.string.wireguard_local_address)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.domain), null) },
            summary = { Text(contentOrUnset(uiState.localAddress)) },
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
            title = { Text(stringResource(Res.string.ssh_private_key)) },
            icon = { Icon(vectorResource(Res.drawable.vpn_key), null) },
        )
    }
    item("public_key") {
        TextFieldPreference(
            value = uiState.publicKey,
            onValueChange = { viewModel.setPublicKey(it) },
            title = { Text(stringResource(Res.string.wireguard_public_key)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.copyright), null) },
            summary = { Text(contentOrUnset(uiState.publicKey)) },
            valueToText = { it },
        )
    }
    item("pre_shared_key") {
        PasswordPreference(
            value = uiState.preSharedKey,
            onValueChange = { viewModel.setPreSharedKey(it) },
            title = { Text(stringResource(Res.string.wireguard_psk)) },
        )
    }
    item("mtu") {
        TextFieldPreference(
            value = uiState.mtu,
            onValueChange = { viewModel.setMtu(it) },
            title = { Text(stringResource(Res.string.mtu)) },
            textToValue = { it.toIntOrNull() ?: 1420 },
            icon = { Icon(vectorResource(Res.drawable.public_icon), null) },
            summary = { Text(contentOrUnset(uiState.mtu.toString())) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }
    item("reserved") {
        TextFieldPreference(
            value = uiState.reserved,
            onValueChange = { viewModel.setReserved(it) },
            title = { Text(stringResource(Res.string.reserved)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.fingerprint), null) },
            summary = { Text(contentOrUnset(uiState.reserved)) },
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
            title = { Text(stringResource(Res.string.listen_port)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = { Icon(vectorResource(Res.drawable.stream), null) },
            summary = { Text(contentOrUnset(uiState.listenPort)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }
    item("persistent_keepalive_interval") {
        TextFieldPreference(
            value = uiState.persistentKeepaliveInterval,
            onValueChange = { viewModel.setPersistentKeepaliveInterval(it) },
            title = { Text(stringResource(Res.string.persistent_keepalive_interval)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = { Icon(vectorResource(Res.drawable.replay), null) },
            summary = {
                Text(contentOrUnset(uiState.persistentKeepaliveInterval))
            },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }
}

