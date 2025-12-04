package io.nekohasekai.sagernet.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.nekohasekai.sagernet.fmt.mieru.MieruBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ProfileSettingsUiState

@Stable
internal class MieruSettingsViewModel : ProfileSettingsViewModel<MieruBean>() {
    override fun createBean() = MieruBean().applyDefaultValues()

    private val _uiState = MutableStateFlow(MieruUiState())
    override val uiState = _uiState.asStateFlow()

    override fun MieruBean.writeToUiState() {
        _uiState.update {
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
            )
        }
    }

    override fun MieruBean.loadFromUiState() {
        val state = _uiState.value
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
    }

    override fun setCustomConfig(config: String) {
        _uiState.update {
            it.copy(customConfig = config)
        }
    }

    override fun setCustomOutbound(outbound: String) {
        _uiState.update {
            it.copy(customOutbound = outbound)
        }
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

    fun setProtocol(protocol: String) {
        _uiState.update { it.copy(protocol = protocol) }
    }

    fun setUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun setMtu(mtu: Int) {
        _uiState.update { it.copy(mtu = mtu) }
    }

    fun setMuxNumber(number: Int) {
        _uiState.update { it.copy(muxNumber = number) }
    }

}
