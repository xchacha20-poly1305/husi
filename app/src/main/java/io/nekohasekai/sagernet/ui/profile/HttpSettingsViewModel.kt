package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

internal class HttpSettingsViewModel : StandardV2RaySettingsViewModel() {
    override fun createBean() = HttpBean().applyDefaultValues()
}