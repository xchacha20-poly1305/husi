package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.UIntegerTextField
import fr.husi.fmt.shadowquic.ShadowQUICBean
import fr.husi.ktx.contentOrUnset
import fr.husi.repository.repo
import fr.husi.resources.Res
import fr.husi.resources.action_shadowquic
import fr.husi.resources.action_sunnyquic
import fr.husi.resources.alpn
import fr.husi.resources.brightness_4
import fr.husi.resources.compare_arrows
import fr.husi.resources.copyright
import fr.husi.resources.developer_board
import fr.husi.resources.directions_boat
import fr.husi.resources.emoji_symbols
import fr.husi.resources.extra_paths
import fr.husi.resources.extra_paths_max
import fr.husi.resources.flight_takeoff
import fr.husi.resources.grid_on
import fr.husi.resources.gso
import fr.husi.resources.initial_mtu
import fr.husi.resources.minimum_mtu
import fr.husi.resources.mtu_discovery
import fr.husi.resources.multiple_stop
import fr.husi.resources.nat
import fr.husi.resources.persistent_keepalive_interval
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.protocol
import fr.husi.resources.proxy_cat
import fr.husi.resources.public_icon
import fr.husi.resources.router
import fr.husi.resources.search
import fr.husi.resources.segment
import fr.husi.resources.server_address
import fr.husi.resources.server_port
import fr.husi.resources.sni
import fr.husi.resources.texture
import fr.husi.resources.timer
import fr.husi.resources.toc
import fr.husi.resources.tuic_congestion_controller
import fr.husi.resources.tuic_reduce_rtt
import fr.husi.resources.udp_over_stream
import fr.husi.resources.username
import fr.husi.resources.wb_sunny
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowQUICSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: ShadowQUICSettingsViewModel = viewModel { ShadowQUICSettingsViewModel() }

    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
    ) { scope, uiState, _ ->
        scope.shadowQuicSettings(uiState as ShadowQUICUiState, viewModel)
    }
}

