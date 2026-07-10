package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
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
import fr.husi.resources.Res
import fr.husi.resources.copyright
import fr.husi.resources.directions_boat
import fr.husi.resources.domain
import fr.husi.resources.emoji_symbols
import fr.husi.resources.fingerprint
import fr.husi.resources.listen_port
import fr.husi.resources.mtu
import fr.husi.resources.persistent_keepalive_interval
import fr.husi.resources.pre_shared_key
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.proxy_cat
import fr.husi.resources.public_icon
import fr.husi.resources.replay
import fr.husi.resources.reserved
import fr.husi.resources.router
import fr.husi.resources.server_address
import fr.husi.resources.server_port
import fr.husi.resources.ssh_private_key
import fr.husi.resources.stream
import fr.husi.resources.vpn_key
import fr.husi.resources.wireguard_local_address
import fr.husi.resources.wireguard_public_key
import fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WireGuardSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: WireGuardSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        WireGuardSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        wireGuardSettings(uiState as WireGuardUiState, viewModel)
    }
}

private fun LazyListScope.wireGuardSettings(
    uiState: WireGuardUiState,
    viewModel: WireGuardSettingsViewModel,
) {
    preferenceGroup {
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
    preferenceGroup {
        TextFieldPreference(
            value = uiState.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.router,
                    color = PreferenceMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(uiState.address)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.port,
            onValueChange = { viewModel.setPort(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull() ?: 51820 },
            icon = {
                MaskedIcon(
                    Res.drawable.directions_boat,
                    color = PreferenceMaskColors.IconLightOrange,
                )
            },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.localAddress,
            onValueChange = { viewModel.setLocalAddress(it) },
            title = { Text(stringResource(Res.string.wireguard_local_address)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.domain,
                    color = PreferenceMaskColors.IconLavender,
                )
            },
            summary = { Text(contentOrUnset(uiState.localAddress)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        PasswordPreference(
            value = uiState.privateKey,
            onValueChange = { viewModel.setPrivateKey(it) },
            title = { Text(stringResource(Res.string.ssh_private_key)) },
            icon = {
                MaskedIcon(
                    Res.drawable.vpn_key,
                    color = PreferenceMaskColors.IconLightGreen,
                    shape = PreferenceShapes.credential(),
                )
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.publicKey,
            onValueChange = { viewModel.setPublicKey(it) },
            title = { Text(stringResource(Res.string.wireguard_public_key)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.copyright, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.publicKey)) },
            valueToText = { it },
        )
        PreferenceDivider()
        PasswordPreference(
            value = uiState.preSharedKey,
            onValueChange = { viewModel.setPreSharedKey(it) },
            title = { Text(stringResource(Res.string.pre_shared_key)) },
            icon = {
                MaskedIcon(
                    Res.drawable.vpn_key,
                    color = PreferenceMaskColors.IconWarmGray,
                )
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.mtu,
            onValueChange = { viewModel.setMtu(it) },
            title = { Text(stringResource(Res.string.mtu)) },
            textToValue = { it.toIntOrNull() ?: 1420 },
            icon = {
                MaskedIcon(
                    Res.drawable.public_icon,
                    color = PreferenceMaskColors.IconLightYellow,
                )
            },
            summary = { Text(contentOrUnset(uiState.mtu.toString())) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.reserved,
            onValueChange = { viewModel.setReserved(it) },
            title = { Text(stringResource(Res.string.reserved)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.fingerprint,
                    color = PreferenceMaskColors.IconCoral,
                )
            },
            summary = { Text(contentOrUnset(uiState.reserved)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.listenPort,
            onValueChange = { viewModel.setListenPort(it) },
            title = { Text(stringResource(Res.string.listen_port)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(
                    Res.drawable.stream,
                    color = PreferenceMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(uiState.listenPort)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.persistentKeepaliveInterval,
            onValueChange = { viewModel.setPersistentKeepaliveInterval(it) },
            title = { Text(stringResource(Res.string.persistent_keepalive_interval)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(
                    Res.drawable.replay,
                    color = PreferenceMaskColors.IconLightOrange,
                )
            },
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
