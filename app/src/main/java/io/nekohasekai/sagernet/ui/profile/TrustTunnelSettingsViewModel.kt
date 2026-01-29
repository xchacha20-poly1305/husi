package io.nekohasekai.sagernet.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.nekohasekai.sagernet.fmt.trusttunnel.TrustTunnelBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class TrustTunnelUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 443,
    val username: String = "",
    val password: String = "",
    val healthCheck: Boolean = false,
    val quic: Boolean = false,
    val quicCongestionControl: String = "bbr",
    val sni: String = "",
    val alpn: String = "",
    val certificates: String = "",
    val certPublicKeySha256: String = "",
    val utlsFingerprint: String = "",
    val allowInsecure: Boolean = false,
    val tlsFragment: Boolean = false,
    val tlsFragmentFallbackDelay: String = "0s",
    val tlsRecordFragment: Boolean = false,
    val ech: Boolean = false,
    val echConfig: String = "",
    val echQueryServerName: String = "",
    val clientCert: String = "",
    val clientKey: String = "",
) : ProfileSettingsUiState

@Stable
internal class TrustTunnelSettingsViewModel : ProfileSettingsViewModel<TrustTunnelBean>() {
    override fun createBean() = TrustTunnelBean().applyDefaultValues()

    private val _uiState = MutableStateFlow(TrustTunnelUiState())
    override val uiState = _uiState.asStateFlow()

    override suspend fun TrustTunnelBean.writeToUiState() {
        _uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                port = serverPort,
                username = username,
                password = password,
                healthCheck = healthCheck,
                quic = quic,
                quicCongestionControl = quicCongestionControl,
                sni = serverName,
                alpn = alpn,
                certificates = certificates,
                certPublicKeySha256 = certPublicKeySha256,
                utlsFingerprint = utlsFingerprint,
                allowInsecure = allowInsecure,
                tlsFragment = tlsFragment,
                tlsFragmentFallbackDelay = tlsFragmentFallbackDelay,
                tlsRecordFragment = tlsRecordFragment,
                ech = ech,
                echConfig = echConfig,
                echQueryServerName = echQueryServerName,
                clientCert = clientCert,
                clientKey = clientKey,
            )
        }
    }

    override fun TrustTunnelBean.loadFromUiState() {
        val state = _uiState.value

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        serverAddress = state.address
        serverPort = state.port
        username = state.username
        password = state.password
        healthCheck = state.healthCheck
        quic = state.quic
        quicCongestionControl = state.quicCongestionControl
        serverName = state.sni
        alpn = state.alpn
        certificates = state.certificates
        certPublicKeySha256 = state.certPublicKeySha256
        utlsFingerprint = state.utlsFingerprint
        allowInsecure = state.allowInsecure
        tlsFragment = state.tlsFragment
        tlsFragmentFallbackDelay = state.tlsFragmentFallbackDelay
        tlsRecordFragment = state.tlsRecordFragment
        ech = state.ech
        echConfig = state.echConfig
        echQueryServerName = state.echQueryServerName
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

    fun setUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun setHealthCheck(enabled: Boolean) {
        _uiState.update { it.copy(healthCheck = enabled) }
    }

    fun setQuic(enabled: Boolean) {
        _uiState.update { it.copy(quic = enabled) }
    }

    fun setQuicCongestionControl(control: String) {
        _uiState.update { it.copy(quicCongestionControl = control) }
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

    fun setUtlsFingerprint(fingerprint: String) {
        _uiState.update { it.copy(utlsFingerprint = fingerprint) }
    }

    fun setAllowInsecure(allow: Boolean) {
        _uiState.update { it.copy(allowInsecure = allow) }
    }

    fun setTlsFragment(enabled: Boolean) {
        _uiState.update { it.copy(tlsFragment = enabled) }
    }

    fun setTlsFragmentFallbackDelay(delay: String) {
        _uiState.update { it.copy(tlsFragmentFallbackDelay = delay) }
    }

    fun setTlsRecordFragment(enabled: Boolean) {
        _uiState.update { it.copy(tlsRecordFragment = enabled) }
    }

    fun setEch(enabled: Boolean) {
        _uiState.update { it.copy(ech = enabled) }
    }

    fun setEchConfig(config: String) {
        _uiState.update { it.copy(echConfig = config) }
    }

    fun setEchQueryServerName(name: String) {
        _uiState.update { it.copy(echQueryServerName = name) }
    }

    fun setClientCert(cert: String) {
        _uiState.update { it.copy(clientCert = cert) }
    }

    fun setClientKey(key: String) {
        _uiState.update { it.copy(clientKey = key) }
    }
}
