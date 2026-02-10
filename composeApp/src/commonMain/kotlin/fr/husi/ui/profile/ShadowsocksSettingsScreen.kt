package fr.husi.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.UIntegerTextField
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.repository.repo
import fr.husi.resources.Res
import fr.husi.resources.bolt
import fr.husi.resources.border_inner
import fr.husi.resources.build
import fr.husi.resources.directions_boat
import fr.husi.resources.emoji_symbols
import fr.husi.resources.enable_brutal
import fr.husi.resources.enable_mux
import fr.husi.resources.enc_method
import fr.husi.resources.enhanced_encryption
import fr.husi.resources.experimental_settings
import fr.husi.resources.grid_3x3
import fr.husi.resources.multiple_stop
import fr.husi.resources.mux_number
import fr.husi.resources.mux_preference
import fr.husi.resources.mux_strategy
import fr.husi.resources.mux_sum
import fr.husi.resources.mux_type
import fr.husi.resources.numbers
import fr.husi.resources.padding
import fr.husi.resources.plugin
import fr.husi.resources.plugin_configure
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.proxy_cat
import fr.husi.resources.router
import fr.husi.resources.server_address
import fr.husi.resources.server_port
import fr.husi.resources.settings
import fr.husi.resources.type_specimen
import fr.husi.resources.udp_over_tcp
import fr.husi.resources.view_in_ar
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowsocksSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: ShadowsocksSettingsViewModel = viewModel { ShadowsocksSettingsViewModel() }
    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
    ) { scope, uiState, scrollTo ->
        scope.shadowsocksSettings(uiState as ShadowsocksUiState, viewModel, scrollTo)
    }
}

private fun LazyListScope.shadowsocksSettings(
    uiState: ShadowsocksUiState,
    viewModel: ShadowsocksSettingsViewModel,
    scrollTo: (key: String) -> Unit,
) {
    val encryptionMethods = listOf(
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
    val keyEnableMux = "enable_mux"

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
            textToValue = { it.toIntOrNull() ?: 8388 },
            icon = { Icon(vectorResource(Res.drawable.directions_boat), null) },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }
    item("method") {
        ListPreference(
            value = uiState.method,
            values = encryptionMethods,
            onValueChange = { viewModel.setMethod(it) },
            title = { Text(stringResource(Res.string.enc_method)) },
            icon = { Icon(vectorResource(Res.drawable.enhanced_encryption), null) },
            summary = { Text(contentOrUnset(uiState.method)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }
    item("password") {
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
        )
    }

    item("category_mux") {
        PreferenceCategory(text = { Text(stringResource(Res.string.mux_preference)) })
    }
    item(keyEnableMux) {
        SwitchPreference(
            value = uiState.enableMux,
            onValueChange = {
                viewModel.setEnableMux(it)
                if (it) {
                    scrollTo(keyEnableMux)
                }
            },
            title = { Text(stringResource(Res.string.enable_mux)) },
            summary = { Text(stringResource(Res.string.mux_sum)) },
            icon = { Icon(vectorResource(Res.drawable.multiple_stop), null) },
        )
    }
    item("mux") {
        androidx.compose.animation.AnimatedVisibility(visible = uiState.enableMux) {
            Column {
                SwitchPreference(
                    value = uiState.brutal,
                    onValueChange = { viewModel.setBrutal(it) },
                    title = { Text(stringResource(Res.string.enable_brutal)) },
                    icon = { Icon(vectorResource(Res.drawable.bolt), null) },
                    enabled = uiState.enableMux,
                )
                ListPreference(
                    value = uiState.muxType,
                    values = intListN(3),
                    onValueChange = { viewModel.setMuxType(it) },
                    title = { Text(stringResource(Res.string.mux_type)) },
                    icon = { Icon(vectorResource(Res.drawable.type_specimen), null) },
                    summary = { Text(muxTypes[uiState.muxType]) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(muxTypes[it]) },
                )
                ListPreference(
                    value = uiState.muxStrategy,
                    values = intListN(3),
                    onValueChange = { viewModel.setMuxStrategy(it) },
                    title = { Text(stringResource(Res.string.mux_strategy)) },
                    icon = { Icon(vectorResource(Res.drawable.view_in_ar), null) },
                    summary = { Text(stringResource(muxStrategies[uiState.muxStrategy])) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = {
                        val text = runBlocking { repo.getString(muxStrategies[it]) }
                        AnnotatedString(text)
                    },
                    enabled = !uiState.brutal,
                )
                TextFieldPreference(
                    value = uiState.muxNumber,
                    onValueChange = { viewModel.setMuxNumber(it) },
                    title = { Text(stringResource(Res.string.mux_number)) },
                    textToValue = { it.toIntOrNull() ?: 8 },
                    icon = { Icon(vectorResource(Res.drawable.numbers), null) },
                    summary = { Text(uiState.muxNumber.toString()) },
                    valueToText = { it.toString() },
                    textField = { value, onValueChange, onOk ->
                        UIntegerTextField(value, onValueChange, onOk)
                    },
                    enabled = !uiState.brutal,
                )
                SwitchPreference(
                    value = uiState.muxPadding,
                    onValueChange = { viewModel.setMuxPadding(it) },
                    title = { Text(stringResource(Res.string.padding)) },
                    icon = { Icon(vectorResource(Res.drawable.border_inner), null) },
                )
            }
        }
    }

    item("category_plugin") {
        PreferenceCategory(text = { Text(stringResource(Res.string.plugin)) })
    }
    item("plugin_name") {
        ListPreference(
            value = uiState.pluginName,
            values = listOf("", "obfs-local", "v2ray-plugin"),
            onValueChange = { viewModel.setPluginName(it) },
            title = { Text(stringResource(Res.string.plugin)) },
            icon = { Icon(vectorResource(Res.drawable.build), null) },
            summary = { Text(contentOrUnset(uiState.pluginName)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }
    item("plugin_config") {
        TextFieldPreference(
            value = uiState.pluginConfig,
            onValueChange = { viewModel.setPluginConfig(it) },
            title = { Text(stringResource(Res.string.plugin_configure)) },
            textToValue = { it },
            enabled = uiState.pluginName.isNotBlank(),
            icon = { Icon(vectorResource(Res.drawable.settings), null) },
            summary = { Text(contentOrUnset(uiState.pluginConfig)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
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
            enabled = !uiState.enableMux,
            icon = { Spacer(Modifier.size(24.dp)) },
        )
    }
}

