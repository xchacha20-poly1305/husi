package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.shadowquic.ShadowQUICBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

internal class ShadowQUICSettingsViewModel : ProfileSettingsViewModel<ShadowQUICBean>() {
    override fun createBean() = ShadowQUICBean().applyDefaultValues()

    override fun ShadowQUICBean.writeToTempDatabase() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = username
        DataStore.serverPassword = password
        DataStore.serverSNI = sni
        DataStore.serverALPN = alpn
        DataStore.serverInitialMTU = initialMTU
        DataStore.serverMinimumMTU = minimumMTU
        DataStore.serverCongestionController = congestionControl
        DataStore.serverZeroRTT = zeroRTT
        DataStore.udpOverTcp = udpOverStream
    }

    override fun ShadowQUICBean.loadFromTempDatabase() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        sni = DataStore.serverSNI
        alpn = DataStore.serverALPN
        initialMTU = DataStore.serverInitialMTU
        minimumMTU = DataStore.serverMinimumMTU
        congestionControl = DataStore.serverCongestionController
        zeroRTT = DataStore.serverZeroRTT
        udpOverStream = DataStore.udpOverTcp
    }
}