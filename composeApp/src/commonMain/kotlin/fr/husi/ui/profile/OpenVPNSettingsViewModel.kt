package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import fr.husi.fmt.openvpn.OpenVPNBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class OpenVPNUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 1194,
    val network: String = "udp",
    val username: String = "",
    val password: String = "",
    val cipher: String = "",
    val certificate: String = "",
    val clientCertificate: String = "",
    val clientKey: String = "",
    val serverName: String = "",
    val serverNameType: String = "",
    val peerFingerprint: String = "",
    val remoteCertificateKU: String = "",
    val remoteCertificateEKU: String = "",
    val controlWrapType: String = "",
    val controlWrapKey: String = "",
    val controlWrapDirection: String = "",
    val dataCiphers: String = "",
    val auth: String = "",
    val compression: String = "",
    val redirectGateway: Boolean = false,
    val mtu: Int = 1500,
) : ProfileEditorUiState

internal class OpenVPNSettingsViewModel : ProfileEditorViewModel<OpenVPNBean>() {
    override fun createBean() = OpenVPNBean().applyDefaultValues()
    private val mutableUiState = MutableStateFlow(OpenVPNUiState())

    override val uiState: StateFlow<OpenVPNUiState> = mutableUiState

    override suspend fun OpenVPNBean.writeToUiState() {
        mutableUiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                port = serverPort,
                network = network,
                username = username,
                password = password,
                cipher = cipher,
                certificate = certificate,
                clientCertificate = clientCertificate,
                clientKey = clientKey,
                serverName = serverName,
                serverNameType = serverNameType,
                peerFingerprint = peerFingerprint,
                remoteCertificateKU = remoteCertificateKU,
                remoteCertificateEKU = remoteCertificateEKU,
                controlWrapType = controlWrapType,
                controlWrapKey = controlWrapKey,
                controlWrapDirection = controlWrapDirection,
                dataCiphers = dataCiphers,
                auth = auth,
                compression = compression,
                redirectGateway = redirectGateway,
                mtu = mtu,
            )
        }
    }

    override fun OpenVPNBean.loadFromUiState() = uiState.value.let {
        customConfigJson = it.customConfig
        customOutboundJson = it.customOutbound
        name = it.name
        serverAddress = it.address
        serverPort = it.port
        network = it.network
        username = it.username
        password = it.password
        cipher = it.cipher
        certificate = it.certificate
        clientCertificate = it.clientCertificate
        clientKey = it.clientKey
        serverName = it.serverName
        serverNameType = it.serverNameType
        peerFingerprint = it.peerFingerprint
        remoteCertificateKU = it.remoteCertificateKU
        remoteCertificateEKU = it.remoteCertificateEKU
        controlWrapType = it.controlWrapType
        controlWrapKey = it.controlWrapKey
        controlWrapDirection = it.controlWrapDirection
        dataCiphers = it.dataCiphers
        auth = it.auth
        compression = it.compression
        redirectGateway = it.redirectGateway
        mtu = it.mtu
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

    fun setAddress(value: String) {
        mutableUiState.update { it.copy(address = value) }
    }

    fun setPort(value: Int) {
        mutableUiState.update { it.copy(port = value) }
    }

    fun setNetwork(value: String) {
        mutableUiState.update { it.copy(network = value) }
    }

    fun setUsername(value: String) {
        mutableUiState.update { it.copy(username = value) }
    }

    fun setPassword(value: String) {
        mutableUiState.update { it.copy(password = value) }
    }

    fun setCipher(value: String) {
        mutableUiState.update { it.copy(cipher = value) }
    }

    fun setCertificate(value: String) {
        mutableUiState.update { it.copy(certificate = value) }
    }

    fun setClientCertificate(value: String) {
        mutableUiState.update { it.copy(clientCertificate = value) }
    }

    fun setClientKey(value: String) {
        mutableUiState.update { it.copy(clientKey = value) }
    }

    fun setServerName(value: String) {
        mutableUiState.update { it.copy(serverName = value) }
    }

    fun setServerNameType(value: String) {
        mutableUiState.update { it.copy(serverNameType = value) }
    }

    fun setPeerFingerprint(value: String) {
        mutableUiState.update { it.copy(peerFingerprint = value) }
    }

    fun setRemoteCertificateKU(value: String) {
        mutableUiState.update { it.copy(remoteCertificateKU = value) }
    }

    fun setRemoteCertificateEKU(value: String) {
        mutableUiState.update { it.copy(remoteCertificateEKU = value) }
    }

    fun setControlWrapType(value: String) {
        mutableUiState.update {
            it.copy(
                controlWrapType = value,
                controlWrapDirection = it.controlWrapDirection.takeIf { value == "tls_auth" }.orEmpty(),
            )
        }
    }

    fun setControlWrapKey(value: String) {
        mutableUiState.update { it.copy(controlWrapKey = value) }
    }

    fun setControlWrapDirection(value: String) {
        mutableUiState.update { it.copy(controlWrapDirection = value) }
    }

    fun setDataCiphers(value: String) {
        mutableUiState.update { it.copy(dataCiphers = value) }
    }

    fun setAuth(value: String) {
        mutableUiState.update { it.copy(auth = value) }
    }

    fun setCompression(value: String) {
        mutableUiState.update { it.copy(compression = value) }
    }

    fun setRedirectGateway(enabled: Boolean) {
        mutableUiState.update { it.copy(redirectGateway = enabled) }
    }

    fun setMtu(value: Int) {
        mutableUiState.update { it.copy(mtu = value) }
    }
}
