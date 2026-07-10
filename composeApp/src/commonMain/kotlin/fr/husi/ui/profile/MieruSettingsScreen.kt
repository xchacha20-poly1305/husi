package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.PreferenceMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.resources.Res
import fr.husi.resources.compare_arrows
import fr.husi.resources.directions_boat
import fr.husi.resources.emoji_symbols
import fr.husi.resources.high
import fr.husi.resources.low
import fr.husi.resources.middle
import fr.husi.resources.mtu
import fr.husi.resources.mux_preference
import fr.husi.resources.not_set
import fr.husi.resources.off
import fr.husi.resources.pattern
import fr.husi.resources.person
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.protocol
import fr.husi.resources.proxy_cat
import fr.husi.resources.public_icon
import fr.husi.resources.router
import fr.husi.resources.server_address
import fr.husi.resources.server_port
import fr.husi.resources.traffic_pattern
import fr.husi.resources.username
import fr.husi.resources.vpn_key
import fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MieruSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: MieruSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        MieruSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        mieruSettings(uiState as MieruUiState, viewModel)
    }
}

private fun LazyListScope.mieruSettings(
    uiState: MieruUiState,
    viewModel: MieruSettingsViewModel,
) {
    val protocols = listOf("TCP", "UDP")

    preferenceGroup {
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
    preferenceGroup {
        TextFieldPreference(
            value = uiState.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.router,
                    color = PreferenceMaskColors.IconLightBlue,
                )
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
                MaskedIcon(
                    Res.drawable.directions_boat,
                    color = PreferenceMaskColors.IconLightOrange,
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
            value = uiState.protocol,
            values = protocols,
            onValueChange = { viewModel.setProtocol(it) },
            title = { Text(stringResource(Res.string.protocol)) },
            icon = {
                MaskedIcon(
                    Res.drawable.compare_arrows,
                    color = PreferenceMaskColors.IconLavender,
                )
            },
            summary = { Text(contentOrUnset(uiState.protocol.uppercase())) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.username,
            onValueChange = { viewModel.setUsername(it) },
            title = { Text(stringResource(Res.string.username)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.person, color = PreferenceMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.username)) },
            valueToText = { it },
        )
        PreferenceDivider()
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
            icon = {
                MaskedIcon(
                    Res.drawable.vpn_key,
                    color = PreferenceMaskColors.IconWarmGray,
                )
            },
        )
        if (uiState.protocol == "udp") {
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.mtu,
                onValueChange = { viewModel.setMtu(it) },
                title = { Text(stringResource(Res.string.mtu)) },
                textToValue = { it.toIntOrNull() ?: 1400 },
                icon = {
                    MaskedIcon(
                        Res.drawable.public_icon,
                        color = PreferenceMaskColors.IconLightGreen,
                    )
                },
                summary = { Text(contentOrUnset(uiState.mtu)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                },
            )
        }
        PreferenceDivider()
        ListPreference(
            value = uiState.muxNumber,
            values = intListN(4),
            onValueChange = { viewModel.setMuxNumber(it) },
            title = { Text(stringResource(Res.string.mux_preference)) },
            icon = {
                MaskedIcon(
                    Res.drawable.compare_arrows,
                    color = PreferenceMaskColors.IconLightYellow,
                )
            },
            summary = {
                val muxSummary: StringResource = when (uiState.muxNumber) {
                    0 -> Res.string.off
                    1 -> Res.string.low
                    2 -> Res.string.middle
                    3 -> Res.string.high
                    else -> Res.string.not_set
                }
                Text(stringResource(muxSummary))
            },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                val muxSummary: StringResource = when (it) {
                    0 -> Res.string.off
                    1 -> Res.string.low
                    2 -> Res.string.middle
                    3 -> Res.string.high
                    else -> Res.string.not_set
                }
                AnnotatedString(stringResource(muxSummary))
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.trafficPattern,
            onValueChange = viewModel::setTrafficPattern,
            title = { Text(stringResource(Res.string.traffic_pattern)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.pattern, color = PreferenceMaskColors.IconCoral)
            },
            summary = { Text(contentOrUnset(uiState.trafficPattern)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
}
