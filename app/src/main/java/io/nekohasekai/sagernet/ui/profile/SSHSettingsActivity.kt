package io.nekohasekai.sagernet.ui.profile

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
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
import io.nekohasekai.sagernet.compose.listPreferenceMenuItem
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class SSHSettingsActivity : ProfileSettingsActivity<SSHBean>() {

    override val viewModel by viewModels<SSHSettingsViewModel>()

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as SshUiState

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
                textToValue = { it.toIntOrNull() ?: 22 },
                icon = { Icon(ImageVector.vectorResource(R.drawable.directions_boat), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.port)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                }
            )
        }
        item("username") {
            TextFieldPreference(
                value = uiState.username,
                onValueChange = { viewModel.setUsername(it) },
                title = { Text(stringResource(R.string.username)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.person), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.username)) },
                valueToText = { it },
            )
        }
        item("auth_type") {
            fun authType(type: Int) = when (type) {
                SSHBean.AUTH_TYPE_NONE -> R.string.ssh_auth_type_none
                SSHBean.AUTH_TYPE_PASSWORD -> R.string.password
                SSHBean.AUTH_TYPE_PRIVATE_KEY -> R.string.ssh_public_key
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
                title = { Text(stringResource(R.string.hysteria_auth_type)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.compare_arrows), null) },
                summary = {
                    val text = stringResource(authType(uiState.authType))
                    Text(text)
                },
                type = ListPreferenceType.DROPDOWN_MENU,
                item = listPreferenceMenuItem {
                    AnnotatedString(getString(authType(it)))
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
                        title = { Text(stringResource(R.string.ssh_private_key)) },
                        textToValue = { it },
                        icon = { Icon(ImageVector.vectorResource(R.drawable.vpn_key), null) },
                        summary = { Text(LocalContext.current.contentOrUnset(uiState.privateKey)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                    PasswordPreference(
                        value = uiState.privateKeyPassphrase,
                        onValueChange = { viewModel.setPrivateKeyPassphrase(it) },
                        title = { Text(stringResource(R.string.ssh_private_key_passphrase)) },
                    )
                }
            }
        }

        item("public_key") {
            TextFieldPreference(
                value = uiState.publicKey,
                onValueChange = { viewModel.setPublicKey(it) },
                title = { Text(stringResource(R.string.ssh_public_key)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.copyright), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.publicKey)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
    }
}