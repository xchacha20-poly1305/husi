package io.nekohasekai.sagernet.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class SocksUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val protocol: Int = SOCKSBean.PROTOCOL_SOCKS5,
    val address: String = "127.0.0.1",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val udpOverTcp: Boolean = false,
) : ProfileSettingsUiState

@Stable
internal class SocksSettingsViewModel : ProfileSettingsViewModel<SOCKSBean>() {
    override fun createBean() = SOCKSBean().applyDefaultValues()

    private val _uiState = MutableStateFlow(SocksUiState())
    override val uiState = _uiState.asStateFlow()

    override fun SOCKSBean.writeToUiState() {
        _uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                protocol = protocol,
                address = serverAddress,
                port = serverPort,
                username = username,
                password = password,
                udpOverTcp = udpOverTcp,
            )
        }
    }

    override fun SOCKSBean.loadFromUiState() {
        val state = _uiState.value

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        protocol = state.protocol
        serverAddress = state.address
        serverPort = state.port
        username = state.username
        password = state.password
        udpOverTcp = state.udpOverTcp
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

    fun setProtocol(protocol: Int) {
        _uiState.update { it.copy(protocol = protocol) }
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

    fun setUdpOverTcp(enabled: Boolean) {
        _uiState.update { it.copy(udpOverTcp = enabled) }
    }
}