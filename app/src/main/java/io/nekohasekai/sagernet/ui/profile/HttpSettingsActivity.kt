package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

class HttpSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createEntity() = HttpBean().applyDefaultValues()

}
