package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean

internal abstract class StandardV2RaySettingsViewModel : ProfileSettingsViewModel<StandardV2RayBean>() {

    override fun StandardV2RayBean.writeToTempDatabase() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort

        // V2Ray Transport
        DataStore.serverNetwork = v2rayTransport
        DataStore.serverHost = host
        DataStore.serverPath = path
        DataStore.serverHeaders = headers
        DataStore.serverWsMaxEarlyData = wsMaxEarlyData
        DataStore.serverWsEarlyDataHeaderName = earlyDataHeaderName

        // Security
        DataStore.serverSecurity = security
        DataStore.serverSNI = sni
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverCertPublicKeySha256 = certPublicKeySha256
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverDisableSNI = disableSNI
        DataStore.serverFragment = fragment
        DataStore.serverFragmentFallbackDelay = fragmentFallbackDelay
        DataStore.serverRecordFragment = recordFragment
        DataStore.serverUtlsFingerPrint = utlsFingerprint
        DataStore.serverRealityPublicKey = realityPublicKey
        DataStore.serverRealityShortID = realityShortID
        DataStore.serverECH = ech
        DataStore.serverECHConfig = echConfig

        // Mux
        DataStore.serverMux = serverMux
        DataStore.serverBrutal = serverBrutal
        DataStore.serverMuxType = serverMuxType
        DataStore.serverMuxStrategy = serverMuxStrategy
        DataStore.serverMuxNumber = serverMuxNumber
        DataStore.serverMuxPadding = serverMuxPadding

        // Protocol Specific
        when (this) {
            is HttpBean -> {
                DataStore.serverUsername = username
                DataStore.serverPassword = password
                DataStore.udpOverTcp = udpOverTcp
            }

            is TrojanBean -> {
                DataStore.serverPassword = password
            }

            is VMessBean -> {
                DataStore.serverUserID = uuid
                DataStore.serverAlterID = alterId
                DataStore.serverEncryption = encryption
                DataStore.serverPacketEncoding = packetEncoding
                DataStore.serverAuthenticatedLength = authenticatedLength
            }
        }
    }

    override fun StandardV2RayBean.loadFromTempDatabase() {
        // Basic
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort

        // V2Ray Transport
        v2rayTransport = DataStore.serverNetwork
        host = DataStore.serverHost
        path = DataStore.serverPath
        headers = DataStore.serverHeaders
        wsMaxEarlyData = DataStore.serverWsMaxEarlyData
        earlyDataHeaderName = DataStore.serverWsEarlyDataHeaderName

        // Security
        security = DataStore.serverSecurity
        sni = DataStore.serverSNI
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        certPublicKeySha256 = DataStore.serverCertPublicKeySha256
        allowInsecure = DataStore.serverAllowInsecure
        disableSNI = DataStore.serverDisableSNI
        fragment = DataStore.serverFragment
        fragmentFallbackDelay = DataStore.serverFragmentFallbackDelay
        recordFragment = DataStore.serverRecordFragment
        utlsFingerprint = DataStore.serverUtlsFingerPrint
        realityPublicKey = DataStore.serverRealityPublicKey
        realityShortID = DataStore.serverRealityShortID
        ech = DataStore.serverECH
        echConfig = DataStore.serverECHConfig

        // Mux
        serverMux = DataStore.serverMux
        serverBrutal = DataStore.serverBrutal
        serverMuxType = DataStore.serverMuxType
        serverMuxStrategy = DataStore.serverMuxStrategy
        serverMuxNumber = DataStore.serverMuxNumber
        serverMuxPadding = DataStore.serverMuxPadding

        // Protocol Specific
        when (this) {
            is HttpBean -> {
                username = DataStore.serverUsername
                password = DataStore.serverPassword
                udpOverTcp = DataStore.udpOverTcp
            }

            is TrojanBean -> {
                password = DataStore.serverPassword
            }

            is VMessBean -> {
                uuid = DataStore.serverUserID
                alterId = DataStore.serverAlterID
                encryption = DataStore.serverEncryption
                packetEncoding = DataStore.serverPacketEncoding
                authenticatedLength = DataStore.serverAuthenticatedLength
            }
        }
    }
}