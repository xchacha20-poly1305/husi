package io.nekohasekai.sagernet.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val clientCert: String = "",
    val clientKey: String = "",
) : ProfileSettingsUiState

@Stable
internal class TuicSettingsViewModel : ProfileSettingsViewModel<TuicBean>() {
    override fun createBean() = TuicBean().applyDefaultValues()

    private val _uiState = MutableStateFlow(TuicUiState())
    override val uiState = _uiState.asStateFlow()

    override suspend fun TuicBean.writeToUiState() {
        _uiState.update {
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
                clientCert = clientCert,
                clientKey = clientKey,
            )
        }
    }

    override fun TuicBean.loadFromUiState() {
        val state = _uiState.value

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
        clientCert = state.clientCert
        clientKey = state.clientKey
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

    fun setAddress(address: String) {
        _uiState.update { it.copy(address = address) }
    }

    fun setPort(port: Int) {
        _uiState.update { it.copy(port = port) }
    }

    fun setUuid(uuid: String) {
        _uiState.update { it.copy(uuid = uuid) }
    }

    fun setToken(token: String) {
        _uiState.update { it.copy(token = token) }
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

    fun setUdpRelayMode(mode: String) {
        _uiState.update { it.copy(udpRelayMode = mode) }
    }

    fun setCongestionController(controller: String) {
        _uiState.update { it.copy(congestionController = controller) }
    }

    fun setDisableSNI(disable: Boolean) {
        _uiState.update { it.copy(disableSNI = disable) }
    }

    fun setSni(sni: String) {
        _uiState.update { it.copy(sni = sni) }
    }

    fun setZeroRTT(enabled: Boolean) {
        _uiState.update { it.copy(zeroRTT = enabled) }
    }

    fun setAllowInsecure(allow: Boolean) {
        _uiState.update { it.copy(allowInsecure = allow) }
    }

    fun setEch(enabled: Boolean) {
        _uiState.update { it.copy(ech = enabled) }
    }

    fun setEchConfig(config: String) {
        _uiState.update { it.copy(echConfig = config) }
    }

    fun setClientCert(cert: String) {
        _uiState.update { it.copy(clientCert = cert) }
    }

    fun setClientKey(key: String) {
        _uiState.update { it.copy(clientKey = key) }
    }
}