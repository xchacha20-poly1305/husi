package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.fmt.v2ray.VMessBean

internal class VMessSettingsViewModel(private val isVLESS: Boolean) : StandardV2RaySettingsViewModel(){

    override fun createBean() = VMessBean().also {
        if (isVLESS) {
            it.alterId = -1
        }
        it.initializeDefaultValues()
    }
}