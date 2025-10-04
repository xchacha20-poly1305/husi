package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.MuxStrategy
import io.nekohasekai.sagernet.MuxType
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class VMessUiState(
    override val name: String = "",
    override val address: String = "127.0.0.1",
    override val port: Int = 443,

    override val v2rayTransport: String = "",
    override val host: String = "",
    override val path: String = "",
    override val headers: String = "",
    override val wsMaxEarlyData: Int = 0,
    override val wsEarlyDataHeaderName: String = "",

    override val security: String = "",
    override val sni: String = "",
    override val alpn: String = "",
    override val certificate: String = "",
    override val certPublicKeySha256: String = "",
    override val allowInsecure: Boolean = false,
    override val disableSNI: Boolean = false,
    override val tlsFragment: Boolean = false,
    override val tlsFragmentFallbackDelay: String = "",
    override val tlsRecordFragment: Boolean = false,
    override val utlsFingerprint: String = "",
    override val realityPublicKey: String = "",
    override val realityShortID: String = "",
    override val ech: Boolean = false,
    override val echConfig: String = "",

    override val enableMux: Boolean = false,
    override val brutal: Boolean = false,
    override val muxType: Int = MuxType.H2MUX,
    override val muxStrategy: Int = MuxStrategy.MAX_CONNECTIONS,
    override val muxNumber: Int = 0,
    override val muxPadding: Boolean = false,

    override val customConfig: String = "",
    override val customOutbound: String = "",

    val uuid: String = "",
    val alterID: Int = 0,
    val encryption: String = "",
    val flow: String = "",
    val packetEncoding: Int = 0,
    val authenticatedLength: Boolean = false,
) : StandardV2RayUiState

