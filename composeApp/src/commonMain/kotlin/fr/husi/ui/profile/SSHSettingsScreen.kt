package fr.husi.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.UIntegerTextField
import fr.husi.fmt.ssh.SSHBean
import fr.husi.ktx.contentOrUnset
import fr.husi.repository.repo
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
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SSHSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: SSHSettingsViewModel = viewModel { SSHSettingsViewModel() }

    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
    ) { scope, uiState, _ ->
        scope.sshSettings(uiState as SshUiState, viewModel)
    }
}

private fun LazyListScope.sshSettings(
    uiState: SshUiState,
    viewModel: SSHSettingsViewModel,
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
            textToValue = { it.toIntOrNull() ?: 22 },
            icon = { Icon(vectorResource(Res.drawable.directions_boat), null) },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }
    item("username") {
        TextFieldPreference(
            value = uiState.username,
            onValueChange = { viewModel.setUsername(it) },
            title = { Text(stringResource(Res.string.username)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.person), null) },
            summary = { Text(contentOrUnset(uiState.username)) },
            valueToText = { it },
        )
    }
    item("auth_type") {
        fun authType(type: Int) = when (type) {
            SSHBean.AUTH_TYPE_NONE -> Res.string.ssh_auth_type_none
            SSHBean.AUTH_TYPE_PASSWORD -> Res.string.password
            SSHBean.AUTH_TYPE_PRIVATE_KEY -> Res.string.ssh_public_key
            else -> error("impossible")
        }
        ListPreference(
            value = uiState.authType,
            values = listOf(
                SSHBean.AUTH_TYPE_NONE,
                SSHBean.AUTH_TYPE_PASSWORD,
                SSHBean.AUTH_TYPE_PRIVATE_KEY,
            ),
            onValueChange = { viewModel.setAuthType(it) },
            title = { Text(stringResource(Res.string.hysteria_auth_type)) },
            icon = { Icon(vectorResource(Res.drawable.compare_arrows), null) },
            summary = {
                val text = stringResource(authType(uiState.authType))
                Text(text)
            },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                val text = runBlocking { repo.getString(authType(it)) }
                AnnotatedString(text)
            },
        )
    }

    item("auth_fields") {
        AnimatedVisibility(visible = uiState.authType == SSHBean.AUTH_TYPE_PASSWORD) {
            PasswordPreference(
                value = uiState.password,
                onValueChange = { viewModel.setPassword(it) },
            )
        }
        AnimatedVisibility(visible = uiState.authType == SSHBean.AUTH_TYPE_PRIVATE_KEY) {
            Column {
                TextFieldPreference(
                    value = uiState.privateKey,
                    onValueChange = { viewModel.setPrivateKey(it) },
                    title = { Text(stringResource(Res.string.ssh_private_key)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.vpn_key), null) },
                    summary = { Text(contentOrUnset(uiState.privateKey)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PasswordPreference(
                    value = uiState.privateKeyPassphrase,
                    onValueChange = { viewModel.setPrivateKeyPassphrase(it) },
                    title = { Text(stringResource(Res.string.ssh_private_key_passphrase)) },
                )
            }
        }
    }

    item("public_key") {
        TextFieldPreference(
            value = uiState.publicKey,
            onValueChange = { viewModel.setPublicKey(it) },
            title = { Text(stringResource(Res.string.ssh_public_key)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.copyright), null) },
            summary = { Text(contentOrUnset(uiState.publicKey)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
}

