package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import io.nekohasekai.sagernet.ktx.intListN
import io.nekohasekai.sagernet.ui.StringOrRes
import io.nekohasekai.sagernet.ui.getStringOrRes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.TextFieldPreference

class VLESSSettingsActivity : StandardV2RaySettingsActivity<VLESSBean>() {

    override val viewModel by viewModels<VLESSSettingsViewModel>()

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as VLESSUiState

        headSettings(uiState)
        item("uuid") {
            TextFieldPreference(
                value = uiState.uuid,
                onValueChange = { viewModel.setUUID(it) },
                title = { Text(stringResource(R.string.uuid)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Person, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.uuid)) },
                valueToText = { it },
            )
        }
        item("flow") {
            ListPreference(
                value = uiState.flow,
                onValueChange = { viewModel.setFlow(it) },
                values = listOf("", "rprx-xtls-vision"),
                title = { Text(stringResource(R.string.xtls_flow)) },
                icon = { Icon(Icons.Filled.Stream, null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.flow)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
        }
        item("packet_encoding") {
            fun packetEncodingName(packetEncoding: Int) = when (packetEncoding) {
                0 -> StringOrRes.Res(androidx.preference.R.string.not_set)
                1 -> StringOrRes.Direct("packetaddr")
                2 -> StringOrRes.Direct("XUDP")
                else -> error("impossible")
            }
            ListPreference(
                value = uiState.packetEncoding,
                onValueChange = { viewModel.setPacketEncoding(it) },
                values = intListN(3),
                title = { Text(stringResource(R.string.packet_encoding)) },
                icon = { Icon(Icons.Filled.Outbox, null) },
                summary = { Text(LocalContext.current.getStringOrRes(packetEncodingName(uiState.packetEncoding))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(getStringOrRes(packetEncodingName(it))) },
            )
        }

        transportSettings(uiState)
        muxSettings(uiState)
        tlsSettings(uiState, scrollTo)
    }
}