internal class VMessSettingsViewModel(val isVLESS: Boolean) :
    StandardV2RaySettingsViewModel<VMessBean>() {

    override fun createBean() = VMessBean().also {
        if (isVLESS) {
            it.alterId = -1
        }
        it.initializeDefaultValues()
    }

    private val _uiState = MutableStateFlow(VMessUiState())
    override val uiState = _uiState.asStateFlow()

    override fun VMessBean.writeToUiState() {
        _uiState.update {
            it.copy(
                name = name,
                address = serverAddress,
                port = serverPort,

                v2rayTransport = v2rayTransport,
                host = host,
                path = path,
                headers = headers,
                wsMaxEarlyData = wsMaxEarlyData,
                wsEarlyDataHeaderName = earlyDataHeaderName,

                security = security,
                sni = sni,
                alpn = alpn,
                certificate = certificates,
                certPublicKeySha256 = certPublicKeySha256,
                allowInsecure = allowInsecure,
                disableSNI = disableSNI,
                tlsFragment = fragment,
                tlsFragmentFallbackDelay = fragmentFallbackDelay,
                tlsRecordFragment = recordFragment,
                utlsFingerprint = utlsFingerprint,
                realityPublicKey = realityPublicKey,
                realityShortID = realityShortID,
                ech = ech,
                echConfig = echConfig,

                enableMux = serverMux,
                brutal = serverBrutal,
                muxType = serverMuxType,
                muxStrategy = serverMuxStrategy,
                muxNumber = serverMuxNumber,
                muxPadding = serverMuxPadding,

                customConfig = customConfigJson,
                customOutbound = customOutboundJson,

                uuid = uuid,
                alterID = alterId,
                encryption = if (isVLESS) {
                    ""
                } else {
                    encryption
                },
                flow = if (isVLESS) {
                    encryption
                } else {
                    ""
                },
                packetEncoding = packetEncoding,
                authenticatedLength = authenticatedLength,
            )
        }
    }

    override fun VMessBean.loadFromUiState() {
        val state = _uiState.value

        name = state.name
        serverAddress = state.address
        serverPort = state.port

        v2rayTransport = state.v2rayTransport
        host = state.host
        path = state.path
        headers = state.headers
        wsMaxEarlyData = state.wsMaxEarlyData
        earlyDataHeaderName = state.wsEarlyDataHeaderName

        security = state.security
        sni = state.sni
        alpn = state.alpn
        certificates = state.certificate
        certPublicKeySha256 = state.certPublicKeySha256
        allowInsecure = state.allowInsecure
        disableSNI = state.disableSNI
        fragment = state.tlsFragment
        fragmentFallbackDelay = state.tlsFragmentFallbackDelay
        recordFragment = state.tlsRecordFragment
        utlsFingerprint = state.utlsFingerprint
        realityPublicKey = state.realityPublicKey
        realityShortID = state.realityShortID
        ech = state.ech
        echConfig = state.echConfig

        serverMux = state.enableMux
        serverBrutal = state.brutal
        serverMuxType = state.muxType
        serverMuxStrategy = state.muxStrategy
        serverMuxNumber = state.muxNumber
        serverMuxPadding = state.muxPadding

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound

        uuid = state.uuid
        alterId = state.alterID
        encryption = if (isVLESS) {
            state.flow
        } else {
            state.encryption
        }
        packetEncoding = state.packetEncoding
        authenticatedLength = state.authenticatedLength
    }

    override fun setCustomConfig(config: String) {
        _uiState.update { it.copy(customConfig = config) }
    }

    override fun setCustomOutbound(outbound: String) {
        _uiState.update { it.copy(customOutbound = outbound) }
    }

    override fun setName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    override fun setAddress(address: String) {
        _uiState.update { it.copy(address = address) }
    }

    override fun setPort(port: Int) {
        _uiState.update { it.copy(port = port) }
    }

    override fun setTransport(transport: String) {
        _uiState.update { it.copy(v2rayTransport = transport) }
    }

    override fun setHost(host: String) {
        _uiState.update { it.copy(host = host) }
    }

    override fun setPath(path: String) {
        _uiState.update { it.copy(path = path) }
    }

    override fun setHeaders(headers: String) {
        _uiState.update { it.copy(headers = headers) }
    }

    override fun setWsMaxEarlyData(maxEarlyData: Int) {
        _uiState.update { it.copy(wsMaxEarlyData = maxEarlyData) }
    }

    override fun setWsEarlyDataHeaderName(headerName: String) {
        _uiState.update { it.copy(wsEarlyDataHeaderName = headerName) }
    }

    override fun setSecurity(security: String) {
        _uiState.update { it.copy(security = security) }
    }

    override fun setSni(sni: String) {
        _uiState.update { it.copy(sni = sni) }
    }

    override fun setAlpn(alpn: String) {
        _uiState.update { it.copy(alpn = alpn) }
    }

    override fun setCertificate(certificate: String) {
        _uiState.update { it.copy(certificate = certificate) }
    }

    override fun setCertPublicKeySha256(sha256: String) {
        _uiState.update { it.copy(certPublicKeySha256 = sha256) }
    }

    override fun setAllowInsecure(allow: Boolean) {
        _uiState.update { it.copy(allowInsecure = allow) }
    }

    override fun setDisableSNI(disable: Boolean) {
        _uiState.update { it.copy(disableSNI = disable) }
    }

    override fun setTlsFragment(enable: Boolean) {
        _uiState.update { it.copy(tlsFragment = enable) }
    }

    override fun setTlsFragmentFallbackDelay(delay: String) {
        _uiState.update { it.copy(tlsFragmentFallbackDelay = delay) }
    }

    override fun setTlsRecordFragment(enable: Boolean) {
        _uiState.update { it.copy(tlsRecordFragment = enable) }
    }

    override fun setUtlsFingerprint(fingerprint: String) {
        _uiState.update { it.copy(utlsFingerprint = fingerprint) }
    }

    override fun setRealityPublicKey(publicKey: String) {
        _uiState.update { it.copy(realityPublicKey = publicKey) }
    }

    override fun setRealityShortID(shortID: String) {
        _uiState.update { it.copy(realityShortID = shortID) }
    }

    override fun setEch(enable: Boolean) {
        _uiState.update { it.copy(ech = enable) }
    }

    override fun setEchConfig(config: String) {
        _uiState.update { it.copy(echConfig = config) }
    }

    override fun setEnableMux(enable: Boolean) {
        _uiState.update { it.copy(enableMux = enable) }
    }

    override fun setBrutal(enable: Boolean) {
        _uiState.update { it.copy(brutal = enable) }
    }

    override fun setMuxType(type: Int) {
        _uiState.update { it.copy(muxType = type) }
    }

    override fun setMuxStrategy(strategy: Int) {
        _uiState.update { it.copy(muxStrategy = strategy) }
    }

    override fun setMuxNumber(number: Int) {
        _uiState.update { it.copy(muxNumber = number) }
    }

    override fun setMuxPadding(enable: Boolean) {
        _uiState.update { it.copy(muxPadding = enable) }
    }

    fun setUUID(uuid: String) {
        _uiState.update { it.copy(uuid = uuid) }
    }

    fun setAlterID(alertID: Int) {
        _uiState.update { it.copy(alterID = alertID) }
    }

    fun setEncryption(encryption: String) {
        _uiState.update { it.copy(encryption = encryption) }
    }

    fun setFlow(flow: String) {
        _uiState.update { it.copy(flow = flow) }
    }

    fun setPacketEncoding(packetEncoding: Int) {
        _uiState.update { it.copy(packetEncoding = packetEncoding) }
    }

    fun setAuthenticatedLength(enabled: Boolean) {
        _uiState.update { it.copy(authenticatedLength = enabled) }
    }

}