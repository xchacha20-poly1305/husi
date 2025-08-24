package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

internal class ShadowsocksSettingsViewModel : ProfileSettingsViewModel<ShadowsocksBean>(){
    override fun createBean() = ShadowsocksBean().applyDefaultValues()

    override fun ShadowsocksBean.writeToTempDatabase() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverMethod = method
        DataStore.serverPassword = password
        DataStore.serverMux = serverMux
        DataStore.serverBrutal = serverBrutal
        DataStore.serverMuxType = serverMuxType
        DataStore.serverMuxNumber = serverMuxNumber
        DataStore.serverMuxStrategy = serverMuxStrategy
        DataStore.serverMuxPadding = serverMuxPadding
        DataStore.pluginName = plugin.substringBefore(";")
        DataStore.pluginConfig = plugin.substringAfter(";")
        DataStore.udpOverTcp = udpOverTcp
    }

    override fun ShadowsocksBean.loadFromTempDatabase() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        method = DataStore.serverMethod
        password = DataStore.serverPassword
        serverMux = DataStore.serverMux
        serverBrutal = DataStore.serverBrutal
        serverMuxType = DataStore.serverMuxType
        serverMuxNumber = DataStore.serverMuxNumber
        serverMuxStrategy = DataStore.serverMuxStrategy
        serverMuxPadding = DataStore.serverMuxPadding
        udpOverTcp = DataStore.udpOverTcp

        val pluginName = DataStore.pluginName
        val pluginConfig = DataStore.pluginConfig
        plugin = if (pluginName.isNotBlank()) {
            "$pluginName;$pluginConfig"
        } else {
            ""
        }
    }
}