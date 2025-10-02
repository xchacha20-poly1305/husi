package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
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
import io.nekohasekai.sagernet.fmt.mieru.MieruBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import io.nekohasekai.sagernet.ktx.intListN
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class MieruSettingsActivity : ProfileSettingsActivity<MieruBean>() {

    override val viewModel by viewModels<MieruSettingsViewModel>()

    private val protocols = listOf("tcp", "udp")

    override fun LazyListScope.settings(state: ProfileSettingsUiState) {
        state as MieruUiState

        item("name") {
            TextFieldPreference(
                value = state.name,
                onValueChange = { viewModel.setName(it) },
                title = { Text(stringResource(R.string.profile_name)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.EmojiSymbols, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.name)) },
                valueToText = { it },
            )
        }

        item("category_proxy") {
            PreferenceCategory(text = { Text(stringResource(R.string.proxy_cat)) })
        }
        item("address") {
            TextFieldPreference(
                value = state.address,
                onValueChange = { viewModel.setAddress(it) },
                title = { Text(stringResource(R.string.server_address)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Router, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.address)) },
                valueToText = { it },
            )
        }
        item("port") {
            TextFieldPreference(
                value = state.port,
                onValueChange = { viewModel.setPort(it) },
                title = { Text(stringResource(R.string.server_port)) },
                textToValue = { it.toIntOrNull() ?: 443 },
                icon = { Icon(Icons.Filled.DirectionsBoat, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.port)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                }
            )
        }
        item("protocol") {
            ListPreference(
                value = state.protocol,
                values = protocols,
                onValueChange = { viewModel.setProtocol(it) },
                title = { Text(stringResource(R.string.protocol)) },
                icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.protocol)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
        }
        item("username") {
            TextFieldPreference(
                value = state.username,
                onValueChange = { viewModel.setUsername(it) },
                title = { Text(stringResource(R.string.username)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Person, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.username)) },
                valueToText = { it },
            )
        }
        item("password") {
            PasswordPreference(
                value = state.password,
                onValueChange = { viewModel.setPassword(it) },
            )
        }
        if (state.protocol == "udp") {
            item("mtu") {
                TextFieldPreference(
                    value = state.mtu,
                    onValueChange = { viewModel.setMtu(it) },
                    title = { Text(stringResource(R.string.mtu)) },
                    textToValue = { it.toIntOrNull() ?: 1400 },
                    icon = { Icon(Icons.Filled.Public, null) },
                    summary = { Text(LocalContext.current.contentOrUnset(state.mtu)) },
                    valueToText = { it.toString() },
                    textField = { value, onValueChange, onOk ->
                        UIntegerTextField(value, onValueChange, onOk)
                    }
                )
            }
        }
        item("mux_number") {
            ListPreference(
                value = state.muxNumber,
                values = intListN(4),
                onValueChange = { viewModel.setMuxNumber(it) },
                title = { Text(stringResource(R.string.mux_preference)) },
                icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, null) },
                summary = {
                    val text = when (state.muxNumber) {
                        0 -> stringResource(R.string.off)
                        1 -> stringResource(R.string.low)
                        2 -> stringResource(R.string.middle)
                        3 -> stringResource(R.string.high)
                        else -> stringResource(androidx.preference.R.string.not_set)
                    }
                    Text(text)
                },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = {
                    AnnotatedString(
                        when (it) {
                            0 -> getString(R.string.off)
                            1 -> getString(R.string.low)
                            2 -> getString(R.string.middle)
                            3 -> getString(R.string.high)
                            else -> ""
                        }
                    )
                },
            )
        }
    }
}

