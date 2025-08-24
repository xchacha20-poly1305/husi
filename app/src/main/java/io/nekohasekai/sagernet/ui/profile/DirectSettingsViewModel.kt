package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.direct.DirectBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

internal class DirectSettingsViewModel : ProfileSettingsViewModel<DirectBean>() {
    override fun createBean() = DirectBean().applyDefaultValues()

    override fun DirectBean.writeToTempDatabase() {
        DataStore.profileName = name
    }

    override fun DirectBean.loadFromTempDatabase() {
        name = DataStore.profileName
    }
}