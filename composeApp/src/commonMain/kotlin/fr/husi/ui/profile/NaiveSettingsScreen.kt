package fr.husi.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import fr.husi.compose.HostTextField
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.PreferenceMaskColors
import fr.husi.compose.ProfilePreferenceIcon
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.code
import fr.husi.resources.copyright
import fr.husi.resources.directions_boat
import fr.husi.resources.disable_post_quantum
import fr.husi.resources.ech
import fr.husi.resources.ech_config
import fr.husi.resources.ech_query_server_name
import fr.husi.resources.emoji_symbols
import fr.husi.resources.enable
import fr.husi.resources.experimental_settings
import fr.husi.resources.extra_headers
import fr.husi.resources.grain
import fr.husi.resources.grid_3x3
import fr.husi.resources.https
import fr.husi.resources.naive_idle_timeout
import fr.husi.resources.naive_insecure_concurrency
import fr.husi.resources.naive_insecure_concurrency_summary
import fr.husi.resources.naive_tunnel_timeout
import fr.husi.resources.nfc
import fr.husi.resources.not_set
import fr.husi.resources.password
import fr.husi.resources.password_opt
import fr.husi.resources.person
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.protocol
import fr.husi.resources.proxy_cat
import fr.husi.resources.router
import fr.husi.resources.search
import fr.husi.resources.security
import fr.husi.resources.server_address
import fr.husi.resources.server_port
import fr.husi.resources.sni
import fr.husi.resources.speed
import fr.husi.resources.timelapse
import fr.husi.resources.traffic
import fr.husi.resources.tuic_congestion_controller
import fr.husi.resources.udp_over_tcp
import fr.husi.resources.username_opt
import fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaiveSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: NaiveSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        NaiveSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        naiveSettings(uiState as NaiveUiState, viewModel)
    }
}

