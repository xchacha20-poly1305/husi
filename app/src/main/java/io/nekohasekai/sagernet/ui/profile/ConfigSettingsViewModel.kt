package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.config.ConfigBean

internal class ConfigSettingsViewModel : ProfileSettingsViewModel<ConfigBean>() {

    companion object {
        private const val KEY_IS_OUTBOUND_ONLY = "is_outbound_only"
    }

    override fun createBean() = ConfigBean()


    override fun ConfigBean.writeToTempDatabase() {
        // CustomBean to input
        DataStore.profileCacheStore.putBoolean(KEY_IS_OUTBOUND_ONLY, type == ConfigBean.TYPE_OUTBOUND)
        DataStore.profileName = name
        DataStore.serverConfig = config
    }

    override fun ConfigBean.loadFromTempDatabase() {
        // CustomBean from input
        type = if (DataStore.profileCacheStore.getBoolean(KEY_IS_OUTBOUND_ONLY, false)) {
            ConfigBean.TYPE_OUTBOUND
        } else {
            ConfigBean.TYPE_CONFIG
        }
        name = DataStore.profileName
        config = DataStore.serverConfig
    }
}