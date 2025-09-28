package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class HysteriaUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val protocolVersion: Int = HysteriaBean.PROTOCOL_VERSION_2,
    val address: String = "127.0.0.1",
    val ports: String = "443",
    val obfuscation: String = "",
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
    val mtlsCert: String = "",
    val mtlsKey: String = "",
    val ech: Boolean = false,
    val echConfig: String = "",
) : ProfileSettingsUiState

internal class HysteriaSettingsViewModel : ProfileSettingsViewModel<HysteriaBean>() {
    override fun createBean() = HysteriaBean().applyDefaultValues()

    private val _uiState = MutableStateFlow(HysteriaUiState())
    override val uiState = _uiState.asStateFlow()

    override fun HysteriaBean.writeToUiState() {
        _uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                protocolVersion = protocolVersion,
                address = serverAddress,
                ports = serverPorts,
                obfuscation = obfuscation,
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
                mtlsCert = mtlsCert,
                mtlsKey = mtlsKey,
                ech = ech,
                echConfig = echConfig,
            )
        }
    }

    override fun HysteriaBean.loadFromUiState() {
        val state = _uiState.value
        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        protocolVersion = state.protocolVersion
        serverAddress = state.address
        serverPorts = state.ports
        obfuscation = state.obfuscation
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
        mtlsCert = state.mtlsCert
        mtlsKey = state.mtlsKey
        ech = state.ech
        echConfig = state.echConfig
    }

    override fun setCustomConfig(config: String) {
        _uiState.update { it.copy(customConfig = config) }
    }

    override fun setCustomOutbound(outbound: String) {
        _uiState.update { it.copy(customOutbound = outbound) }
    }

    fun setName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun setProtocolVersion(version: Int) {
        _uiState.update { it.copy(protocolVersion = version) }
    }

    fun setAddress(address: String) {
        _uiState.update { it.copy(address = address) }
    }

    fun setPorts(ports: String) {
        _uiState.update { it.copy(ports = ports) }
    }

    fun setObfuscation(obfuscation: String) {
        _uiState.update { it.copy(obfuscation = obfuscation) }
    }

    fun setAuthType(type: Int) {
        _uiState.update { it.copy(authType = type) }
    }

    fun setAuthPayload(payload: String) {
        _uiState.update { it.copy(authPayload = payload) }
    }

    fun setProtocol(protocol: Int) {
        _uiState.update { it.copy(protocol = protocol) }
    }

    fun setSni(sni: String) {
        _uiState.update { it.copy(sni = sni) }
    }

    fun setAlpn(alpn: String) {
        _uiState.update { it.copy(alpn = alpn) }
    }

    fun setCertificates(certs: String) {
        _uiState.update { it.copy(certificates = certs) }
    }

    fun setCertPublicKeySha256(sha: String) {
        _uiState.update { it.copy(certPublicKeySha256 = sha) }
    }

    fun setAllowInsecure(allow: Boolean) {
        _uiState.update { it.copy(allowInsecure = allow) }
    }

    fun setDisableSNI(disable: Boolean) {
        _uiState.update { it.copy(disableSNI = disable) }
    }

    fun setStreamReceiveWindow(window: Int) {
        _uiState.update { it.copy(streamReceiveWindow = window) }
    }

    fun setConnectionReceiveWindow(window: Int) {
        _uiState.update { it.copy(connectionReceiveWindow = window) }
    }

    fun setDisableMtuDiscovery(disable: Boolean) {
        _uiState.update { it.copy(disableMtuDiscovery = disable) }
    }

    fun setHopInterval(interval: String) {
        _uiState.update { it.copy(hopInterval = interval) }
    }

    fun setMtlsCert(cert: String) {
        _uiState.update { it.copy(mtlsCert = cert) }
    }

    fun setMtlsKey(key: String) {
        _uiState.update { it.copy(mtlsKey = key) }
    }

    fun setEch(enabled: Boolean) {
        _uiState.update { it.copy(ech = enabled) }
    }

    fun setEchConfig(config: String) {
        _uiState.update { it.copy(echConfig = config) }
    }
}