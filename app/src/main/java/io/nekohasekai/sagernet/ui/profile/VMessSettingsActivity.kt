package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.compose.UIntegerTextField
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

    companion object {
        const val EXTRA_VLESS = "vless"
    }

    override val viewModel by viewModels<VMessSettingsViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val isVLESS = intent?.getBooleanExtra(EXTRA_VLESS, false) == true
                return VMessSettingsViewModel(isVLESS) as T
            }
        }
    }

    override fun LazyListScope.settings(state: ProfileSettingsUiState) {
        state as VMessUiState

        basicSettings(state)
        item("uuid") {
            TextFieldPreference(
                value = state.uuid,
                onValueChange = { viewModel.setUUID(it) },
                title = { Text(stringResource(R.string.uuid)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Person, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.uuid)) },
                valueToText = { it },
            )
        }
        if (!viewModel.isVLESS) {
            item("alter_id") {
                TextFieldPreference(
                    value = state.alterID,
                    onValueChange = { viewModel.setAlterID(it) },
                    title = { Text(stringResource(R.string.alter_id)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = { Icon(Icons.Filled.AlternateEmail, null) },
                    summary = { Text(LocalContext.current.contentOrUnset(state.alterID)) },
                    valueToText = { it.toString() },
                    textField = { value, onValueChange, onOk ->
                        UIntegerTextField(value, onValueChange, onOk)
                    },
                )
            }
            item("encryption") {
                ListPreference(
                    value = state.encryption,
                    onValueChange = { viewModel.setEncryption(it) },
                    values = listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero"),
                    title = { Text(stringResource(R.string.encryption)) },
                    icon = { Icon(Icons.Filled.EnhancedEncryption, null) },
                    summary = { Text(LocalContext.current.contentOrUnset(state.encryption)) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(it) },
                )
            }
        } else {
            item("flow") {
                ListPreference(
                    value = state.flow,
                    onValueChange = { viewModel.setFlow(it) },
                    values = listOf("", "rprx-xtls-vision"),
                    title = { Text(stringResource(R.string.xtls_flow)) },
                    icon = { Icon(Icons.Filled.Stream, null) },
                    summary = { Text(LocalContext.current.contentOrUnset(state.flow)) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(it) },
                )
            }
        }
        item("packet_encoding") {
            fun packetEncodingName(packetEncoding: Int) = when (packetEncoding) {
                0 -> StringOrRes.Res(androidx.preference.R.string.not_set)
                1 -> StringOrRes.Direct("packetaddr")
                2 -> StringOrRes.Direct("XUDP")
                else -> error("impossible")
            }
            ListPreference(
                value = state.packetEncoding,
                onValueChange = { viewModel.setPacketEncoding(it) },
                values = intListN(3),
                title = { Text(stringResource(R.string.packet_encoding)) },
                icon = { Icon(Icons.Filled.Stream, null) },
                summary = { Text(LocalContext.current.getStringOrRes(packetEncodingName(state.packetEncoding))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(getStringOrRes(packetEncodingName(it))) },
            )
        }

        transportSettings(state)
        muxSettings(state)
        tlsSettings(state)

        if (!viewModel.isVLESS) {
            item("category_experimental") {
                PreferenceCategory(
                    text = { Text(stringResource(R.string.experimental_settings)) },
                    icon = { Icon(Icons.Filled.Grid3x3, null) },
                )
            }
            item("authenticated_length") {
                SwitchPreference(
                    value = state.authenticatedLength,
                    onValueChange = { viewModel.setAuthenticatedLength(it) },
                    title = { Text(stringResource(R.string.authenticated_length)) },
                    icon = { Spacer(Modifier.size(24.dp)) },
                    summary = { Text(stringResource(R.string.experimental_authenticated_length)) },
                )
            }
        }
    }

}