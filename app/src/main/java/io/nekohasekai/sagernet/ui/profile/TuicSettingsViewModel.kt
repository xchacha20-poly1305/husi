package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

internal class TuicSettingsViewModel : ProfileSettingsViewModel<TuicBean>() {
    override fun createBean() = TuicBean().applyDefaultValues()

    override fun TuicBean.writeToTempDatabase() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = uuid
        DataStore.serverPassword = token
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverCertPublicKeySha256 = certPublicKeySha256
        DataStore.serverUDPRelayMode = udpRelayMode
        DataStore.serverCongestionController = congestionController
        DataStore.serverDisableSNI = disableSNI
        DataStore.serverSNI = sni
        DataStore.serverZeroRTT = zeroRTT
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverECH = ech
        DataStore.serverECHConfig = echConfig
    }

    override fun TuicBean.loadFromTempDatabase() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        uuid = DataStore.serverUsername
        token = DataStore.serverPassword
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        certPublicKeySha256 = DataStore.serverCertPublicKeySha256
        udpRelayMode = DataStore.serverUDPRelayMode
        congestionController = DataStore.serverCongestionController
        disableSNI = DataStore.serverDisableSNI
        sni = DataStore.serverSNI
        zeroRTT = DataStore.serverZeroRTT
        allowInsecure = DataStore.serverAllowInsecure
        ech = DataStore.serverECH
        echConfig = DataStore.serverECHConfig
    }
}