private fun LazyListScope.naiveSettings(
    uiState: NaiveUiState,
    viewModel: NaiveSettingsViewModel,
) {
    val protos = listOf("https", "quic")
    val quicCongestionControls = listOf(
        "",
        "bbr",
        "bbr2",
        "cubic",
        "reno",
    )

    preferenceGroup(key = "name") {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = {
                ProfilePreferenceIcon(
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
                ProfilePreferenceIcon(Res.drawable.router, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.address)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.port,
            onValueChange = { viewModel.setPort(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull() ?: 443 },
            icon = {
                ProfilePreferenceIcon(
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
        TextFieldPreference(
            value = uiState.username,
            onValueChange = { viewModel.setUsername(it) },
            title = { Text(stringResource(Res.string.username_opt)) },
            textToValue = { it },
            icon = {
                ProfilePreferenceIcon(Res.drawable.person, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.username)) },
            valueToText = { it },
        )
        PreferenceDivider()
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
            title = { Text(stringResource(Res.string.password_opt)) },
            icon = {
                ProfilePreferenceIcon(Res.drawable.password, color = PreferenceMaskColors.IconCyan)
            },
        )
        PreferenceDivider()
        ListPreference(
            value = uiState.proto,
            values = protos,
            onValueChange = { viewModel.setProto(it) },
            title = { Text(stringResource(Res.string.protocol)) },
            icon = {
                ProfilePreferenceIcon(Res.drawable.https, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.proto)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
        PreferenceDivider()
        ListPreference(
            value = uiState.quicCongestionControl,
            values = quicCongestionControls,
            onValueChange = { viewModel.setQuicCongestionControl(it) },
            title = { Text(stringResource(Res.string.tuic_congestion_controller)) },
            enabled = uiState.proto == "quic",
            icon = {
                ProfilePreferenceIcon(Res.drawable.traffic, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.quicCongestionControl)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.sni,
            onValueChange = { viewModel.setSni(it) },
            title = { Text(stringResource(Res.string.sni)) },
            textToValue = { it },
            icon = {
                ProfilePreferenceIcon(Res.drawable.copyright, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.sni)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.extraHeaders,
            onValueChange = { viewModel.setExtraHeaders(it) },
            title = { Text(stringResource(Res.string.extra_headers)) },
            textToValue = { it },
            icon = {
                ProfilePreferenceIcon(Res.drawable.code, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.extraHeaders)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                HostTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.insecureConcurrency,
            onValueChange = { viewModel.setInsecureConcurrency(it) },
            title = { Text(stringResource(Res.string.naive_insecure_concurrency)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                ProfilePreferenceIcon(Res.drawable.speed, color = PreferenceMaskColors.IconCyan)
            },
            summary = {
                val text = if (uiState.insecureConcurrency == 0) {
                    stringResource(Res.string.not_set)
                } else {
                    uiState.insecureConcurrency.toString()
                }
                Text(text)
            },
            textField = { value, onValueChange, onOk ->
                Column {
                    Text(
                        text = stringResource(Res.string.naive_insecure_concurrency_summary),
                        modifier = Modifier.padding(16.dp),
                    )

                    UIntegerTextField(value, onValueChange, onOk)
                }
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.tunnelTimeout,
            onValueChange = { viewModel.setTunnelTimeout(it) },
            title = { Text(stringResource(Res.string.naive_tunnel_timeout)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                ProfilePreferenceIcon(Res.drawable.timelapse, color = PreferenceMaskColors.IconCyan)
            },
            summary = {
                val text = if (uiState.tunnelTimeout == 0) {
                    stringResource(Res.string.not_set)
                } else {
                    uiState.tunnelTimeout.toString()
                }
                Text(text)
            },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.idleTimeout,
            onValueChange = { viewModel.setIdleTimeout(it) },
            title = { Text(stringResource(Res.string.naive_idle_timeout)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                ProfilePreferenceIcon(Res.drawable.timelapse, color = PreferenceMaskColors.IconCyan)
            },
            summary = {
                val text = if (uiState.idleTimeout == 0) {
                    stringResource(Res.string.not_set)
                } else {
                    uiState.idleTimeout.toString()
                }
                Text(text)
            },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }

    item("category_experimental") {
        PreferenceCategory(
            icon = {
                ProfilePreferenceIcon(Res.drawable.grid_3x3, color = PreferenceMaskColors.IconCyan)
            },
            text = { Text(stringResource(Res.string.experimental_settings)) },
        )
    }
    preferenceGroup(key = "udp_over_tcp") {
        SwitchPreference(
            value = uiState.udpOverTcp,
            onValueChange = { viewModel.setUdpOverTcp(it) },
            title = { Text(stringResource(Res.string.udp_over_tcp)) },
            icon = { Spacer(Modifier.size(24.dp)) },
        )
        PreferenceDivider()
        SwitchPreference(
            value = uiState.noPostQuantum,
            onValueChange = { viewModel.setNoPostQuantum(it) },
            title = { Text(stringResource(Res.string.disable_post_quantum)) },
            icon = {
                ProfilePreferenceIcon(Res.drawable.grain, color = PreferenceMaskColors.IconCyan)
            },
        )
    }

    item("category_ech") {
        PreferenceCategory(text = { Text(stringResource(Res.string.ech)) })
    }
    preferenceGroup(key = "ech") {
        SwitchPreference(
            value = uiState.enableEch,
            onValueChange = { viewModel.setEnableEch(it) },
            title = { Text(stringResource(Res.string.enable)) },
            icon = {
                ProfilePreferenceIcon(Res.drawable.security, color = PreferenceMaskColors.IconCyan)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.echConfig,
            onValueChange = { viewModel.setEchConfig(it) },
            title = { Text(stringResource(Res.string.ech_config)) },
            textToValue = { it },
            icon = {
                ProfilePreferenceIcon(Res.drawable.nfc, color = PreferenceMaskColors.IconCyan)
            },
            enabled = uiState.enableEch,
            summary = { Text(contentOrUnset(uiState.echConfig)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.echQueryServerName,
            onValueChange = { viewModel.setEchQueryServerName(it) },
            title = { Text(stringResource(Res.string.ech_query_server_name)) },
            textToValue = { it },
            icon = {
                ProfilePreferenceIcon(Res.drawable.search, color = PreferenceMaskColors.IconCyan)
            },
            enabled = uiState.enableEch,
            summary = { Text(contentOrUnset(uiState.echQueryServerName)) },
            valueToText = { it },
        )
    }
}
