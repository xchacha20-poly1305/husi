package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.fmt.shadowtls.ShadowTLSBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class ShadowTLSUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 443,
    val protocolVersion: Int = 3,
    val password: String = "",
    val sni: String = "",
    val alpn: String = "",
    val certificates: String = "",
    val certPublicKeySha256: String = "",
    val allowInsecure: Boolean = false,
    val utlsFingerprint: String = "",
) : ProfileSettingsUiState

internal class ShadowTLSSettingsViewModel : ProfileSettingsViewModel<ShadowTLSBean>() {
    override fun createBean() = ShadowTLSBean().applyDefaultValues()

    private val _uiState = MutableStateFlow(ShadowTLSUiState())
    override val uiState = _uiState.asStateFlow()

    override fun ShadowTLSBean.writeToUiState() {
        _uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                port = serverPort,
                protocolVersion = protocolVersion,
                password = password,
                sni = sni,
                alpn = alpn,
                certificates = certificates,
                certPublicKeySha256 = certPublicKeySha256,
                allowInsecure = allowInsecure,
                utlsFingerprint = utlsFingerprint,
            )
        }
    }

    override fun ShadowTLSBean.loadFromUiState() {
        val state = _uiState.value

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        serverAddress = state.address
        serverPort = state.port
        protocolVersion = state.protocolVersion
        password = state.password
        sni = state.sni
        alpn = state.alpn
        certificates = state.certificates
        certPublicKeySha256 = state.certPublicKeySha256
        allowInsecure = state.allowInsecure
        utlsFingerprint = state.utlsFingerprint
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

    fun setProtocolVersion(version: Int) {
        _uiState.update { it.copy(protocolVersion = version) }
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password) }
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

    fun setUtlsFingerprint(fingerprint: String) {
        _uiState.update { it.copy(utlsFingerprint = fingerprint) }
    }
}