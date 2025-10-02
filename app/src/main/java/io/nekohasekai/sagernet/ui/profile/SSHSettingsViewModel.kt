package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
) : ProfileSettingsUiState

internal class SSHSettingsViewModel : ProfileSettingsViewModel<SSHBean>() {
    override fun createBean() = SSHBean().applyDefaultValues()

    private val _uiState = MutableStateFlow(SshUiState())
    override val uiState = _uiState.asStateFlow()

    override fun SSHBean.writeToUiState() {
        _uiState.update {
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
        val state = _uiState.value

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

    fun setAuthType(type: Int) {
        _uiState.update { it.copy(authType = type) }
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun setPrivateKey(key: String) {
        _uiState.update { it.copy(privateKey = key) }
    }

    fun setPrivateKeyPassphrase(passphrase: String) {
        _uiState.update { it.copy(privateKeyPassphrase = passphrase) }
    }

    fun setPublicKey(key: String) {
        _uiState.update { it.copy(publicKey = key) }
    }
}