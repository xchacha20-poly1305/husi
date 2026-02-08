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
import io.nekohasekai.sagernet.fmt.shadowquic.ShadowQUICBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class ShadowQUICSettingsActivity : ProfileSettingsActivity<ShadowQUICBean>() {

    override val viewModel by viewModels<ShadowQUICSettingsViewModel>()

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as ShadowQUICUiState

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
        item("sub_protocol") {
            fun subProtocolText(subProtocol: Int) = when (subProtocol) {
                ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC -> R.string.action_shadowquic
                ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC -> R.string.action_sunnyquic
                else -> error("impossible")
            }
            ListPreference(
                value = uiState.subProtocol,
                values = listOf(
                    ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC,
                    ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC,
                ),
                onValueChange = { viewModel.setSubProtocol(it) },
                title = { Text(stringResource(R.string.protocol)) },
                icon = {
                    Icon(
                        ImageVector.vectorResource(
                            if (uiState.subProtocol == ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC) {
                                R.drawable.brightness_4
                            } else {
                                R.drawable.wb_sunny
                            },
                        ),
                        null,
                    )
                },
                summary = { Text(stringResource(subProtocolText(uiState.subProtocol))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(getString(subProtocolText(it))) },
            )
        }
        item("username") {
            TextFieldPreference(
                value = uiState.username,
                onValueChange = { viewModel.setUsername(it) },
                title = { Text(stringResource(R.string.username)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.texture), null) },
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
        item("alpn") {
            TextFieldPreference(
                value = uiState.alpn,
                onValueChange = { viewModel.setAlpn(it) },
                title = { Text(stringResource(R.string.alpn)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.toc), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.alpn)) },
                valueToText = { it },
            )
        }
        item("congestion_control") {
            ListPreference(
                value = uiState.congestionControl,
                values = congestionControls,
                onValueChange = { viewModel.setCongestionControl(it) },
                title = { Text(stringResource(R.string.tuic_congestion_controller)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.compare_arrows), null) },
                summary = { Text(uiState.congestionControl) },
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
                icon = { Icon(ImageVector.vectorResource(R.drawable.copyright), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.sni)) },
                valueToText = { it },
            )
        }
        item("zero_rtt") {
            SwitchPreference(
                value = uiState.zeroRTT,
                onValueChange = { viewModel.setZeroRTT(it) },
                title = { Text(stringResource(R.string.tuic_reduce_rtt)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.flight_takeoff), null) },
            )
        }
        item("initial_mtu") {
            TextFieldPreference(
                value = uiState.initialMtu,
                onValueChange = { viewModel.setInitialMtu(it) },
                title = { Text(stringResource(R.string.initial_mtu)) },
                textToValue = { it.toIntOrNull() ?: 1300 },
                icon = { Icon(ImageVector.vectorResource(R.drawable.public_icon), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.initialMtu)) },
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
                title = { Text(stringResource(R.string.minimum_mtu)) },
                textToValue = { it.toIntOrNull() ?: 1290 },
                icon = { Icon(ImageVector.vectorResource(R.drawable.developer_board), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.minMtu)) },
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
                title = { Text(stringResource(R.string.udp_over_stream)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.nat), null) },
            )
        }
        item("gso") {
            SwitchPreference(
                value = uiState.gso,
                onValueChange = { viewModel.setGso(it) },
                title = { Text(stringResource(R.string.gso)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.segment), null) },
            )
        }
        item("keep_alive_interval") {
            TextFieldPreference(
                value = uiState.keepAliveInterval,
                onValueChange = { viewModel.setKeepAliveInterval(it) },
                title = { Text(stringResource(R.string.persistent_keepalive_interval)) },
                textToValue = { it.toIntOrNull() ?: 0 },
                icon = { Icon(ImageVector.vectorResource(R.drawable.timer), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.keepAliveInterval)) },
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
                title = { Text(stringResource(R.string.mtu_discovery)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.search), null) },
            )
        }

        if (uiState.subProtocol == ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC) {
            item("extra_paths") {
                TextFieldPreference(
                    value = uiState.extraPaths,
                    onValueChange = { viewModel.setExtraPaths(it) },
                    title = { Text(stringResource(R.string.extra_paths)) },
                    textToValue = { it },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.grid_on), null) },
                    summary = { Text(LocalContext.current.contentOrUnset(uiState.extraPaths)) },
                    valueToText = { it },
                )
            }
            item("max_paths") {
                TextFieldPreference(
                    value = uiState.maxPaths,
                    onValueChange = { viewModel.setMaxPaths(it) },
                    title = { Text(stringResource(R.string.extra_paths_max)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    enabled = uiState.extraPaths.isNotEmpty(),
                    icon = { Icon(ImageVector.vectorResource(R.drawable.multiple_stop), null) },
                    summary = { Text(LocalContext.current.contentOrUnset(uiState.maxPaths)) },
                    valueToText = { it.toString() },
                    textField = { value, onValueChange, onOk ->
                        UIntegerTextField(value, onValueChange, onOk)
                    },
                )
            }
        }
    }
}
