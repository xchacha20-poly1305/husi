package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.PasswordPreference
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

class HttpSettingsActivity : StandardV2RaySettingsActivity<HttpBean>() {

    override val viewModel by viewModels<HttpSettingsViewModel>()

    override fun LazyListScope.settings(state: ProfileSettingsUiState) {
        state as HttpUiState

        basicSettings(state)
        item("username") {
            TextFieldPreference(
                value = state.username,
                onValueChange = { viewModel.setUsername(it) },
                title = { Text(stringResource(R.string.username_opt)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Person, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.username)) },
                valueToText = { it },
            )
            PasswordPreference(
                value = state.password,
                onValueChange = { viewModel.setPassword(it) },
                title = { Text(stringResource(R.string.password_opt)) },
            )
        }
        item("host") {
            TextFieldPreference(
                value = state.host,
                onValueChange = { viewModel.setHost(it) },
                title = { Text(stringResource(R.string.http_host)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Language, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.host)) },
                valueToText = { it },
            )
        }
        item("path") {
            TextFieldPreference(
                value = state.path,
                onValueChange = { viewModel.setPath(it) },
                title = { Text(stringResource(R.string.http_path)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Route, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.path)) },
                valueToText = { it },
            )
        }
        item("headers") {
            TextFieldPreference(
                value = state.headers,
                onValueChange = { viewModel.setHeaders(it) },
                title = { Text(stringResource(R.string.http_headers)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.Code, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.headers)) },
                valueToText = { it },
            )
        }

        tlsSettings(state)

        item("category_experimental") {
            PreferenceCategory(
                icon = { Icon(Icons.Filled.Grid3x3, null) },
                text = { Text(stringResource(R.string.experimental_settings)) },
            )
        }
        item("udp_over_tcp") {
            SwitchPreference(
                value = state.udpOverTcp,
                onValueChange = { viewModel.setUdpOverTcp(it) },
                title = { Text(stringResource(R.string.udp_over_tcp)) },
                icon = { Icon(Icons.Filled.GridOn, null) },
            )
        }
    }

}
