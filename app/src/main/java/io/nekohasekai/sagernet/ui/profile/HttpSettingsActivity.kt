package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels

class HttpSettingsActivity : StandardV2RaySettingsActivity() {

    override val viewModel by viewModels<HttpSettingsViewModel>()

}
