package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import fr.husi.Key
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.MaskedIcon
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.SwitchPreference
import fr.husi.compose.TextFieldPreference
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.collectAdvancedFeatureAsStateWithLifecycle
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.database.DataStore
import fr.husi.ktx.contentOrUnset
import fr.husi.platform.PlatformInfo
import fr.husi.resources.Res
import fr.husi.resources.add_road
import fr.husi.resources.code
import fr.husi.resources.domain
import fr.husi.resources.download
import fr.husi.resources.emoji_symbols
import fr.husi.resources.flight_takeoff
import fr.husi.resources.folder_open
import fr.husi.resources.home
import fr.husi.resources.label
import fr.husi.resources.link
import fr.husi.resources.listen_port
import fr.husi.resources.nat
import fr.husi.resources.outbond
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.proxy_cat
import fr.husi.resources.route
import fr.husi.resources.stream
import fr.husi.resources.tailscale_accept_routes
import fr.husi.resources.tailscale_advertise_exit_node
import fr.husi.resources.tailscale_advertise_fake_dns_routes
import fr.husi.resources.tailscale_advertise_fake_dns_routes_summary
import fr.husi.resources.tailscale_advertise_routes
import fr.husi.resources.tailscale_advertise_tags
import fr.husi.resources.tailscale_auth_key
import fr.husi.resources.tailscale_control_url
import fr.husi.resources.tailscale_ephemeral
import fr.husi.resources.tailscale_exit_node
import fr.husi.resources.tailscale_exit_node_allow_lan_access
import fr.husi.resources.tailscale_hostname
import fr.husi.resources.tailscale_ssh_server
import fr.husi.resources.tailscale_state_directory
import fr.husi.resources.taildrop_directory
import fr.husi.resources.timelapse
import fr.husi.resources.vpn_key
import fr.husi.ui.NavRoutes
import org.jetbrains.compose.resources.stringResource

@Composable
fun TailscaleSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: TailscaleSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        TailscaleSettingsViewModel()
    }
    val sshServerFeature by collectAdvancedFeatureAsStateWithLifecycle(Key.ADVANCED_TAILSCALE_SSH_SERVER)

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        tailscaleSettings(uiState as TailscaleUiState, viewModel, sshServerFeature != null)
    }
}

