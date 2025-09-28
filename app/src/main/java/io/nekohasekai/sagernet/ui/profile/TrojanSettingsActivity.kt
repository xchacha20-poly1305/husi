package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListScope
import io.nekohasekai.sagernet.compose.PasswordPreference
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean

class TrojanSettingsActivity : StandardV2RaySettingsActivity<TrojanBean>() {

    override val viewModel by viewModels<TrojanSettingsViewModel>()

    override fun LazyListScope.settings(state: ProfileSettingsUiState) {
        state as TrojanUiState

        basicSettings(state)
        item("password") {
            PasswordPreference(
                value = state.password,
                onValueChange = { viewModel.setPassword(it) },
            )
        }
        transportSettings(state)
        muxSettings(state)
        tlsSettings(state)
    }

}
