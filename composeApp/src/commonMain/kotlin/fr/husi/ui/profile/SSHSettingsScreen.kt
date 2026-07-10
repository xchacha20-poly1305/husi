package fr.husi.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
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
import fr.husi.fmt.ssh.SSHBean
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.compare_arrows
import fr.husi.resources.copyright
import fr.husi.resources.directions_boat
import fr.husi.resources.emoji_symbols
import fr.husi.resources.hysteria_auth_type
import fr.husi.resources.password
import fr.husi.resources.person
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.proxy_cat
import fr.husi.resources.router
import fr.husi.resources.server_address
import fr.husi.resources.server_port
import fr.husi.resources.ssh_auth_type_none
import fr.husi.resources.ssh_private_key
import fr.husi.resources.ssh_private_key_passphrase
import fr.husi.resources.ssh_public_key
import fr.husi.resources.username
import fr.husi.resources.vpn_key
import fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SSHSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: SSHSettingsViewModel =
        profileEditorViewModel(profileId = profileId, isSubscription = isSubscription) {
            SSHSettingsViewModel()
        }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        sshSettings(uiState as SshUiState, viewModel)
    }
}

private fun LazyListScope.sshSettings(uiState: SshUiState, viewModel: SSHSettingsViewModel) {
    item("category_proxy") {
        PreferenceCategory(text = { Text(stringResource(Res.string.proxy_cat)) })
    }
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
        PreferenceDivider()
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
            textToValue = { it.toIntOrNull() ?: 22 },
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
            value = uiState.username,
            onValueChange = { viewModel.setUsername(it) },
            title = { Text(stringResource(Res.string.username)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.person,
                    color = PreferenceMaskColors.IconLavender,
                )
            },
            summary = { Text(contentOrUnset(uiState.username)) },
            valueToText = { it },
        )
        PreferenceDivider()
        fun authType(type: Int) =
            when (type) {
                SSHBean.AUTH_TYPE_NONE -> Res.string.ssh_auth_type_none
                SSHBean.AUTH_TYPE_PASSWORD -> Res.string.password
                SSHBean.AUTH_TYPE_PRIVATE_KEY -> Res.string.ssh_public_key
                else -> error("impossible")
            }
        ListPreference(
            value = uiState.authType,
            values =
                listOf(
                    SSHBean.AUTH_TYPE_NONE,
                    SSHBean.AUTH_TYPE_PASSWORD,
                    SSHBean.AUTH_TYPE_PRIVATE_KEY,
                ),
            onValueChange = { viewModel.setAuthType(it) },
            title = { Text(stringResource(Res.string.hysteria_auth_type)) },
            icon = {
                MaskedIcon(
                    Res.drawable.compare_arrows,
                    color = PreferenceMaskColors.IconLightGreen,
                )
            },
            summary = {
                val text = stringResource(authType(uiState.authType))
                Text(text)
            },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(stringResource(authType(it))) },
        )
        AnimatedVisibility(visible = uiState.authType == SSHBean.AUTH_TYPE_PASSWORD) {
            Column {
                PreferenceDivider()
                PasswordPreference(
                    value = uiState.password,
                    onValueChange = { viewModel.setPassword(it) },
                )
            }
        }
        AnimatedVisibility(visible = uiState.authType == SSHBean.AUTH_TYPE_PRIVATE_KEY) {
            Column {
                PreferenceDivider()
                TextFieldPreference(
                    value = uiState.privateKey,
                    onValueChange = { viewModel.setPrivateKey(it) },
                    title = { Text(stringResource(Res.string.ssh_private_key)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.vpn_key,
                            color = PreferenceMaskColors.IconCyan,
                            shape = PreferenceShapes.credential(),
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.privateKey)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                PasswordPreference(
                    value = uiState.privateKeyPassphrase,
                    onValueChange = { viewModel.setPrivateKeyPassphrase(it) },
                    title = { Text(stringResource(Res.string.ssh_private_key_passphrase)) },
                )
            }
        }
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.publicKey,
            onValueChange = { viewModel.setPublicKey(it) },
            title = { Text(stringResource(Res.string.ssh_public_key)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.copyright,
                    color = PreferenceMaskColors.IconWarmGray,
                )
            },
            summary = { Text(contentOrUnset(uiState.publicKey)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
}
