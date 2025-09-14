package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

internal class HysteriaSettingsViewModel : ProfileSettingsViewModel<HysteriaBean>() {
    override fun createBean() = HysteriaBean().applyDefaultValues()

    override fun HysteriaBean.writeToTempDatabase() {
        DataStore.profileName = name
        DataStore.protocolVersion = protocolVersion
        DataStore.serverAddress = serverAddress
        DataStore.serverPorts = serverPorts
        DataStore.serverObfs = obfuscation
        DataStore.serverAuthType = authPayloadType
        DataStore.serverProtocolInt = protocol
        DataStore.serverPassword = authPayload
        DataStore.serverSNI = sni
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverDisableSNI = disableSNI
        DataStore.serverStreamReceiveWindow = streamReceiveWindow
        DataStore.serverConnectionReceiveWindow = connectionReceiveWindow
        DataStore.serverDisableMtuDiscovery = disableMtuDiscovery
        DataStore.serverHopInterval = hopInterval
        DataStore.serverMTlsCert = mtlsCert
        DataStore.serverMTlsKey = mtlsKey
        DataStore.serverECH = ech
        DataStore.serverECHConfig = echConfig
    }

    override fun HysteriaBean.loadFromTempDatabase() {
        name = DataStore.profileName
        protocolVersion = DataStore.protocolVersion
        serverAddress = DataStore.serverAddress
        serverPorts = DataStore.serverPorts
        obfuscation = DataStore.serverObfs
        authPayloadType = DataStore.serverAuthType
        authPayload = DataStore.serverPassword
        protocol = DataStore.serverProtocolInt
        sni = DataStore.serverSNI
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        allowInsecure = DataStore.serverAllowInsecure
        disableSNI = DataStore.serverDisableSNI
        streamReceiveWindow = DataStore.serverStreamReceiveWindow
        connectionReceiveWindow = DataStore.serverConnectionReceiveWindow
        disableMtuDiscovery = DataStore.serverDisableMtuDiscovery
        hopInterval = DataStore.serverHopInterval
        mtlsCert = DataStore.serverMTlsCert
        mtlsKey = DataStore.serverMTlsKey
        ech = DataStore.serverECH
        echConfig = DataStore.serverECHConfig
    }
}