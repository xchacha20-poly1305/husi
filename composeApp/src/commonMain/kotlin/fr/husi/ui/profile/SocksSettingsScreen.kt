package fr.husi.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.UIntegerTextField
import fr.husi.fmt.socks.SOCKSBean
import fr.husi.ktx.contentOrUnset
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import fr.husi.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocksSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: SocksSettingsViewModel = viewModel { SocksSettingsViewModel() }
    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
    ) { scope, uiState, _ ->
        scope.socksSettings(uiState as SocksUiState, viewModel)
    }
}

private fun LazyListScope.socksSettings(
    uiState: SocksUiState,
    viewModel: SocksSettingsViewModel,
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
    item("protocol") {
        ListPreference(
            value = uiState.protocol,
            values = listOf(
                SOCKSBean.PROTOCOL_SOCKS4,
                SOCKSBean.PROTOCOL_SOCKS4A,
                SOCKSBean.PROTOCOL_SOCKS5,
            ),
            onValueChange = { viewModel.setProtocol(it) },
            title = { Text(stringResource(Res.string.protocol_version)) },
            icon = { Icon(vectorResource(Res.drawable.nfc), null) },
            summary = {
                val text = when (uiState.protocol) {
                    SOCKSBean.PROTOCOL_SOCKS4 -> "SOCKS4"
                    SOCKSBean.PROTOCOL_SOCKS4A -> "SOCKS4A"
                    else -> "SOCKS5"
                }
                Text(text)
            },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                AnnotatedString(
                    when (it) {
                        SOCKSBean.PROTOCOL_SOCKS4 -> "SOCKS4"
                        SOCKSBean.PROTOCOL_SOCKS4A -> "SOCKS4A"
                        else -> "SOCKS5"
                    },
                )
            },
        )
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
            textToValue = { it.toIntOrNull() ?: 1080 },
            icon = { Icon(vectorResource(Res.drawable.directions_boat), null) },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }

    val showAuth = uiState.protocol == SOCKSBean.PROTOCOL_SOCKS5
    item("auth") {
        androidx.compose.animation.AnimatedVisibility(visible = showAuth) {
            Column {
                TextFieldPreference(
                    value = uiState.username,
                    onValueChange = { viewModel.setUsername(it) },
                    title = { Text(stringResource(Res.string.username_opt)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.person), null) },
                    summary = { Text(contentOrUnset(uiState.username)) },
                    valueToText = { it },
                )
                PasswordPreference(
                    value = uiState.password,
                    onValueChange = { viewModel.setPassword(it) },
                    title = { Text(stringResource(Res.string.password_opt)) },
                )
            }
        }
    }

    item("category_experimental") {
        PreferenceCategory(
            icon = { Icon(vectorResource(Res.drawable.grid_3x3), null) },
            text = { Text(stringResource(Res.string.experimental_settings)) },
        )
    }
    item("udp_over_tcp") {
        SwitchPreference(
            value = uiState.udpOverTcp,
            onValueChange = { viewModel.setUdpOverTcp(it) },
            title = { Text(stringResource(Res.string.udp_over_tcp)) },
            icon = { Icon(vectorResource(Res.drawable.grid_on), null) },
        )
    }
}

