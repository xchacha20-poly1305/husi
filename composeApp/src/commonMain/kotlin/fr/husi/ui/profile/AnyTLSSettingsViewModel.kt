package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.anytls.AnyTLSBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class AnyTLSUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 443,
    val password: String = "",
    val idleSessionCheckInterval: String = "30s",
    val idleSessionTimeout: String = "30s",
    val minIdleSession: Int = 0,
    val sni: String = "",
    val alpn: String = "",
    val certificates: String = "",
    val certPublicKeySha256: String = "",
    val utlsFingerprint: String = "",
    val allowInsecure: Boolean = false,
    val disableSNI: Boolean = false,
    val tlsFragment: Boolean = false,
    val tlsFragmentFallbackDelay: String = "",
    val tlsRecordFragment: Boolean = false,
    val tlsSpoof: String = "",
    val tlsSpoofMethod: String = "",
    val ech: Boolean = false,
    val echConfig: String = "",
    val echQueryServerName: String = "",
    val clientCert: String = "",
    val clientKey: String = "",
) : ProfileEditorUiState

@Stable
internal class AnyTLSSettingsViewModel : ProfileEditorViewModel<AnyTLSBean>() {
    override fun createBean() = AnyTLSBean().applyDefaultValues()

    override val uiState: StateFlow<AnyTLSUiState>
        field = MutableStateFlow(AnyTLSUiState())

    override suspend fun AnyTLSBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                port = serverPort,
                password = password,
                idleSessionCheckInterval = idleSessionCheckInterval,
                idleSessionTimeout = idleSessionTimeout,
                minIdleSession = minIdleSession,
                sni = serverName,
                alpn = alpn,
                certificates = certificates,
                certPublicKeySha256 = certPublicKeySha256,
                utlsFingerprint = utlsFingerprint,
                allowInsecure = allowInsecure,
                disableSNI = disableSNI,
                tlsFragment = tlsFragment,
                tlsFragmentFallbackDelay = tlsFragmentFallbackDelay,
                tlsRecordFragment = tlsRecordFragment,
                tlsSpoof = tlsSpoof,
                tlsSpoofMethod = tlsSpoofMethod,
                ech = ech,
                echConfig = echConfig,
                echQueryServerName = echQueryServerName,
                clientCert = clientCert,
                clientKey = clientKey,
            )
        }
    }

    override fun AnyTLSBean.loadFromUiState() {
        val state = uiState.value
        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        serverAddress = state.address
        serverPort = state.port
        password = state.password
        idleSessionCheckInterval = state.idleSessionCheckInterval
        idleSessionTimeout = state.idleSessionTimeout
        minIdleSession = state.minIdleSession
        serverName = state.sni
        alpn = state.alpn
        certificates = state.certificates
        certPublicKeySha256 = state.certPublicKeySha256
        utlsFingerprint = state.utlsFingerprint
        allowInsecure = state.allowInsecure
        disableSNI = state.disableSNI
        tlsFragment = state.tlsFragment
        tlsFragmentFallbackDelay = state.tlsFragmentFallbackDelay
        tlsRecordFragment = state.tlsRecordFragment
        tlsSpoof = state.tlsSpoof
        tlsSpoofMethod = state.tlsSpoofMethod
        ech = state.ech
        echConfig = state.echConfig
        echQueryServerName = state.echQueryServerName
        clientCert = state.clientCert
        clientKey = state.clientKey
    }

    override fun setCustomConfig(config: String) {
        uiState.update {
            it.copy(customConfig = config)
        }
    }

    override fun setCustomOutbound(outbound: String) {
        uiState.update {
            it.copy(customOutbound = outbound)
        }
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

    fun setPassword(password: String) {
        uiState.update { it.copy(password = password) }
    }

    fun setIdleSessionCheckInterval(interval: String) {
        uiState.update { it.copy(idleSessionCheckInterval = interval) }
    }

    fun setIdleSessionTimeout(timeout: String) {
        uiState.update { it.copy(idleSessionTimeout = timeout) }
    }

    fun setMinIdleSession(count: Int) {
        uiState.update { it.copy(minIdleSession = count) }
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

    fun setUtlsFingerprint(fingerprint: String) {
        uiState.update { it.copy(utlsFingerprint = fingerprint) }
    }

    fun setAllowInsecure(allow: Boolean) {
        uiState.update { it.copy(allowInsecure = allow) }
    }

    fun setDisableSNI(disable: Boolean) {
        uiState.update { it.copy(disableSNI = disable) }
    }

    fun setTlsFragment(enabled: Boolean) {
        uiState.update { it.copy(tlsFragment = enabled) }
    }

    fun setTlsFragmentFallbackDelay(delay: String) {
        uiState.update { it.copy(tlsFragmentFallbackDelay = delay) }
    }

    fun setTlsRecordFragment(enabled: Boolean) {
        uiState.update { it.copy(tlsRecordFragment = enabled) }
    }

    fun setTlsSpoof(value: String) {
        uiState.update { it.copy(tlsSpoof = value) }
    }

    fun setTlsSpoofMethod(value: String) {
        uiState.update { it.copy(tlsSpoofMethod = value) }
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

}