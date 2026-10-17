package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.MaskedIcon
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.TextFieldPreference
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.copyright
import fr.husi.resources.dns
import fr.husi.resources.emoji_symbols
import fr.husi.resources.fingerprint
import fr.husi.resources.link
import fr.husi.resources.location_on
import fr.husi.resources.pre_shared_key
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.proxy_cat
import fr.husi.resources.ssh_private_key
import fr.husi.resources.tailcat_derp_map_url
import fr.husi.resources.tailcat_derp_region
import fr.husi.resources.tailcat_derp_servers
import fr.husi.resources.tailcat_server_disco_key
import fr.husi.resources.tailcat_server_public_key
import fr.husi.resources.vpn_key
import fr.husi.ui.NavRoutes
import org.jetbrains.compose.resources.stringResource

@Composable
fun TailcatSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: TailcatSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        TailcatSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        tailcatSettings(uiState as TailcatUiState, viewModel)
    }
}

private fun LazyListScope.tailcatSettings(
    uiState: TailcatUiState,
    viewModel: TailcatSettingsViewModel,
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
                    color = IconMaskColors.IconCyan,
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
        PasswordPreference(
            value = uiState.privateKey,
            onValueChange = { viewModel.setPrivateKey(it) },
            title = { Text(stringResource(Res.string.ssh_private_key)) },
            icon = {
                MaskedIcon(
                    Res.drawable.vpn_key,
                    color = IconMaskColors.IconLightGreen,
                    shape = IconMaskShapes.credential(),
                )
            },
        )
        TextFieldPreference(
            value = uiState.serverPublicKey,
            onValueChange = { viewModel.setServerPublicKey(it) },
            title = { Text(stringResource(Res.string.tailcat_server_public_key)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.copyright, color = IconMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.serverPublicKey)) },
            valueToText = { it },
        )
        TextFieldPreference(
            value = uiState.serverDiscoKey,
            onValueChange = { viewModel.setServerDiscoKey(it) },
            title = { Text(stringResource(Res.string.tailcat_server_disco_key)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.fingerprint, color = IconMaskColors.IconLavender)
            },
            summary = { Text(contentOrUnset(uiState.serverDiscoKey)) },
            valueToText = { it },
        )
        PasswordPreference(
            value = uiState.preSharedKey,
            onValueChange = { viewModel.setPreSharedKey(it) },
            title = { Text(stringResource(Res.string.pre_shared_key)) },
            icon = {
                MaskedIcon(
                    resource = Res.drawable.vpn_key,
                    color = IconMaskColors.IconCoral,
                    shape = IconMaskShapes.credential(),
                )
            },
        )
    }

    preferenceGroup {
        TextFieldPreference(
            value = uiState.derpMapURL,
            onValueChange = { viewModel.setDerpMapURL(it) },
            title = { Text(stringResource(Res.string.tailcat_derp_map_url)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.link,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(uiState.derpMapURL)) },
            valueToText = { it },
        )
        TextFieldPreference(
            value = uiState.derpRegion,
            onValueChange = { viewModel.setDerpRegion(it) },
            title = { Text(stringResource(Res.string.tailcat_derp_region)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(
                    Res.drawable.location_on,
                    color = IconMaskColors.IconLightOrange,
                )
            },
            summary = { Text(contentOrUnset(uiState.derpRegion)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        TextFieldPreference(
            value = uiState.derpServers,
            onValueChange = { viewModel.setDerpServers(it) },
            title = { Text(stringResource(Res.string.tailcat_derp_servers)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.dns,
                    color = IconMaskColors.IconLightYellow,
                )
            },
            summary = { Text(contentOrUnset(uiState.derpServers)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
}
