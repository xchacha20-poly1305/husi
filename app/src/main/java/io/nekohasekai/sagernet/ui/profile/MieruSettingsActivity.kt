package io.nekohasekai.sagernet.ui.profile

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
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
import io.nekohasekai.sagernet.compose.ListPreferenceMenuItem
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

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as MieruUiState

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
                textToValue = { it.toIntOrNull() ?: 443 },
                icon = { Icon(ImageVector.vectorResource(R.drawable.directions_boat), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.port)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                },
            )
        }
        item("protocol") {
            ListPreference(
                value = uiState.protocol,
                values = protocols,
                onValueChange = { viewModel.setProtocol(it) },
                title = { Text(stringResource(R.string.protocol)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.compare_arrows), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.protocol.uppercase())) },
                type = ListPreferenceType.DROPDOWN_MENU,
                item = ListPreferenceMenuItem { AnnotatedString(it) },
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
        item("password") {
            PasswordPreference(
                value = uiState.password,
                onValueChange = { viewModel.setPassword(it) },
            )
        }
        if (uiState.protocol == "udp") {
            item("mtu") {
                TextFieldPreference(
                    value = uiState.mtu,
                    onValueChange = { viewModel.setMtu(it) },
                    title = { Text(stringResource(R.string.mtu)) },
                    textToValue = { it.toIntOrNull() ?: 1400 },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.public_icon), null) },
                    summary = { Text(LocalContext.current.contentOrUnset(uiState.mtu)) },
                    valueToText = { it.toString() },
                    textField = { value, onValueChange, onOk ->
                        UIntegerTextField(value, onValueChange, onOk)
                    },
                )
            }
        }
        item("mux_number") {
            ListPreference(
                value = uiState.muxNumber,
                values = intListN(4),
                onValueChange = { viewModel.setMuxNumber(it) },
                title = { Text(stringResource(R.string.mux_preference)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.compare_arrows), null) },
                summary = {
                    val text = when (uiState.muxNumber) {
                        0 -> stringResource(R.string.off)
                        1 -> stringResource(R.string.low)
                        2 -> stringResource(R.string.middle)
                        3 -> stringResource(R.string.high)
                        else -> stringResource(R.string.not_set)
                    }
                    Text(text)
                },
                type = ListPreferenceType.DROPDOWN_MENU,
                item = ListPreferenceMenuItem {
                    AnnotatedString(
                        when (it) {
                            0 -> getString(R.string.off)
                            1 -> getString(R.string.low)
                            2 -> getString(R.string.middle)
                            3 -> getString(R.string.high)
                            else -> ""
                        },
                    )
                },
            )
        }
    }
}

