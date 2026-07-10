package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.code
import fr.husi.resources.experimental_settings
import fr.husi.resources.grid_on
import fr.husi.resources.http_headers
import fr.husi.resources.http_host
import fr.husi.resources.http_path
import fr.husi.resources.language
import fr.husi.resources.password
import fr.husi.resources.password_opt
import fr.husi.resources.person
import fr.husi.resources.profile_config
import fr.husi.resources.route
import fr.husi.resources.udp_over_tcp
import fr.husi.resources.username_opt
import fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HttpSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: HttpSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        HttpSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, scrollTo ->
        httpSettings(uiState as HttpUiState, viewModel, scrollTo)
    }
}


private fun LazyListScope.httpSettings(
    uiState: HttpUiState,
    viewModel: HttpSettingsViewModel,
    scrollTo: (String) -> Unit,
) {
    headSettings(uiState, viewModel)
    preferenceGroup {
        TextFieldPreference(
            value = uiState.username,
            onValueChange = { viewModel.setUsername(it) },
            title = { Text(stringResource(Res.string.username_opt)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.person, color = IconMaskColors.IconCyan)
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
                MaskedIcon(
                    Res.drawable.password,
                    color = IconMaskColors.IconWarmGray,
                )
            },
        )
    }
    preferenceGroup {
        TextFieldPreference(
            value = uiState.host,
            onValueChange = { viewModel.setHost(it) },
            title = { Text(stringResource(Res.string.http_host)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.language,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(uiState.host)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.path,
            onValueChange = { viewModel.setPath(it) },
            title = { Text(stringResource(Res.string.http_path)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.route,
                    color = IconMaskColors.IconLightOrange,
                )
            },
            summary = { Text(contentOrUnset(uiState.path)) },
            valueToText = { it },
        )
        PreferenceDivider()
        TextFieldPreference(
            value = uiState.headers,
            onValueChange = { viewModel.setHeaders(it) },
            title = { Text(stringResource(Res.string.http_headers)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.code, color = IconMaskColors.IconLavender)
            },
            summary = { Text(contentOrUnset(uiState.headers)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }

    tlsSettings(uiState, viewModel, scrollTo)

    item("category_experimental") {
        PreferenceCategory(
            text = { Text(stringResource(Res.string.experimental_settings)) },
        )
    }
    preferenceGroup {
        SwitchPreference(
            value = uiState.udpOverTcp,
            onValueChange = { viewModel.setUdpOverTcp(it) },
            title = { Text(stringResource(Res.string.udp_over_tcp)) },
            icon = {
                MaskedIcon(Res.drawable.grid_on, color = IconMaskColors.IconCoral)
            },
        )
    }
}
