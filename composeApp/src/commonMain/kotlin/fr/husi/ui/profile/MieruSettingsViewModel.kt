package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.mieru.MieruBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class MieruUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 443,
    val protocol: String = "TCP",
    val username: String = "",
    val password: String = "",
    val mtu: Int = 1400,
    val muxNumber: Int = 0,
    val trafficPattern: String = "",
) : ProfileEditorUiState

@Stable
internal class MieruSettingsViewModel : ProfileEditorViewModel<MieruBean>() {
    override fun createBean() = MieruBean().applyDefaultValues()

    override val uiState: StateFlow<MieruUiState>
        field = MutableStateFlow(MieruUiState())

    override suspend fun MieruBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                port = serverPort,
                protocol = protocol,
                username = username,
                password = password,
                mtu = mtu,
                muxNumber = serverMuxNumber,
                trafficPattern = trafficPattern,
            )
        }
    }

    override fun MieruBean.loadFromUiState() {
        val state = uiState.value
        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        serverAddress = state.address
        serverPort = state.port
        protocol = state.protocol
        username = state.username
        password = state.password
        mtu = state.mtu
        serverMuxNumber = state.muxNumber
        trafficPattern = state.trafficPattern
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

    fun setProtocol(protocol: String) {
        uiState.update { it.copy(protocol = protocol) }
    }

    fun setUsername(username: String) {
        uiState.update { it.copy(username = username) }
    }

    fun setPassword(password: String) {
        uiState.update { it.copy(password = password) }
    }

    fun setMtu(mtu: Int) {
        uiState.update { it.copy(mtu = mtu) }
    }

    fun setMuxNumber(number: Int) {
        uiState.update { it.copy(muxNumber = number) }
    }

    fun setTrafficPattern(trafficPattern: String) {
        uiState.update { it.copy(trafficPattern = trafficPattern) }
    }

}
