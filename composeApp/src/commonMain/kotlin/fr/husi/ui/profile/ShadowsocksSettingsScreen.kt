package fr.husi.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.PreferenceMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.resources.Res
import fr.husi.resources.bolt
import fr.husi.resources.border_inner
import fr.husi.resources.build
import fr.husi.resources.directions_boat
import fr.husi.resources.edit
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
import fr.husi.results.ResultEffect
import fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowsocksSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
    onOpenSIP003Editor: (NavRoutes.SIP003Editor) -> Unit,
) {
    val viewModel: ShadowsocksSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        ShadowsocksSettingsViewModel()
    }

    val sip003ResultKey = rememberSaveable {
        val number = viewModel.editingId.takeIf { it >= 0 } ?: Random.nextLong()
        "sip003-editor-$number"
    }
    ResultEffect<String?>(resultKey = sip003ResultKey) { result ->
        if (result == null) return@ResultEffect
        viewModel.setPluginConfig(result)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, scrollTo ->
        shadowsocksSettings(
            uiState as ShadowsocksUiState,
            viewModel,
            scrollTo,
            sip003ResultKey,
            onOpenSIP003Editor,
        )
    }
}

private fun LazyListScope.shadowsocksSettings(
    uiState: ShadowsocksUiState,
    viewModel: ShadowsocksSettingsViewModel,
    scrollTo: (key: String) -> Unit,
    sip003ResultKey: String,
    onOpenSIP003Editor: (NavRoutes.SIP003Editor) -> Unit,
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

    preferenceGroup(key = "name") {
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
    }

    item("category_proxy") {
        PreferenceCategory(text = { Text(stringResource(Res.string.proxy_cat)) })
    }
    preferenceGroup(key = "address") {
        TextFieldPreference(
            value = uiState.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.router, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.address)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.port,
            onValueChange = { viewModel.setPort(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull() ?: 8388 },
            icon = {
                MaskedIcon(
                    Res.drawable.directions_boat,
                    color = PreferenceMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        ListPreference(
            value = uiState.method,
            values = encryptionMethods,
            onValueChange = { viewModel.setMethod(it) },
            title = { Text(stringResource(Res.string.enc_method)) },
            icon = {
                MaskedIcon(
                    Res.drawable.enhanced_encryption,
                    color = PreferenceMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.method)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
        PreferenceDivider()
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
        )
    }

    item("category_mux") {
        PreferenceCategory(text = { Text(stringResource(Res.string.mux_preference)) })
    }
    preferenceGroup(key = keyEnableMux) {
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
            icon = {
                MaskedIcon(
                    Res.drawable.multiple_stop,
                    color = PreferenceMaskColors.IconCyan,
                )
            },
        )
        androidx.compose.animation.AnimatedVisibility(visible = uiState.enableMux) {
            Column {
                PreferenceDivider()
                SwitchPreference(
                    value = uiState.brutal,
                    onValueChange = { viewModel.setBrutal(it) },
                    title = { Text(stringResource(Res.string.enable_brutal)) },
                    icon = {
                        MaskedIcon(
                            Res.drawable.bolt,
                            color = PreferenceMaskColors.IconCyan,
                        )
                    },
                    enabled = uiState.enableMux,
                )
                PreferenceDivider()
                ListPreference(
                    value = uiState.muxType,
                    values = intListN(3),
                    onValueChange = { viewModel.setMuxType(it) },
                    title = { Text(stringResource(Res.string.mux_type)) },
                    icon = {
                        MaskedIcon(
                            Res.drawable.type_specimen,
                            color = PreferenceMaskColors.IconCyan,
                        )
                    },
                    summary = { Text(muxTypes[uiState.muxType]) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(muxTypes[it]) },
                )
                PreferenceDivider()
                ListPreference(
                    value = uiState.muxStrategy,
                    values = intListN(3),
                    onValueChange = { viewModel.setMuxStrategy(it) },
                    title = { Text(stringResource(Res.string.mux_strategy)) },
                    icon = {
                        MaskedIcon(
                            Res.drawable.view_in_ar,
                            color = PreferenceMaskColors.IconCyan,
                        )
                    },
                    summary = { Text(stringResource(muxStrategies[uiState.muxStrategy])) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(stringResource(muxStrategies[it])) },
                    enabled = !uiState.brutal,
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = uiState.muxNumber,
                    onValueChange = { viewModel.setMuxNumber(it) },
                    title = { Text(stringResource(Res.string.mux_number)) },
                    textToValue = { it.toIntOrNull() ?: 8 },
                    icon = {
                        MaskedIcon(
                            Res.drawable.numbers,
                            color = PreferenceMaskColors.IconCyan,
                        )
                    },
                    summary = { Text(uiState.muxNumber.toString()) },
                    valueToText = { it.toString() },
                    textField = { value, onValueChange, onOk ->
                        UIntegerTextField(value, onValueChange, onOk)
                    },
                    enabled = !uiState.brutal,
                )
                PreferenceDivider()
                SwitchPreference(
                    value = uiState.muxPadding,
                    onValueChange = { viewModel.setMuxPadding(it) },
                    title = { Text(stringResource(Res.string.padding)) },
                    icon = {
                        MaskedIcon(
                            Res.drawable.border_inner,
                            color = PreferenceMaskColors.IconCyan,
                        )
                    },
                )
            }
        }
    }

    item("category_plugin") {
        PreferenceCategory(text = { Text(stringResource(Res.string.plugin)) })
    }
    preferenceGroup(key = "plugin_name") {
        ListPreference(
            value = uiState.pluginName,
            values = listOf("", "obfs-local", "v2ray-plugin"),
            onValueChange = { viewModel.setPluginName(it) },
            title = { Text(stringResource(Res.string.plugin)) },
            icon = {
                MaskedIcon(Res.drawable.build, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.pluginName)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.pluginConfig,
            onValueChange = { viewModel.setPluginConfig(it) },
            title = { Text(stringResource(Res.string.plugin_configure)) },
            textToValue = { it },
            enabled = uiState.pluginName.isNotBlank(),
            icon = {
                MaskedIcon(Res.drawable.settings, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.pluginConfig)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MultilineTextField(
                        value = value,
                        onValueChange = onValueChange,
                        onOk = onOk,
                        modifier = Modifier.weight(1f),
                    )
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.edit),
                        contentDescription = stringResource(Res.string.edit),
                        onClick = {
                            onOpenSIP003Editor(
                                NavRoutes.SIP003Editor(
                                    pluginName = uiState.pluginName,
                                    initialOpts = value.text,
                                    resultKey = sip003ResultKey,
                                ),
                            )
                        },
                    )
                }
            },
        )
    }

    item("category_experimental") {
        PreferenceCategory(
            icon = {
                MaskedIcon(Res.drawable.grid_3x3, color = PreferenceMaskColors.IconCyan)
            },
            text = { Text(stringResource(Res.string.experimental_settings)) },
        )
    }
    preferenceGroup(key = "udp_over_tcp") {
        SwitchPreference(
            value = uiState.udpOverTcp,
            onValueChange = { viewModel.setUdpOverTcp(it) },
            title = { Text(stringResource(Res.string.udp_over_tcp)) },
            enabled = !uiState.enableMux,
            icon = { Spacer(Modifier.size(24.dp)) },
        )
    }
}