private fun LazyListScope.shadowQuicSettings(
    uiState: ShadowQUICUiState,
    viewModel: ShadowQUICSettingsViewModel,
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
    item("sub_protocol") {
        fun subProtocolText(subProtocol: Int) = when (subProtocol) {
            ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC -> Res.string.action_shadowquic
            ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC -> Res.string.action_sunnyquic
            else -> error("impossible")
        }
        ListPreference(
            value = uiState.subProtocol,
            values = listOf(
                ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC,
                ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC,
            ),
            onValueChange = { viewModel.setSubProtocol(it) },
            title = { Text(stringResource(Res.string.protocol)) },
            icon = {
                Icon(
                    vectorResource(
                        if (uiState.subProtocol == ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC) {
                            Res.drawable.brightness_4
                        } else {
                            Res.drawable.wb_sunny
                        },
                    ),
                    null,
                )
            },
            summary = { Text(stringResource(subProtocolText(uiState.subProtocol))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                val text = runBlocking { repo.getString(subProtocolText(it)) }
                AnnotatedString(text)
            },
        )
    }
    item("username") {
        TextFieldPreference(
            value = uiState.username,
            onValueChange = { viewModel.setUsername(it) },
            title = { Text(stringResource(Res.string.username)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.texture), null) },
            summary = { Text(contentOrUnset(uiState.username)) },
            valueToText = { it },
        )
    }
    item("password") {
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
        )
    }
    item("alpn") {
        TextFieldPreference(
            value = uiState.alpn,
            onValueChange = { viewModel.setAlpn(it) },
            title = { Text(stringResource(Res.string.alpn)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.toc), null) },
            summary = { Text(contentOrUnset(uiState.alpn)) },
            valueToText = { it },
        )
    }
    item("congestion_control") {
        ListPreference(
            value = uiState.congestionControl,
            values = congestionControls,
            onValueChange = { viewModel.setCongestionControl(it) },
            title = { Text(stringResource(Res.string.tuic_congestion_controller)) },
            icon = { Icon(vectorResource(Res.drawable.compare_arrows), null) },
            summary = { Text(uiState.congestionControl) },
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
    item("zero_rtt") {
        SwitchPreference(
            value = uiState.zeroRTT,
            onValueChange = { viewModel.setZeroRTT(it) },
            title = { Text(stringResource(Res.string.tuic_reduce_rtt)) },
            icon = { Icon(vectorResource(Res.drawable.flight_takeoff), null) },
        )
    }
    item("initial_mtu") {
        TextFieldPreference(
            value = uiState.initialMtu,
            onValueChange = { viewModel.setInitialMtu(it) },
            title = { Text(stringResource(Res.string.initial_mtu)) },
            textToValue = { it.toIntOrNull() ?: 1300 },
            icon = { Icon(vectorResource(Res.drawable.public_icon), null) },
            summary = { Text(contentOrUnset(uiState.initialMtu)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }
    item("minimum_mtu") {
        TextFieldPreference(
            value = uiState.minMtu,
            onValueChange = { viewModel.setMinMtu(it) },
            title = { Text(stringResource(Res.string.minimum_mtu)) },
            textToValue = { it.toIntOrNull() ?: 1290 },
            icon = { Icon(vectorResource(Res.drawable.developer_board), null) },
            summary = { Text(contentOrUnset(uiState.minMtu)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }
    item("udp_over_stream") {
        SwitchPreference(
            value = uiState.udpOverStream,
            onValueChange = { viewModel.setUdpOverStream(it) },
            title = { Text(stringResource(Res.string.udp_over_stream)) },
            icon = { Icon(vectorResource(Res.drawable.nat), null) },
        )
    }
    item("gso") {
        SwitchPreference(
            value = uiState.gso,
            onValueChange = { viewModel.setGso(it) },
            title = { Text(stringResource(Res.string.gso)) },
            icon = { Icon(vectorResource(Res.drawable.segment), null) },
        )
    }
    item("keep_alive_interval") {
        TextFieldPreference(
            value = uiState.keepAliveInterval,
            onValueChange = { viewModel.setKeepAliveInterval(it) },
            title = { Text(stringResource(Res.string.persistent_keepalive_interval)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = { Icon(vectorResource(Res.drawable.timer), null) },
            summary = { Text(contentOrUnset(uiState.keepAliveInterval)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }
    item("mtu_discovery") {
        SwitchPreference(
            value = uiState.mtuDiscovery,
            onValueChange = { viewModel.setMtuDiscovery(it) },
            title = { Text(stringResource(Res.string.mtu_discovery)) },
            icon = { Icon(vectorResource(Res.drawable.search), null) },
        )
    }

    if (uiState.subProtocol == ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC) {
        item("extra_paths") {
            TextFieldPreference(
                value = uiState.extraPaths,
                onValueChange = { viewModel.setExtraPaths(it) },
                title = { Text(stringResource(Res.string.extra_paths)) },
                textToValue = { it },
                icon = { Icon(vectorResource(Res.drawable.grid_on), null) },
                summary = { Text(contentOrUnset(uiState.extraPaths)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
        item("max_paths") {
            val maxPathsFloat = uiState.maxPaths.toFloat()
            val currentPathCount = uiState.extraPaths.lines().count { it.isNotBlank() }
            var previewValue by remember { mutableFloatStateOf(maxPathsFloat) }
            SliderPreference(
                value = maxPathsFloat,
                onValueChange = { viewModel.setMaxPaths(it.roundToInt()) },
                sliderValue = previewValue,
                onSliderValueChange = { previewValue = it },
                title = { Text(stringResource(Res.string.extra_paths_max)) },
                valueRange = 0f..currentPathCount.toFloat(),
                valueSteps = currentPathCount.coerceAtLeast(1) - 1,
                enabled = currentPathCount > 0,
                icon = { Icon(vectorResource(Res.drawable.multiple_stop), null) },
                summary = { Text(contentOrUnset(uiState.maxPaths)) },
                valueText = { Text(previewValue.roundToInt().toString()) },
            )
        }
    }
}

