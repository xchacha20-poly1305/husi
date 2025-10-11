package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.PasswordPreference
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.compose.UIntegerTextField
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class SocksSettingsActivity : ProfileSettingsActivity<SOCKSBean>() {

    override val viewModel by viewModels<SocksSettingsViewModel>()

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as SocksUiState

        item("name") {
            TextFieldPreference(
                value = uiState.name,
                onValueChange = { viewModel.setName(it) },
                title = { Text(stringResource(R.string.profile_name)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.EmojiSymbols, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.name)) },
                valueToText = { it },
            )
        }

        item("category_proxy") {
            PreferenceCategory(text = { Text(stringResource(R.string.proxy_cat)) })
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
                title = { Text(stringResource(R.string.protocol_version)) },
                icon = { Icon(Icons.Filled.Nfc, null) },
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
                        }
                    )
                },
            )
        }
        item("address") {
            TextFieldPreference(
                value = uiState.address,
                onValueChange = { viewModel.setAddress(it) },
                title = { Text(stringResource(R.string.server_address)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Router, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.address)) },
                valueToText = { it },
            )
        }
        item("port") {
            TextFieldPreference(
                value = uiState.port,
                onValueChange = { viewModel.setPort(it) },
                title = { Text(stringResource(R.string.server_port)) },
                textToValue = { it.toIntOrNull() ?: 1080 },
                icon = { Icon(Icons.Filled.DirectionsBoat, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.port)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                }
            )
        }

        val showAuth = uiState.protocol == SOCKSBean.PROTOCOL_SOCKS5
        item("auth") {
            AnimatedVisibility(visible = showAuth) {
                Column {
                    TextFieldPreference(
                        value = uiState.username,
                        onValueChange = { viewModel.setUsername(it) },
                        title = { Text(stringResource(R.string.username_opt)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Person, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(uiState.username)) },
                        valueToText = { it },
                    )
                    PasswordPreference(
                        value = uiState.password,
                        onValueChange = { viewModel.setPassword(it) },
                        title = { Text(stringResource(R.string.password_opt)) },
                    )
                }
            }
        }

        item("category_experimental") {
            PreferenceCategory(
                icon = { Icon(Icons.Filled.Grid3x3, null) },
                text = { Text(stringResource(R.string.experimental_settings)) },
            )
        }
        item("udp_over_tcp") {
            SwitchPreference(
                value = uiState.udpOverTcp,
                onValueChange = { viewModel.setUdpOverTcp(it) },
                title = { Text(stringResource(R.string.udp_over_tcp)) },
                icon = { Icon(Icons.Filled.GridOn, null) },
            )
        }
    }
}
