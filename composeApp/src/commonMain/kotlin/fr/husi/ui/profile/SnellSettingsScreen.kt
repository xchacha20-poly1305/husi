package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.fmt.snell.SnellBean
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.directions_boat
import fr.husi.resources.emoji_symbols
import fr.husi.resources.enhanced_encryption
import fr.husi.resources.grid_3x3
import fr.husi.resources.http_host
import fr.husi.resources.obfs_mode
import fr.husi.resources.password
import fr.husi.resources.pre_shared_key
import fr.husi.resources.profile_config
import fr.husi.resources.profile_name
import fr.husi.resources.protocol_version
import fr.husi.resources.proxy_cat
import fr.husi.resources.router
import fr.husi.resources.security
import fr.husi.resources.server_address
import fr.husi.resources.server_port
import fr.husi.resources.settings
import fr.husi.resources.snell_mode
import fr.husi.resources.snell_reuse
import fr.husi.resources.snell_user_key
import fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnellSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: SnellSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        SnellSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        snellSettings(uiState as SnellUiState, viewModel)
    }
}

private fun LazyListScope.snellSettings(
    uiState: SnellUiState,
    viewModel: SnellSettingsViewModel,
) {
    val versions = listOf(SnellBean.VERSION_4, SnellBean.VERSION_6)
    fun versionText(version: Int) = when (version) {
        SnellBean.VERSION_4 -> "v4 (5)"
        else -> "v$version"
    }

    val obfsModes = listOf("", "http", "tls")
    val snellModes = listOf("default", "unshaped", "unsafe-raw")
    fun snellModeText(mode: String) = mode.ifBlank { "default" }

    preferenceGroup {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.emoji_symbols,
                    color = IconMaskColors.IconCyan,
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
                    color = IconMaskColors.IconLightBlue,
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
                    color = IconMaskColors.IconLightOrange,
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
            value = uiState.version,
            values = versions,
            onValueChange = { viewModel.setVersion(it) },
            title = { Text(stringResource(Res.string.protocol_version)) },
            icon = {
                MaskedIcon(
                    Res.drawable.security,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = { Text(versionText(uiState.version)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(versionText(it)) },
        )
        PreferenceDivider()
        PasswordPreference(
            value = uiState.psk,
            onValueChange = { viewModel.setPsk(it) },
            title = { Text(stringResource(Res.string.pre_shared_key)) },
            icon = {
                MaskedIcon(
                    Res.drawable.password,
                    color = IconMaskColors.IconWarmGray,
                )
            },
        )
        PreferenceDivider()
        PasswordPreference(
            value = uiState.userKey,
            onValueChange = { viewModel.setUserKey(it) },
            title = { Text(stringResource(Res.string.snell_user_key)) },
            icon = {
                MaskedIcon(
                    Res.drawable.enhanced_encryption,
                    color = IconMaskColors.IconCoral,
                )
            },
        )
    }

    item("category_options") {
        PreferenceCategory(text = { Text(stringResource(Res.string.settings)) })
    }
    preferenceGroup {
        SwitchPreference(
            value = uiState.reuse,
            onValueChange = { viewModel.setReuse(it) },
            title = { Text(stringResource(Res.string.snell_reuse)) },
            icon = {
                MaskedIcon(
                    Res.drawable.grid_3x3,
                    color = IconMaskColors.IconLightBlue,
                )
            },
        )
    }

    when (uiState.version) {
        SnellBean.VERSION_4 -> {
            preferenceGroup {
                ListPreference(
                    value = uiState.obfsMode,
                    values = obfsModes,
                    onValueChange = { viewModel.setObfsMode(it) },
                    title = { Text(stringResource(Res.string.obfs_mode)) },
                    icon = {
                        MaskedIcon(
                            Res.drawable.settings,
                            color = IconMaskColors.IconLightGreen,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.obfsMode)) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(contentOrUnset(it)) },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = uiState.obfsHost,
                    onValueChange = { viewModel.setObfsHost(it) },
                    title = { Text(stringResource(Res.string.http_host)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.router,
                            color = IconMaskColors.IconLightOrange,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.obfsHost)) },
                    valueToText = { it },
                )
            }
        }

        SnellBean.VERSION_6 -> {
            preferenceGroup {
                ListPreference(
                    value = snellModeText(uiState.mode),
                    values = snellModes,
                    onValueChange = { viewModel.setMode(it) },
                    title = { Text(stringResource(Res.string.snell_mode)) },
                    icon = {
                        MaskedIcon(
                            Res.drawable.settings,
                            color = IconMaskColors.IconLightGreen,
                        )
                    },
                    summary = { Text(snellModeText(uiState.mode)) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(it) },
                )
            }
        }
    }

}
