package fr.husi.ui.profile

import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
import fr.husi.compose.PasswordPreference
import fr.husi.fmt.trojan.TrojanBean

class TrojanSettingsActivity : StandardV2RaySettingsActivity<TrojanBean>() {

    override val viewModel by viewModels<TrojanSettingsViewModel>()

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as TrojanUiState

        headSettings(uiState)
        item("password") {
            PasswordPreference(
                value = uiState.password,
                onValueChange = { viewModel.setPassword(it) },
            )
        }
        transportSettings(uiState)
        muxSettings(uiState)
        tlsSettings(uiState, scrollTo)
    }

}
