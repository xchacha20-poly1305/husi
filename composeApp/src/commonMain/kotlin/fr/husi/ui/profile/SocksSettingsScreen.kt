package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.PreferenceMaskColors
import fr.husi.compose.ProfilePreferenceIcon
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.fmt.socks.SOCKSBean
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.directions_boat
import fr.husi.resources.emoji_symbols
import fr.husi.resources.experimental_settings
import fr.husi.resources.grid_3x3
import fr.husi.resources.grid_on
import fr.husi.resources.nfc
import fr.husi.resources.password
import fr.husi.resources.password_opt
import fr.husi.resources.person
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.protocol_version
import fr.husi.resources.proxy_cat
import fr.husi.resources.router
import fr.husi.resources.server_address
import fr.husi.resources.server_port
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
fun SocksSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: SocksSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        SocksSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        socksSettings(uiState as SocksUiState, viewModel)
    }
}

private fun LazyListScope.socksSettings(
    uiState: SocksUiState,
    viewModel: SocksSettingsViewModel,
) {
    preferenceGroup {
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
    val showAuth = uiState.protocol == SOCKSBean.PROTOCOL_SOCKS5
    preferenceGroup {
        ListPreference(
            value = uiState.protocol,
            values = listOf(
                SOCKSBean.PROTOCOL_SOCKS4,
                SOCKSBean.PROTOCOL_SOCKS4A,
                SOCKSBean.PROTOCOL_SOCKS5,
            ),
            onValueChange = { viewModel.setProtocol(it) },
            title = { Text(stringResource(Res.string.protocol_version)) },
            icon = {
                ProfilePreferenceIcon(Res.drawable.nfc, color = PreferenceMaskColors.IconLightBlue)
            },
            summary = {
                val text = when (uiState.protocol) {
                    SOCKSBean.PROTOCOL_SOCKS4 -> "SOCKS4"
                    SOCKSBean.PROTOCOL_SOCKS4A -> "SOCKS4A"
                    else -> "SOCKS5"
                }
                Text(text)
            },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                AnnotatedString(
                    when (it) {
                        SOCKSBean.PROTOCOL_SOCKS4 -> "SOCKS4"
                        SOCKSBean.PROTOCOL_SOCKS4A -> "SOCKS4A"
                        else -> "SOCKS5"
                    },
                )
            },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = {
                ProfilePreferenceIcon(
                    Res.drawable.router,
                    color = PreferenceMaskColors.IconLightOrange,
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
            textToValue = { it.toIntOrNull() ?: 1080 },
            icon = {
                ProfilePreferenceIcon(
                    Res.drawable.directions_boat,
                    color = PreferenceMaskColors.IconLavender,
                )
            },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        if (showAuth) {
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.username,
                onValueChange = { viewModel.setUsername(it) },
                title = { Text(stringResource(Res.string.username_opt)) },
                textToValue = { it },
                icon = {
                    ProfilePreferenceIcon(
                        Res.drawable.person,
                        color = PreferenceMaskColors.IconCyan,
                    )
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
                    ProfilePreferenceIcon(
                        Res.drawable.password,
                        color = PreferenceMaskColors.IconWarmGray,
                    )
                },
            )
        }
    }

    item("category_experimental") {
        PreferenceCategory(
            icon = {
                ProfilePreferenceIcon(
                    Res.drawable.grid_3x3,
                    color = PreferenceMaskColors.IconLightBlue,
                )
            },
            text = { Text(stringResource(Res.string.experimental_settings)) },
        )
    }
    preferenceGroup {
        SwitchPreference(
            value = uiState.udpOverTcp,
            onValueChange = { viewModel.setUdpOverTcp(it) },
            title = { Text(stringResource(Res.string.udp_over_tcp)) },
            icon = {
                ProfilePreferenceIcon(Res.drawable.grid_on, color = PreferenceMaskColors.IconCoral)
            },
        )
    }
}
