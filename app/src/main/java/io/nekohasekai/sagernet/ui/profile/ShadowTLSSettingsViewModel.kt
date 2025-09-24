package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.shadowtls.ShadowTLSBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

internal class ShadowTLSSettingsViewModel : ProfileSettingsViewModel<ShadowTLSBean>() {
    override fun createBean() = ShadowTLSBean().applyDefaultValues()

    override fun ShadowTLSBean.writeToTempDatabase() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.protocolVersion = protocolVersion
        DataStore.serverPassword = password
        DataStore.serverSNI = sni
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverCertPublicKeySha256 = certPublicKeySha256
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverUtlsFingerPrint = utlsFingerprint
    }

    override fun ShadowTLSBean.loadFromTempDatabase() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        protocolVersion = DataStore.protocolVersion
        password = DataStore.serverPassword
        sni = DataStore.serverSNI
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        certPublicKeySha256 = DataStore.serverCertPublicKeySha256
        allowInsecure = DataStore.serverAllowInsecure
        utlsFingerprint = DataStore.serverUtlsFingerPrint
    }
}