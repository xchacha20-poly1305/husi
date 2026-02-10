package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.MultilineTextField
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.resources.Res
import fr.husi.resources.encrypted
import fr.husi.resources.encryption
import fr.husi.resources.not_set
import fr.husi.resources.outbox
import fr.husi.resources.packet_encoding
import fr.husi.resources.person
import fr.husi.resources.profile_config
import fr.husi.resources.stream
import fr.husi.resources.uuid
import fr.husi.resources.xtls_flow
import fr.husi.ui.StringOrRes
import fr.husi.ui.getStringOrRes
import fr.husi.ui.stringOrRes
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VLESSSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: VLESSSettingsViewModel = viewModel { VLESSSettingsViewModel() }

    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
    ) { scope, uiState, scrollTo ->
        scope.vlessSettings(uiState as VLESSUiState, viewModel, scrollTo)
    }
}


private fun LazyListScope.vlessSettings(
    uiState: VLESSUiState,
    viewModel: VLESSSettingsViewModel,
    scrollTo: (String) -> Unit,
) {
    headSettings(uiState, viewModel)
    item("uuid") {
        TextFieldPreference(
            value = uiState.uuid,
            onValueChange = { viewModel.setUUID(it) },
            title = { Text(stringResource(Res.string.uuid)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.person), null) },
            summary = { Text(contentOrUnset(uiState.uuid)) },
            valueToText = { it },
        )
    }
    item("flow") {
        ListPreference(
            value = uiState.flow,
            onValueChange = { viewModel.setFlow(it) },
            values = listOf("", "xtls-rprx-vision"),
            title = { Text(stringResource(Res.string.xtls_flow)) },
            icon = { Icon(vectorResource(Res.drawable.stream), null) },
            summary = { Text(contentOrUnset(uiState.flow)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
    }
    item("encryption") {
        TextFieldPreference(
            value = uiState.encryption,
            onValueChange = { viewModel.setEncryption(it) },
            title = { Text(stringResource(Res.string.encryption)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.encrypted), null) },
            summary = { Text(contentOrUnset(uiState.encryption)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
    item("packet_encoding") {
        fun packetEncodingName(packetEncoding: Int): StringOrRes = when (packetEncoding) {
            0 -> StringOrRes.Res(Res.string.not_set)
            1 -> StringOrRes.Direct("packetaddr")
            2 -> StringOrRes.Direct("XUDP")
            else -> error("impossible")
        }
        ListPreference(
            value = uiState.packetEncoding,
            onValueChange = { viewModel.setPacketEncoding(it) },
            values = intListN(3),
            title = { Text(stringResource(Res.string.packet_encoding)) },
            icon = { Icon(vectorResource(Res.drawable.outbox), null) },
            summary = { Text(stringOrRes(packetEncodingName(uiState.packetEncoding))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                val text = runBlocking { getStringOrRes(packetEncodingName(it)) }
                AnnotatedString(text)
            },
        )
    }

    transportSettings(uiState, viewModel)
    muxSettings(uiState, viewModel)
    tlsSettings(uiState, viewModel, scrollTo)
}
