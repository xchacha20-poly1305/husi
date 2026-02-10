package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.UIntegerTextField
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.resources.Res
import fr.husi.resources.alter_id
import fr.husi.resources.alternate_email
import fr.husi.resources.authenticated_length
import fr.husi.resources.encryption
import fr.husi.resources.enhanced_encryption
import fr.husi.resources.experimental_authenticated_length
import fr.husi.resources.experimental_settings
import fr.husi.resources.grid_3x3
import fr.husi.resources.not_set
import fr.husi.resources.outbox
import fr.husi.resources.packet_encoding
import fr.husi.resources.person
import fr.husi.resources.profile_config
import fr.husi.resources.uuid
import fr.husi.ui.StringOrRes
import fr.husi.ui.getStringOrRes
import fr.husi.ui.stringOrRes
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VMessSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: VMessSettingsViewModel = viewModel { VMessSettingsViewModel() }

    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
    ) { scope, uiState, scrollTo ->
        scope.vmessSettings(uiState as VMessUiState, viewModel, scrollTo)
    }
}


private fun LazyListScope.vmessSettings(
    uiState: VMessUiState,
    viewModel: VMessSettingsViewModel,
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
    item("alter_id") {
        TextFieldPreference(
            value = uiState.alterID,
            onValueChange = { viewModel.setAlterID(it) },
            title = { Text(stringResource(Res.string.alter_id)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = { Icon(vectorResource(Res.drawable.alternate_email), null) },
            summary = { Text(contentOrUnset(uiState.alterID)) },
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
            title = { Text(stringResource(Res.string.encryption)) },
            icon = { Icon(vectorResource(Res.drawable.enhanced_encryption), null) },
            summary = { Text(contentOrUnset(uiState.encryption)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
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

    item("category_experimental") {
        PreferenceCategory(
            text = { Text(stringResource(Res.string.experimental_settings)) },
            icon = { Icon(vectorResource(Res.drawable.grid_3x3), null) },
        )
    }
    item("authenticated_length") {
        SwitchPreference(
            value = uiState.authenticatedLength,
            onValueChange = { viewModel.setAuthenticatedLength(it) },
            title = { Text(stringResource(Res.string.authenticated_length)) },
            summary = { Text(stringResource(Res.string.experimental_authenticated_length)) },
        )
    }
}
