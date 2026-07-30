package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import fr.husi.fmt.openconnect.OpenConnectBean
import fr.husi.fmt.openconnect.OpenConnectFormEntry
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class OpenConnectUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val server: String = "https://127.0.0.1",
    val flavor: String = "",
    val username: String = "",
    val password: String = "",
    val authGroup: String = "",
    val reportedOS: String = "",
    val userAgent: String = "",
    val localHostname: String = "",
    val allowInsecureCrypto: Boolean = false,
    val useTunnelDNS: Boolean = true,
    val tlsInsecure: Boolean = false,
    val tlsServerName: String = "",
    val tlsPeerFingerprint: String = "",
    val certificateAuthority: String = "",
    val clientCertificate: String = "",
    val clientKey: String = "",
    val clientKeyPassword: String = "",
    val mcaCertificate: String = "",
    val mcaKey: String = "",
    val mcaKeyPassword: String = "",
    val formEntries: List<OpenConnectFormEntry> = emptyList(),
) : ProfileEditorUiState

internal class OpenConnectSettingsViewModel : ProfileEditorViewModel<OpenConnectBean>() {

    override fun createBean() = OpenConnectBean().applyDefaultValues()
    private val mutableUiState = MutableStateFlow(OpenConnectUiState())

    override val uiState: StateFlow<OpenConnectUiState> = mutableUiState

    override suspend fun OpenConnectBean.writeToUiState() {
        mutableUiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                server = server,
                flavor = flavor,
                username = username,
                password = password,
                authGroup = authGroup,
                reportedOS = reportedOS,
                userAgent = userAgent,
                localHostname = localHostname,
                allowInsecureCrypto = allowInsecureCrypto,
                useTunnelDNS = useTunnelDNS,
                tlsInsecure = tlsInsecure,
                tlsServerName = tlsServerName,
                tlsPeerFingerprint = tlsPeerFingerprint,
                certificateAuthority = certificateAuthority,
                clientCertificate = clientCertificate,
                clientKey = clientKey,
                clientKeyPassword = clientKeyPassword,
                mcaCertificate = mcaCertificate,
                mcaKey = mcaKey,
                mcaKeyPassword = mcaKeyPassword,
                formEntries = formEntries,
            )
        }
    }

    override fun OpenConnectBean.loadFromUiState() = uiState.value.let {
        customConfigJson = it.customConfig
        customOutboundJson = it.customOutbound
        name = it.name
        server = it.server
        flavor = it.flavor
        username = it.username
        password = it.password
        authGroup = it.authGroup
        reportedOS = it.reportedOS
        userAgent = it.userAgent
        localHostname = it.localHostname
        allowInsecureCrypto = it.allowInsecureCrypto
        useTunnelDNS = it.useTunnelDNS
        tlsInsecure = it.tlsInsecure
        tlsServerName = it.tlsServerName
        tlsPeerFingerprint = it.tlsPeerFingerprint
        certificateAuthority = it.certificateAuthority
        clientCertificate = it.clientCertificate
        clientKey = it.clientKey
        clientKeyPassword = it.clientKeyPassword
        mcaCertificate = it.mcaCertificate
        mcaKey = it.mcaKey
        mcaKeyPassword = it.mcaKeyPassword
        formEntries = it.formEntries
    }

    override fun setCustomConfig(config: String) {
        mutableUiState.update { it.copy(customConfig = config) }
    }

    override fun setCustomOutbound(outbound: String) {
        mutableUiState.update { it.copy(customOutbound = outbound) }
    }

    fun setName(value: String) {
        mutableUiState.update { it.copy(name = value) }
    }

    fun setServer(value: String) {
        mutableUiState.update { it.copy(server = value) }
    }

    fun setFlavor(value: String) {
        mutableUiState.update { it.copy(flavor = value) }
    }

    fun setUsername(value: String) {
        mutableUiState.update { it.copy(username = value) }
    }

    fun setPassword(value: String) {
        mutableUiState.update { it.copy(password = value) }
    }

    fun setAuthGroup(value: String) {
        mutableUiState.update { it.copy(authGroup = value) }
    }

    fun clearFormEntries() {
        mutableUiState.update { it.copy(formEntries = emptyList()) }
    }

    fun setReportedOS(value: String) {
        mutableUiState.update { it.copy(reportedOS = value) }
    }

    fun setUserAgent(value: String) {
        mutableUiState.update { it.copy(userAgent = value) }
    }

    fun setLocalHostname(value: String) {
        mutableUiState.update { it.copy(localHostname = value) }
    }

    fun setAllowInsecureCrypto(allow: Boolean) {
        mutableUiState.update { it.copy(allowInsecureCrypto = allow) }
    }

    fun setUseTunnelDNS(value: Boolean) {
        mutableUiState.update { it.copy(useTunnelDNS = value) }
    }

    fun setTlsInsecure(value: Boolean) {
        mutableUiState.update { it.copy(tlsInsecure = value) }
    }

    fun setTlsServerName(value: String) {
        mutableUiState.update { it.copy(tlsServerName = value) }
    }

    fun setTlsPeerFingerprint(value: String) {
        mutableUiState.update { it.copy(tlsPeerFingerprint = value) }
    }

    fun setCertificateAuthority(value: String) {
        mutableUiState.update { it.copy(certificateAuthority = value) }
    }

    fun setClientCertificate(value: String) {
        mutableUiState.update { it.copy(clientCertificate = value) }
    }

    fun setClientKey(value: String) {
        mutableUiState.update { it.copy(clientKey = value) }
    }

    fun setClientKeyPassword(value: String) {
        mutableUiState.update { it.copy(clientKeyPassword = value) }
    }

    fun setMcaCertificate(value: String) {
        mutableUiState.update { it.copy(mcaCertificate = value) }
    }

    fun setMcaKey(value: String) {
        mutableUiState.update { it.copy(mcaKey = value) }
    }

    fun setMcaKeyPassword(value: String) {
        mutableUiState.update { it.copy(mcaKeyPassword = value) }
    }
}
