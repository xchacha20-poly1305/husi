package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.PasswordPreference
import io.nekohasekai.sagernet.compose.HostTextField
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.compose.UIntegerTextField
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class NaiveSettingsActivity : ProfileSettingsActivity<NaiveBean>() {

    override val viewModel by viewModels<NaiveSettingsViewModel>()

    private val protos = listOf("https", "quic")

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as NaiveUiState

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
                textToValue = { it.toIntOrNull() ?: 443 },
                icon = { Icon(Icons.Filled.DirectionsBoat, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.port)) },
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
                title = { Text(stringResource(R.string.username_opt)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Person, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.username)) },
                valueToText = { it },
            )
        }
        item("password") {
            PasswordPreference(
                value = uiState.password,
                onValueChange = { viewModel.setPassword(it) },
                title = { Text(stringResource(R.string.password_opt)) },
                icon = { Icon(Icons.Filled.Password, null) },
            )
        }
        item("proto") {
            ListPreference(
                value = uiState.proto,
                values = protos,
                onValueChange = { viewModel.setProto(it) },
                title = { Text(stringResource(R.string.protocol)) },
                icon = { Icon(Icons.Filled.Https, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.proto)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
        }
        item("sni") {
            TextFieldPreference(
                value = uiState.sni,
                onValueChange = { viewModel.setSni(it) },
                title = { Text(stringResource(R.string.sni)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Copyright, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.sni)) },
                valueToText = { it },
            )
        }
        item("extra_headers") {
            TextFieldPreference(
                value = uiState.extraHeaders,
                onValueChange = { viewModel.setExtraHeaders(it) },
                title = { Text(stringResource(R.string.extra_headers)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Code, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.extraHeaders)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    HostTextField(value, onValueChange, onOk)
                },
            )
        }
        item("insecure_concurrency") {
            TextFieldPreference(
                value = uiState.insecureConcurrency,
                onValueChange = { viewModel.setInsecureConcurrency(it) },
                title = { Text(stringResource(R.string.naive_insecure_concurrency)) },
                textToValue = { it.toIntOrNull() ?: 0 },
                icon = { Icon(Icons.Filled.Speed, null) },
                summary = {
                    val text = if (uiState.insecureConcurrency == 0) {
                        stringResource(androidx.preference.R.string.not_set)
                    } else {
                        uiState.insecureConcurrency.toString()
                    }
                    Text(text)
                },
                textField = { value, onValueChange, onOk ->
                    Column {
                        Text(
                            text = stringResource(R.string.naive_insecure_concurrency_summary),
                            modifier = Modifier.padding(16.dp)
                        )

                        UIntegerTextField(value, onValueChange, onOk)
                    }
                },
            )
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
                icon = { Spacer(Modifier.size(24.dp)) },
            )
        }
        item("no_post_quantum") {
            SwitchPreference(
                value = uiState.noPostQuantum,
                onValueChange = { viewModel.setNoPostQuantum(it) },
                title = { Text(stringResource(R.string.disable_post_quantum)) },
                icon = { Icon(Icons.Filled.Grain, null) },
            )
        }
    }
}
