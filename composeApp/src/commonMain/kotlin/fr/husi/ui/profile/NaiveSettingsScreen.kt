package fr.husi.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import fr.husi.compose.HostTextField
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.UIntegerTextField
import fr.husi.ktx.contentOrUnset
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import fr.husi.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaiveSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: NaiveSettingsViewModel = viewModel { NaiveSettingsViewModel() }
    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
    ) { scope, uiState, _ ->
        scope.naiveSettings(uiState as NaiveUiState, viewModel)
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
            textToValue = { it.toIntOrNull() ?: 443 },
            icon = { Icon(vectorResource(Res.drawable.directions_boat), null) },
            summary = { Text(contentOrUnset(uiState.port)) },
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
            title = { Text(stringResource(Res.string.username_opt)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.person), null) },
            summary = { Text(contentOrUnset(uiState.username)) },
            valueToText = { it },
        )
    }
    item("password") {
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
            title = { Text(stringResource(Res.string.password_opt)) },
            icon = { Icon(vectorResource(Res.drawable.password), null) },
        )
    }
    item("proto") {
        ListPreference(
            value = uiState.proto,
            values = protos,
            onValueChange = { viewModel.setProto(it) },
            title = { Text(stringResource(Res.string.protocol)) },
            icon = { Icon(vectorResource(Res.drawable.https), null) },
            summary = { Text(contentOrUnset(uiState.proto)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }
    item("quic_congestion_control") {
        ListPreference(
            value = uiState.quicCongestionControl,
            values = quicCongestionControls,
            onValueChange = { viewModel.setQuicCongestionControl(it) },
            title = { Text(stringResource(Res.string.tuic_congestion_controller)) },
            enabled = uiState.proto == "quic",
            icon = { Icon(vectorResource(Res.drawable.traffic), null) },
            summary = { Text(contentOrUnset(uiState.quicCongestionControl)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }
    item("sni") {
        TextFieldPreference(
            value = uiState.sni,
            onValueChange = { viewModel.setSni(it) },
            title = { Text(stringResource(Res.string.sni)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.copyright), null) },
            summary = { Text(contentOrUnset(uiState.sni)) },
            valueToText = { it },
        )
    }
    item("extra_headers") {
        TextFieldPreference(
            value = uiState.extraHeaders,
            onValueChange = { viewModel.setExtraHeaders(it) },
            title = { Text(stringResource(Res.string.extra_headers)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.code), null) },
            summary = { Text(contentOrUnset(uiState.extraHeaders)) },
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
            title = { Text(stringResource(Res.string.naive_insecure_concurrency)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = { Icon(vectorResource(Res.drawable.speed), null) },
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
            icon = { Spacer(Modifier.size(24.dp)) },
        )
    }
    item("no_post_quantum") {
        SwitchPreference(
            value = uiState.noPostQuantum,
            onValueChange = { viewModel.setNoPostQuantum(it) },
            title = { Text(stringResource(Res.string.disable_post_quantum)) },
            icon = { Icon(vectorResource(Res.drawable.grain), null) },
        )
    }

    item("category_ech") {
        PreferenceCategory(text = { Text(stringResource(Res.string.ech)) })
    }
    item("ech") {
        SwitchPreference(
            value = uiState.enableEch,
            onValueChange = { viewModel.setEnableEch(it) },
            title = { Text(stringResource(Res.string.enable)) },
            icon = { Icon(vectorResource(Res.drawable.security), null) },
        )
    }
    item("ech_config") {
        TextFieldPreference(
            value = uiState.echConfig,
            onValueChange = { viewModel.setEchConfig(it) },
            title = { Text(stringResource(Res.string.ech_config)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.nfc), null) },
            enabled = uiState.enableEch,
            summary = { Text(contentOrUnset(uiState.echConfig)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
    item("ech_query_server_name") {
        TextFieldPreference(
            value = uiState.echQueryServerName,
            onValueChange = { viewModel.setEchQueryServerName(it) },
            title = { Text(stringResource(Res.string.ech_query_server_name)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.search), null) },
            enabled = uiState.enableEch,
            summary = { Text(contentOrUnset(uiState.echQueryServerName)) },
            valueToText = { it },
        )
    }
}

