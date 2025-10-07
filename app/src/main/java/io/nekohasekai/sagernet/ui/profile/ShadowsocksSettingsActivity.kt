package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BorderInner
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.MultipleStop
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.TypeSpecimen
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.MultilineTextField
import io.nekohasekai.sagernet.compose.PasswordPreference
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.compose.UIntegerTextField
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import io.nekohasekai.sagernet.ktx.intListN
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class ShadowsocksSettingsActivity : ProfileSettingsActivity<ShadowsocksBean>() {

    override val viewModel by viewModels<ShadowsocksSettingsViewModel>()

    private val encryptionMethods = listOf(
        "2022-blake3-aes-128-gcm",
        "2022-blake3-aes-256-gcm",
        "2022-blake3-chacha20-poly1305",
        "none",
        "aes-128-gcm",
        "aes-192-gcm",
        "aes-256-gcm",
        "chacha20-ietf-poly1305",
        "xchacha20-ietf-poly1305",
        "aes-128-ctr",
        "aes-192-ctr",
        "aes-256-ctr",
        "aes-128-cfb",
        "aes-192-cfb",
        "aes-256-cfb",
        "rc4-md5",
        "chacha20-ietf",
        "xchacha20",
    )

    override fun LazyListScope.settings(state: ProfileSettingsUiState) {
        state as ShadowsocksUiState

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
                textToValue = { it.toIntOrNull() ?: 8388 },
                icon = { Icon(Icons.Filled.DirectionsBoat, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.port)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                }
            )
        }
        item("method") {
            ListPreference(
                value = state.method,
                values = encryptionMethods,
                onValueChange = { viewModel.setMethod(it) },
                title = { Text(stringResource(R.string.enc_method)) },
                icon = { Icon(Icons.Filled.EnhancedEncryption, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.method)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
        }
        item("password") {
            PasswordPreference(
                value = state.password,
                onValueChange = { viewModel.setPassword(it) },
            )
        }

        item("category_mux") {
            PreferenceCategory(text = { Text(stringResource(R.string.mux_preference)) })
        }
        item("enable_mux") {
            SwitchPreference(
                value = state.enableMux,
                onValueChange = { viewModel.setEnableMux(it) },
                title = { Text(stringResource(R.string.enable_mux)) },
                summary = { Text(stringResource(R.string.mux_sum)) },
                icon = { Icon(Icons.Filled.MultipleStop, null) },
            )
        }
        item("mux") {
            AnimatedVisibility(visible = state.enableMux) {
                Column {
                    SwitchPreference(
                        value = state.brutal,
                        onValueChange = { viewModel.setBrutal(it) },
                        title = { Text(stringResource(R.string.enable_brutal)) },
                        icon = { Icon(Icons.Filled.Bolt, null) },
                        enabled = state.enableMux,
                    )
                    ListPreference(
                        value = state.muxType,
                        values = intListN(3),
                        onValueChange = { viewModel.setMuxType(it) },
                        title = { Text(stringResource(R.string.mux_type)) },
                        icon = { Icon(Icons.Filled.TypeSpecimen, null) },
                        summary = { Text(muxTypes[state.muxType]) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(muxTypes[it]) },
                    )
                    ListPreference(
                        value = state.muxStrategy,
                        values = intListN(3),
                        onValueChange = { viewModel.setMuxStrategy(it) },
                        title = { Text(stringResource(R.string.mux_strategy)) },
                        icon = { Icon(Icons.Filled.ViewInAr, null) },
                        summary = { Text(LocalContext.current.getString(muxStrategies[state.muxStrategy])) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(getString(muxStrategies[it])) },
                        enabled = !state.brutal,
                    )
                    TextFieldPreference(
                        value = state.muxNumber,
                        onValueChange = { viewModel.setMuxNumber(it) },
                        title = { Text(stringResource(R.string.mux_number)) },
                        textToValue = { it.toIntOrNull() ?: 8 },
                        icon = { Icon(Icons.Filled.Numbers, null) },
                        summary = { Text(state.muxNumber.toString()) },
                        valueToText = { it.toString() },
                        textField = { value, onValueChange, onOk ->
                            UIntegerTextField(value, onValueChange, onOk)
                        },
                        enabled = !state.brutal,
                    )
                    SwitchPreference(
                        value = state.muxPadding,
                        onValueChange = { viewModel.setMuxPadding(it) },
                        title = { Text(stringResource(R.string.padding)) },
                        icon = { Icon(Icons.Filled.BorderInner, null) },
                    )
                }
            }
        }

        item("category_plugin") {
            PreferenceCategory(text = { Text(stringResource(R.string.plugin)) })
        }
        item("plugin_name") {
            ListPreference(
                value = state.pluginName,
                values = listOf("", "obfs-local", "v2ray-plugin"),
                onValueChange = { viewModel.setPluginName(it) },
                title = { Text(stringResource(R.string.plugin)) },
                icon = { Icon(Icons.Filled.Build, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.pluginName)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
        }
        item("plugin_config") {
            TextFieldPreference(
                value = state.pluginConfig,
                onValueChange = { viewModel.setPluginConfig(it) },
                title = { Text(stringResource(R.string.plugin_configure)) },
                textToValue = { it },
                enabled = state.pluginName.isNotBlank(),
                icon = { Icon(Icons.Filled.Settings, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.pluginConfig)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
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
                value = state.udpOverTcp,
                onValueChange = { viewModel.setUdpOverTcp(it) },
                title = { Text(stringResource(R.string.udp_over_tcp)) },
                enabled = !state.enableMux,
                icon = { Spacer(Modifier.size(24.dp)) },
            )
        }
    }
}

