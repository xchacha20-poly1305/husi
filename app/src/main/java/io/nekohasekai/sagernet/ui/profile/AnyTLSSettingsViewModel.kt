package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.anytls.AnyTLSBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

internal class AnyTLSSettingsViewModel : ProfileSettingsViewModel<AnyTLSBean>() {
    override fun createBean() = AnyTLSBean().applyDefaultValues()

    override fun AnyTLSBean.writeToTempDatabase() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverPassword = password
        DataStore.serverIdleSessionCheckInterval = idleSessionCheckInterval
        DataStore.serverIdleSessionTimeout = idleSessionTimeout
        DataStore.serverMinIdleSession = minIdleSession
        DataStore.serverSNI = serverName
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverUtlsFingerPrint = utlsFingerprint
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverDisableSNI = disableSNI
        DataStore.serverFragment = fragment
        DataStore.serverFragmentFallbackDelay = fragmentFallbackDelay
        DataStore.serverRecordFragment = recordFragment
        DataStore.serverECH = ech
        DataStore.serverECHConfig = echConfig
    }

    override fun AnyTLSBean.loadFromTempDatabase() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        password = DataStore.serverPassword
        idleSessionCheckInterval = DataStore.serverIdleSessionCheckInterval
        idleSessionTimeout = DataStore.serverIdleSessionTimeout
        minIdleSession = DataStore.serverMinIdleSession
        serverName = DataStore.serverSNI
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        utlsFingerprint = DataStore.serverUtlsFingerPrint
        allowInsecure = DataStore.serverAllowInsecure
        disableSNI = DataStore.serverDisableSNI
        fragment = DataStore.serverFragment
        fragmentFallbackDelay = DataStore.serverFragmentFallbackDelay
        recordFragment = DataStore.serverRecordFragment
        ech = DataStore.serverECH
        echConfig = DataStore.serverECHConfig
    }

}