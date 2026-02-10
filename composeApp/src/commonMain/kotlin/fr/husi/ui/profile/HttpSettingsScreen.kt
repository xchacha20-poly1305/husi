package fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PreferenceCategory
import fr.husi.ktx.contentOrUnset
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import fr.husi.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HttpSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
) {
    val viewModel: HttpSettingsViewModel = viewModel { HttpSettingsViewModel() }

    LaunchedEffect(profileId, isSubscription) {
        viewModel.initialize(profileId, isSubscription)
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
    ) { scope, uiState, scrollTo ->
        scope.httpSettings(uiState as HttpUiState, viewModel, scrollTo)
    }
}


private fun LazyListScope.httpSettings(
    uiState: HttpUiState,
    viewModel: HttpSettingsViewModel,
    scrollTo: (String) -> Unit,
) {
    headSettings(uiState, viewModel)
    item("username") {
        TextFieldPreference(
            value = uiState.username,
            onValueChange = { viewModel.setUsername(it) },
            title = { Text(stringResource(Res.string.username_opt)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.person), null) },
            summary = { Text(contentOrUnset(uiState.username)) },
            valueToText = { it },
        )
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
            title = { Text(stringResource(Res.string.password_opt)) },
        )
    }
    item("host") {
        TextFieldPreference(
            value = uiState.host,
            onValueChange = { viewModel.setHost(it) },
            title = { Text(stringResource(Res.string.http_host)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.language), null) },
            summary = { Text(contentOrUnset(uiState.host)) },
            valueToText = { it },
        )
    }
    item("path") {
        TextFieldPreference(
            value = uiState.path,
            onValueChange = { viewModel.setPath(it) },
            title = { Text(stringResource(Res.string.http_path)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.route), null) },
            summary = { Text(contentOrUnset(uiState.path)) },
            valueToText = { it },
        )
    }
    item("headers") {
        TextFieldPreference(
            value = uiState.headers,
            onValueChange = { viewModel.setHeaders(it) },
            title = { Text(stringResource(Res.string.http_headers)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.code), null) },
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
            icon = { Icon(vectorResource(Res.drawable.grid_3x3), null) },
            text = { Text(stringResource(Res.string.experimental_settings)) },
        )
    }
    item("udp_over_tcp") {
        SwitchPreference(
            value = uiState.udpOverTcp,
            onValueChange = { viewModel.setUdpOverTcp(it) },
            title = { Text(stringResource(Res.string.udp_over_tcp)) },
            icon = { Icon(vectorResource(Res.drawable.grid_on), null) },
        )
    }
}

