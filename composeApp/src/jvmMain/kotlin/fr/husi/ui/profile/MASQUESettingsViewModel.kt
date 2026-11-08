package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.util.fastCoerceAtMost
import fr.husi.fmt.HttpVersion
import fr.husi.fmt.masque.MASQUEBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class MASQUEUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 443,
    val username: String = "",
    val password: String = "",
    val path: String = "",
    val headers: String = "",
    val httpVersion: Int = HttpVersion.HTTP_3,
    val disableVersionFallback: Boolean = false,
    val enableTLS: Boolean = true,
    val mtu: Int = MASQUEBean.DEFAULT_MTU,
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
) : ProfileEditorUiState {

    fun withTLSConstraints(): MASQUEUiState = if (enableTLS) {
        this
    } else {
        copy(
            httpVersion = httpVersion.fastCoerceAtMost(HttpVersion.HTTP_2),
            disableVersionFallback = false,
        )
    }
}

@Stable
internal class MASQUESettingsViewModel : ProfileEditorViewModel<MASQUEBean>() {
    override fun createBean() = MASQUEBean().applyDefaultValues()

    override val uiState: StateFlow<MASQUEUiState>
        field = MutableStateFlow(MASQUEUiState())

    override suspend fun MASQUEBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                port = serverPort,
                username = username,
                password = password,
                path = path,
                headers = headers,
                httpVersion = httpVersion,
                disableVersionFallback = disableVersionFallback,
                enableTLS = enableTLS,
                mtu = mtu,
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
            ).withTLSConstraints()
        }
    }

    override fun MASQUEBean.loadFromUiState() {
        val state = uiState.value
        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        serverAddress = state.address
        serverPort = state.port
        username = state.username
        password = state.password
        path = state.path
        headers = state.headers
        httpVersion = state.httpVersion
        disableVersionFallback = state.disableVersionFallback
        enableTLS = state.enableTLS
        mtu = state.mtu
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

    fun setUsername(username: String) {
        uiState.update { it.copy(username = username) }
    }

    fun setPassword(password: String) {
        uiState.update { it.copy(password = password) }
    }

    fun setPath(path: String) {
        uiState.update { it.copy(path = path) }
    }

    fun setHeaders(headers: String) {
        uiState.update { it.copy(headers = headers) }
    }

    fun setHttpVersion(version: Int) {
        uiState.update { it.copy(httpVersion = version).withTLSConstraints() }
    }

    fun setDisableVersionFallback(disable: Boolean) {
        uiState.update { it.copy(disableVersionFallback = disable).withTLSConstraints() }
    }

    fun setEnableTLS(enable: Boolean) {
        uiState.update { it.copy(enableTLS = enable).withTLSConstraints() }
    }

    fun setMtu(mtu: Int) {
        uiState.update { it.copy(mtu = mtu) }
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
