package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.IconMaskColors
import fr.husi.compose.ListPreference
import fr.husi.compose.MaskedIcon
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.SwitchPreference
import fr.husi.compose.TextFieldPreference
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.fmt.http.HttpBean
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.block
import fr.husi.resources.code
import fr.husi.resources.disable_version_fallback
import fr.husi.resources.http_headers
import fr.husi.resources.http_host
import fr.husi.resources.http_path
import fr.husi.resources.language
import fr.husi.resources.nfc
import fr.husi.resources.password
import fr.husi.resources.password_opt
import fr.husi.resources.person
import fr.husi.resources.profile_config
import fr.husi.resources.protocol_version
import fr.husi.resources.route
import fr.husi.resources.username_opt
import fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource

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
    val isHttp1 = uiState.httpVersion == HttpBean.HTTP_VERSION_1
    preferenceGroup {
        val httpVersions = remember(uiState.isTLS) {
            HttpBean.supportedHttpVersions(uiState.isTLS)
        }
        ListPreference(
            value = uiState.httpVersion,
            values = httpVersions,
            onValueChange = { viewModel.setHttpVersion(it) },
            title = { Text(stringResource(Res.string.protocol_version)) },
            icon = {
                MaskedIcon(Res.drawable.nfc, IconMaskColors.IconLightBlue)
            },
            summary = { Text(displayHttpVersion(uiState.httpVersion)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(displayHttpVersion(it)) },
        )
        if (isHttp1) {
            TextFieldPreference(
                value = uiState.host,
                onValueChange = { viewModel.setHost(it) },
                title = { Text(stringResource(Res.string.http_host)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.language,
                        color = IconMaskColors.IconLightBlue,
                    )
                },
                summary = { Text(contentOrUnset(uiState.host)) },
                valueToText = { it },
            )
            TextFieldPreference(
                value = uiState.path,
                onValueChange = { viewModel.setPath(it) },
                title = { Text(stringResource(Res.string.http_path)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.route,
                        color = IconMaskColors.IconLightOrange,
                    )
                },
                summary = { Text(contentOrUnset(uiState.path)) },
                valueToText = { it },
            )
        } else {
            SwitchPreference(
                value = uiState.disableVersionFallback,
                onValueChange = { viewModel.setDisableVersionFallback(it) },
                enabled = uiState.isTLS,
                title = { Text(stringResource(Res.string.disable_version_fallback)) },
                icon = {
                    MaskedIcon(Res.drawable.block, IconMaskColors.IconCoral)
                },
            )
        }
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
}

private fun displayHttpVersion(version: Int) = when (version) {
    HttpBean.HTTP_VERSION_1 -> "HTTP/1.1"
    HttpBean.HTTP_VERSION_2 -> "HTTP/2"
    HttpBean.HTTP_VERSION_3 -> "HTTP/3"
    else -> "HTTP/$version"
}
