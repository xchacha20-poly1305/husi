package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.socks.SOCKSBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
) : ProfileEditorUiState

@Stable
internal class SocksSettingsViewModel : ProfileEditorViewModel<SOCKSBean>() {
    override fun createBean() = SOCKSBean().applyDefaultValues()

    override val uiState: StateFlow<SocksUiState>
        field = MutableStateFlow(SocksUiState())

    override suspend fun SOCKSBean.writeToUiState() {
        uiState.update {
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
        val state = uiState.value

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
        uiState.update { it.copy(customConfig = config) }
    }

    override fun setCustomOutbound(outbound: String) {
        uiState.update { it.copy(customOutbound = outbound) }
    }

    fun setName(name: String) {
        uiState.update { it.copy(name = name) }
    }

    fun setProtocol(protocol: Int) {
        uiState.update { it.copy(protocol = protocol) }
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

    fun setUdpOverTcp(enabled: Boolean) {
        uiState.update { it.copy(udpOverTcp = enabled) }
    }
}