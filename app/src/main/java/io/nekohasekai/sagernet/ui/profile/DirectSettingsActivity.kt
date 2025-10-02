package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.fmt.direct.DirectBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class DirectSettingsActivity : ProfileSettingsActivity<DirectBean>() {

    override val viewModel by viewModels<DirectSettingsViewModel>()

    override fun LazyListScope.settings(state: ProfileSettingsUiState) {
        state as DirectUiState

        item("name") {
            TextFieldPreference(
                value = state.name,
                onValueChange = { viewModel.setName(it) },
                title = { Text(stringResource(R.string.profile_name)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.EmojiSymbols, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.name)) },
                valueToText = { it },
            )
        }
    }
}
