package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

internal class TrojanSettingsViewModel : StandardV2RaySettingsViewModel() {
    override fun createBean() = TrojanBean().applyDefaultValues()
}