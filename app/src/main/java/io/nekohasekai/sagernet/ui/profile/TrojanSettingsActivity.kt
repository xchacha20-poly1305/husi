package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels

class TrojanSettingsActivity : StandardV2RaySettingsActivity() {

    override val viewModel by viewModels<TrojanSettingsViewModel>()

}
