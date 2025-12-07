package io.nekohasekai.sagernet.ui.profile

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.compose.UIntegerTextField
import io.nekohasekai.sagernet.compose.listPreferenceMenuItem
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import io.nekohasekai.sagernet.ktx.intListN
import io.nekohasekai.sagernet.ui.StringOrRes
import io.nekohasekai.sagernet.ui.getStringOrRes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

class VMessSettingsActivity : StandardV2RaySettingsActivity<VMessBean>() {

    override val viewModel by viewModels<VMessSettingsViewModel>()

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as VMessUiState

        headSettings(uiState)
        item("uuid") {
            TextFieldPreference(
                value = uiState.uuid,
                onValueChange = { viewModel.setUUID(it) },
                title = { Text(stringResource(R.string.uuid)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.person), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.uuid)) },
                valueToText = { it },
            )
        }
        item("alter_id") {
            TextFieldPreference(
                value = uiState.alterID,
                onValueChange = { viewModel.setAlterID(it) },
                title = { Text(stringResource(R.string.alter_id)) },
                textToValue = { it.toIntOrNull() ?: 0 },
                icon = { Icon(ImageVector.vectorResource(R.drawable.alternate_email), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.alterID)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                },
            )
        }
        item("encryption") {
            ListPreference(
                value = uiState.encryption,
                onValueChange = { viewModel.setEncryption(it) },
                values = listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero"),
                title = { Text(stringResource(R.string.encryption)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.enhanced_encryption), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.encryption)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                item = listPreferenceMenuItem { AnnotatedString(it) },
            )
        }
        item("packet_encoding") {
            fun packetEncodingName(packetEncoding: Int) = when (packetEncoding) {
                0 -> StringOrRes.Res(R.string.not_set)
                1 -> StringOrRes.Direct("packetaddr")
                2 -> StringOrRes.Direct("XUDP")
                else -> error("impossible")
            }
            ListPreference(
                value = uiState.packetEncoding,
                onValueChange = { viewModel.setPacketEncoding(it) },
                values = intListN(3),
                title = { Text(stringResource(R.string.packet_encoding)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.outbox), null) },
                summary = { Text(LocalContext.current.getStringOrRes(packetEncodingName(uiState.packetEncoding))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                item = listPreferenceMenuItem { AnnotatedString(getStringOrRes(packetEncodingName(it))) },
            )
        }

        transportSettings(uiState)
        muxSettings(uiState)
        tlsSettings(uiState, scrollTo)

        item("category_experimental") {
            PreferenceCategory(
                text = { Text(stringResource(R.string.experimental_settings)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.grid_3x3), null) },
            )
        }
        item("authenticated_length") {
            SwitchPreference(
                value = uiState.authenticatedLength,
                onValueChange = { viewModel.setAuthenticatedLength(it) },
                title = { Text(stringResource(R.string.authenticated_length)) },
                icon = { Spacer(Modifier.size(24.dp)) },
                summary = { Text(stringResource(R.string.experimental_authenticated_length)) },
            )
        }
    }

}