package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

class TrojanSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createEntity() = TrojanBean().applyDefaultValues()

}