private fun LazyListScope.tailscaleSettings(
    uiState: TailscaleUiState,
    viewModel: TailscaleSettingsViewModel,
    isSSHServerEnabled: Boolean,
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
        TextFieldPreference(
            value = uiState.controlURL,
            onValueChange = { viewModel.setControlURL(it) },
            title = { Text(stringResource(Res.string.tailscale_control_url)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.link,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(uiState.controlURL)) },
            valueToText = { it },
        )
        PasswordPreference(
            value = uiState.authKey,
            onValueChange = { viewModel.setAuthKey(it) },
            title = { Text(stringResource(Res.string.tailscale_auth_key)) },
            icon = {
                MaskedIcon(
                    Res.drawable.vpn_key,
                    color = IconMaskColors.IconLightGreen,
                    shape = IconMaskShapes.credential(),
                )
            },
        )
        TextFieldPreference(
            value = uiState.hostname,
            onValueChange = { viewModel.setHostname(it) },
            title = { Text(stringResource(Res.string.tailscale_hostname)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.domain,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = { Text(contentOrUnset(uiState.hostname)) },
            valueToText = { it },
        )
        TextFieldPreference(
            value = uiState.stateDirectory,
            onValueChange = { viewModel.setStateDirectory(it) },
            title = { Text(stringResource(Res.string.tailscale_state_directory)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.folder_open,
                    color = IconMaskColors.IconLightYellow,
                )
            },
            summary = { Text(contentOrUnset(uiState.stateDirectory)) },
            valueToText = { it },
        )
        SwitchPreference(
            value = uiState.ephemeral,
            onValueChange = { viewModel.setEphemeral(it) },
            title = { Text(stringResource(Res.string.tailscale_ephemeral)) },
            icon = {
                MaskedIcon(
                    Res.drawable.timelapse,
                    color = IconMaskColors.IconLightOrange,
                )
            },
        )
        TextFieldPreference(
            value = uiState.listenPort,
            onValueChange = { viewModel.setListenPort(it) },
            title = { Text(stringResource(Res.string.listen_port)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(
                    Res.drawable.stream,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(uiState.listenPort)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }

    preferenceGroup {
        SwitchPreference(
            value = uiState.acceptRoutes,
            onValueChange = { viewModel.setAcceptRoutes(it) },
            title = { Text(stringResource(Res.string.tailscale_accept_routes)) },
            icon = {
                MaskedIcon(
                    Res.drawable.route,
                    color = IconMaskColors.IconCyan,
                    shape = IconMaskShapes.route(),
                )
            },
        )
        TextFieldPreference(
            value = uiState.exitNode,
            onValueChange = { viewModel.setExitNode(it) },
            title = { Text(stringResource(Res.string.tailscale_exit_node)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.outbond,
                    color = IconMaskColors.IconLightGreen,
                )
            },
            summary = { Text(contentOrUnset(uiState.exitNode)) },
            valueToText = { it },
        )
        SwitchPreference(
            value = uiState.exitNodeAllowLANAccess,
            onValueChange = { viewModel.setExitNodeAllowLANAccess(it) },
            title = { Text(stringResource(Res.string.tailscale_exit_node_allow_lan_access)) },
            icon = {
                MaskedIcon(
                    Res.drawable.home,
                    color = IconMaskColors.IconLightYellow,
                )
            },
        )
        TextFieldPreference(
            value = uiState.advertiseRoutes,
            onValueChange = { viewModel.setAdvertiseRoutes(it) },
            title = { Text(stringResource(Res.string.tailscale_advertise_routes)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.add_road,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = { Text(contentOrUnset(uiState.advertiseRoutes)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
        SwitchPreference(
            value = uiState.advertiseFakeDNSRoutes,
            onValueChange = { viewModel.setAdvertiseFakeDNSRoutes(it) },
            title = { Text(stringResource(Res.string.tailscale_advertise_fake_dns_routes)) },
            icon = {
                MaskedIcon(
                    Res.drawable.nat,
                    color = IconMaskColors.IconCyan,
                    shape = IconMaskShapes.route(),
                )
            },
            summary = { Text(stringResource(Res.string.tailscale_advertise_fake_dns_routes_summary)) },
        )
        SwitchPreference(
            value = uiState.advertiseExitNode,
            onValueChange = { viewModel.setAdvertiseExitNode(it) },
            title = { Text(stringResource(Res.string.tailscale_advertise_exit_node)) },
            icon = {
                MaskedIcon(
                    Res.drawable.flight_takeoff,
                    color = IconMaskColors.IconLightOrange,
                )
            },
        )
        TextFieldPreference(
            value = uiState.advertiseTags,
            onValueChange = { viewModel.setAdvertiseTags(it) },
            title = { Text(stringResource(Res.string.tailscale_advertise_tags)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.label,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(uiState.advertiseTags)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }

    // Android has no SSH server support, and serves received files from the default directory
    // through its documents provider.
    if (PlatformInfo.isAndroid) return

    preferenceGroup {
        if (isSSHServerEnabled) {
            SwitchPreference(
                value = uiState.sshServer,
                onValueChange = { viewModel.setSSHServer(it) },
                title = { Text(stringResource(Res.string.tailscale_ssh_server)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.code,
                        color = IconMaskColors.IconCoral,
                        shape = IconMaskShapes.risk(),
                    )
                },
            )
        }
        TextFieldPreference(
            value = uiState.taildropDirectory,
            onValueChange = { viewModel.setTaildropDirectory(it) },
            title = { Text(stringResource(Res.string.taildrop_directory)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.download,
                    color = IconMaskColors.IconLightGreen,
                )
            },
            summary = { Text(contentOrUnset(uiState.taildropDirectory)) },
            valueToText = { it },
        )
    }
}
