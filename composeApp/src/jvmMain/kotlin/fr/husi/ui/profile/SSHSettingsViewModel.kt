package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.ssh.SSHBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class SshUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 22,
    val username: String = "root",
    val authType: Int = SSHBean.AUTH_TYPE_PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val privateKeyPassphrase: String = "",
    val publicKey: String = "",
) : ProfileEditorUiState

@Stable
internal class SSHSettingsViewModel : ProfileEditorViewModel<SSHBean>() {
    override fun createBean() = SSHBean().applyDefaultValues()

    override val uiState: StateFlow<SshUiState>
        field = MutableStateFlow(SshUiState())

    override suspend fun SSHBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                port = serverPort,
                username = username,
                authType = authType,
                password = password,
                privateKey = privateKey,
                privateKeyPassphrase = privateKeyPassphrase,
                publicKey = publicKey,
            )
        }
    }

    override fun SSHBean.loadFromUiState() {
        val state = uiState.value

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        serverAddress = state.address
        serverPort = state.port
        username = state.username
        authType = state.authType
        password = state.password
        privateKey = state.privateKey
        privateKeyPassphrase = state.privateKeyPassphrase
        publicKey = state.publicKey
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

    fun setAuthType(type: Int) {
        uiState.update { it.copy(authType = type) }
    }

    fun setPassword(password: String) {
        uiState.update { it.copy(password = password) }
    }

    fun setPrivateKey(key: String) {
        uiState.update { it.copy(privateKey = key) }
    }

    fun setPrivateKeyPassphrase(passphrase: String) {
        uiState.update { it.copy(privateKeyPassphrase = passphrase) }
    }

    fun setPublicKey(key: String) {
        uiState.update { it.copy(publicKey = key) }
    }
}