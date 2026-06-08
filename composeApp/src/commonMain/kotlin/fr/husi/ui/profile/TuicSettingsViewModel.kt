package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.tuic.TuicBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class TuicUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 443,
    val uuid: String = "",
    val token: String = "",
    val alpn: String = "",
    val certificates: String = "",
    val certPublicKeySha256: String = "",
    val udpRelayMode: String = "native",
    val congestionController: String = "cubic",
    val disableSNI: Boolean = false,
    val sni: String = "",
    val zeroRTT: Boolean = false,
    val allowInsecure: Boolean = false,
    val ech: Boolean = false,
    val echConfig: String = "",
    val echQueryServerName: String = "",
    val clientCert: String = "",
    val clientKey: String = "",
    val idleTimeout: String = "",
    val keepAlivePeriod: String = "",
    val streamReceiveWindow: Int = 0,
    val connectionReceiveWindow: Int = 0,
    val maxConcurrentStreams: Int = 0,
    val initialPacketSize: Int = 0,
    val disablePathMtuDiscovery: Boolean = false,
) : ProfileEditorUiState

@Stable
internal class TuicSettingsViewModel : ProfileEditorViewModel<TuicBean>() {
    override fun createBean() = TuicBean().applyDefaultValues()

    override val uiState: StateFlow<TuicUiState>
        field = MutableStateFlow(TuicUiState())

    override suspend fun TuicBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                port = serverPort,
                uuid = uuid,
                token = token,
                alpn = alpn,
                certificates = certificates,
                certPublicKeySha256 = certPublicKeySha256,
                udpRelayMode = udpRelayMode,
                congestionController = congestionController,
                disableSNI = disableSNI,
                sni = sni,
                zeroRTT = zeroRTT,
                allowInsecure = allowInsecure,
                ech = ech,
                echConfig = echConfig,
                echQueryServerName = echQueryServerName,
                clientCert = clientCert,
                clientKey = clientKey,
                idleTimeout = idleTimeout,
                keepAlivePeriod = keepAlivePeriod,
                streamReceiveWindow = streamReceiveWindow,
                connectionReceiveWindow = connectionReceiveWindow,
                maxConcurrentStreams = maxConcurrentStreams,
                initialPacketSize = initialPacketSize,
                disablePathMtuDiscovery = disablePathMtuDiscovery,
            )
        }
    }

    override fun TuicBean.loadFromUiState() {
        val state = uiState.value

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        serverAddress = state.address
        serverPort = state.port
        uuid = state.uuid
        token = state.token
        alpn = state.alpn
        certificates = state.certificates
        certPublicKeySha256 = state.certPublicKeySha256
        udpRelayMode = state.udpRelayMode
        congestionController = state.congestionController
        disableSNI = state.disableSNI
        sni = state.sni
        zeroRTT = state.zeroRTT
        allowInsecure = state.allowInsecure
        ech = state.ech
        echConfig = state.echConfig
        echQueryServerName = state.echQueryServerName
        clientCert = state.clientCert
        clientKey = state.clientKey
        idleTimeout = state.idleTimeout
        keepAlivePeriod = state.keepAlivePeriod
        streamReceiveWindow = state.streamReceiveWindow
        connectionReceiveWindow = state.connectionReceiveWindow
        maxConcurrentStreams = state.maxConcurrentStreams
        initialPacketSize = state.initialPacketSize
        disablePathMtuDiscovery = state.disablePathMtuDiscovery
    }

    override fun setCustomConfig(config: String) {
        uiState.update { it.copy(customConfig = config) }
    }

    override fun setCustomOutbound(outbound: String) {
        uiState.update { it.copy(customOutbound = outbound) }
    }

    fun setName(name: String) {
        uiState.update { it.copy(name = name) }
    }

    fun setAddress(address: String) {
        uiState.update { it.copy(address = address) }
    }

    fun setPort(port: Int) {
        uiState.update { it.copy(port = port) }
    }

    fun setUuid(uuid: String) {
        uiState.update { it.copy(uuid = uuid) }
    }

    fun setToken(token: String) {
        uiState.update { it.copy(token = token) }
    }

    fun setAlpn(alpn: String) {
        uiState.update { it.copy(alpn = alpn) }
    }

    fun setCertificates(certs: String) {
        uiState.update { it.copy(certificates = certs) }
    }

    fun setCertPublicKeySha256(sha: String) {
        uiState.update { it.copy(certPublicKeySha256 = sha) }
    }

    fun setUdpRelayMode(mode: String) {
        uiState.update { it.copy(udpRelayMode = mode) }
    }

    fun setCongestionController(controller: String) {
        uiState.update { it.copy(congestionController = controller) }
    }

    fun setDisableSNI(disable: Boolean) {
        uiState.update { it.copy(disableSNI = disable) }
    }

    fun setSni(sni: String) {
        uiState.update { it.copy(sni = sni) }
    }

    fun setZeroRTT(enabled: Boolean) {
        uiState.update { it.copy(zeroRTT = enabled) }
    }

    fun setAllowInsecure(allow: Boolean) {
        uiState.update { it.copy(allowInsecure = allow) }
    }

    fun setEch(enabled: Boolean) {
        uiState.update { it.copy(ech = enabled) }
    }

    fun setEchConfig(config: String) {
        uiState.update { it.copy(echConfig = config) }
    }

    fun setEchQueryServerName(name: String) {
        uiState.update { it.copy(echQueryServerName = name) }
    }

    fun setClientCert(cert: String) {
        uiState.update { it.copy(clientCert = cert) }
    }

    fun setClientKey(key: String) {
        uiState.update { it.copy(clientKey = key) }
    }

    fun setIdleTimeout(value: String) {
        uiState.update { it.copy(idleTimeout = value) }
    }

    fun setKeepAlivePeriod(value: String) {
        uiState.update { it.copy(keepAlivePeriod = value) }
    }

    fun setStreamReceiveWindow(value: Int) {
        uiState.update { it.copy(streamReceiveWindow = value) }
    }

    fun setConnectionReceiveWindow(value: Int) {
        uiState.update { it.copy(connectionReceiveWindow = value) }
    }

    fun setMaxConcurrentStreams(value: Int) {
        uiState.update { it.copy(maxConcurrentStreams = value) }
    }

    fun setInitialPacketSize(value: Int) {
        uiState.update { it.copy(initialPacketSize = value) }
    }

    fun setDisablePathMtuDiscovery(value: Boolean) {
        uiState.update { it.copy(disablePathMtuDiscovery = value) }
    }
}