package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.hysteria.HysteriaBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class HysteriaUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val protocolVersion: Int = HysteriaBean.PROTOCOL_VERSION_2,
    val address: String = "127.0.0.1",
    val ports: String = "443",
    val obfsType: String = "",
    val obfsPassword: String = "",
    val geckoMinPacketSize: Int = 0,
    val geckoMaxPacketSize: Int = 0,
    val authType: Int = HysteriaBean.TYPE_NONE,
    val authPayload: String = "",
    val protocol: Int = HysteriaBean.PROTOCOL_UDP,
    val sni: String = "",
    val alpn: String = "",
    val certificates: String = "",
    val certPublicKeySha256: String = "",
    val allowInsecure: Boolean = false,
    val disableSNI: Boolean = false,
    val streamReceiveWindow: Int = 0,
    val connectionReceiveWindow: Int = 0,
    val disableMtuDiscovery: Boolean = false,
    val hopInterval: String = "10s",
    val clientCert: String = "",
    val clientKey: String = "",
    val congestionControl: String = HysteriaBean.CONGESTION_CONTROL_BBR,
    val bbrProfile: Int = HysteriaBean.BBR_PROFILE_STANDARD,
    val disableChromeParrot: Boolean = false,
    val ech: Boolean = false,
    val echConfig: String = "",
    val echQueryServerName: String = "",
    val idleTimeout: String = "",
    val keepAlivePeriod: String = "",
    val maxConcurrentStreams: Int = 0,
    val initialPacketSize: Int = 0,
) : ProfileEditorUiState

@Stable
internal class HysteriaSettingsViewModel : ProfileEditorViewModel<HysteriaBean>() {
    override fun createBean() = HysteriaBean().applyDefaultValues()

    override val uiState: StateFlow<HysteriaUiState>
        field = MutableStateFlow(HysteriaUiState())

    override suspend fun HysteriaBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                protocolVersion = protocolVersion,
                address = serverAddress,
                ports = serverPorts,
                obfsType = obfsType,
                obfsPassword = obfsPassword,
                geckoMinPacketSize = geckoMinPacketSize,
                geckoMaxPacketSize = geckoMaxPacketSize,
                authType = authPayloadType,
                authPayload = authPayload,
                protocol = protocol,
                sni = sni,
                alpn = alpn,
                certificates = certificates,
                certPublicKeySha256 = certPublicKeySha256,
                allowInsecure = allowInsecure,
                disableSNI = disableSNI,
                streamReceiveWindow = streamReceiveWindow,
                connectionReceiveWindow = connectionReceiveWindow,
                disableMtuDiscovery = disableMtuDiscovery,
                hopInterval = hopInterval,
                clientCert = clientCert,
                clientKey = clientKey,
                congestionControl = congestionControl,
                bbrProfile = bbrProfile,
                disableChromeParrot = disableChromeParrot,
                ech = ech,
                echConfig = echConfig,
                echQueryServerName = echQueryServerName,
                idleTimeout = idleTimeout,
                keepAlivePeriod = keepAlivePeriod,
                maxConcurrentStreams = maxConcurrentStreams,
                initialPacketSize = initialPacketSize,
            )
        }
    }

    override fun HysteriaBean.loadFromUiState() {
        val state = uiState.value
        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        protocolVersion = state.protocolVersion
        serverAddress = state.address
        serverPorts = state.ports
        obfsType = state.obfsType
        obfsPassword = state.obfsPassword
        geckoMinPacketSize = state.geckoMinPacketSize
        geckoMaxPacketSize = state.geckoMaxPacketSize
        authPayloadType = state.authType
        authPayload = state.authPayload
        protocol = state.protocol
        sni = state.sni
        alpn = state.alpn
        certificates = state.certificates
        certPublicKeySha256 = state.certPublicKeySha256
        allowInsecure = state.allowInsecure
        disableSNI = state.disableSNI
        streamReceiveWindow = state.streamReceiveWindow
        connectionReceiveWindow = state.connectionReceiveWindow
        disableMtuDiscovery = state.disableMtuDiscovery
        hopInterval = state.hopInterval
        clientCert = state.clientCert
        clientKey = state.clientKey
        congestionControl = state.congestionControl
        bbrProfile = state.bbrProfile
        disableChromeParrot = state.disableChromeParrot
        ech = state.ech
        echConfig = state.echConfig
        echQueryServerName = state.echQueryServerName
        idleTimeout = state.idleTimeout
        keepAlivePeriod = state.keepAlivePeriod
        maxConcurrentStreams = state.maxConcurrentStreams
        initialPacketSize = state.initialPacketSize
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

    fun setProtocolVersion(version: Int) {
        uiState.update { it.copy(protocolVersion = version) }
    }

    fun setAddress(address: String) {
        uiState.update { it.copy(address = address) }
    }

    fun setPorts(ports: String) {
        uiState.update { it.copy(ports = ports) }
    }

    fun setObfsType(type: String) {
        uiState.update { it.copy(obfsType = type) }
    }

    fun setObfsPassword(obfsPassword: String) {
        uiState.update { it.copy(obfsPassword = obfsPassword) }
    }

    fun setGeckoMinPacketSize(size: Int) {
        uiState.update { it.copy(geckoMinPacketSize = size) }
    }

    fun setGeckoMaxPacketSize(size: Int) {
        uiState.update { it.copy(geckoMaxPacketSize = size) }
    }

    fun setAuthType(type: Int) {
        uiState.update { it.copy(authType = type) }
    }

    fun setAuthPayload(payload: String) {
        uiState.update { it.copy(authPayload = payload) }
    }

    fun setProtocol(protocol: Int) {
        uiState.update { it.copy(protocol = protocol) }
    }

    fun setSni(sni: String) {
        uiState.update { it.copy(sni = sni) }
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

    fun setAllowInsecure(allow: Boolean) {
        uiState.update { it.copy(allowInsecure = allow) }
    }

    fun setDisableSNI(disable: Boolean) {
        uiState.update { it.copy(disableSNI = disable) }
    }

    fun setStreamReceiveWindow(window: Int) {
        uiState.update { it.copy(streamReceiveWindow = window) }
    }

    fun setConnectionReceiveWindow(window: Int) {
        uiState.update { it.copy(connectionReceiveWindow = window) }
    }

    fun setDisableMtuDiscovery(disable: Boolean) {
        uiState.update { it.copy(disableMtuDiscovery = disable) }
    }

    fun setHopInterval(interval: String) {
        uiState.update { it.copy(hopInterval = interval) }
    }

    fun setClientCert(cert: String) {
        uiState.update { it.copy(clientCert = cert) }
    }

    fun setClientKey(key: String) {
        uiState.update { it.copy(clientKey = key) }
    }

    fun setCongestionControl(congestionControl: String) {
        uiState.update { it.copy(congestionControl = congestionControl) }
    }

    fun setBBRProfile(bbrProfile: Int) {
        uiState.update { it.copy(bbrProfile = bbrProfile) }
    }

    fun setDisableChromeParrot(disable: Boolean) {
        uiState.update { it.copy(disableChromeParrot = disable) }
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

    fun setIdleTimeout(value: String) {
        uiState.update { it.copy(idleTimeout = value) }
    }

    fun setKeepAlivePeriod(value: String) {
        uiState.update { it.copy(keepAlivePeriod = value) }
    }

    fun setMaxConcurrentStreams(value: Int) {
        uiState.update { it.copy(maxConcurrentStreams = value) }
    }

    fun setInitialPacketSize(value: Int) {
        uiState.update { it.copy(initialPacketSize = value) }
    }
}
