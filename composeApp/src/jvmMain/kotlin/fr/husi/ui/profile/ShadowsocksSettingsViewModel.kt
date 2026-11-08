package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.MuxStrategy
import fr.husi.MuxType
import fr.husi.fmt.shadowsocks.ShadowsocksBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class ShadowsocksUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 8388,
    val method: String = "aes-256-gcm",
    val password: String = "",

    val enableMux: Boolean = false,
    val brutal: Boolean = false,
    val muxType: Int = MuxType.H2MUX,
    val muxNumber: Int = 8,
    val muxStrategy: Int = MuxStrategy.MAX_CONNECTIONS,
    val muxPadding: Boolean = false,

    val pluginName: String = "",
    val pluginConfig: String = "",

    val udpOverTcp: Boolean = false,
) : ProfileEditorUiState

@Stable
internal class ShadowsocksSettingsViewModel : ProfileEditorViewModel<ShadowsocksBean>() {
    override fun createBean() = ShadowsocksBean().applyDefaultValues()

    override val uiState: StateFlow<ShadowsocksUiState>
        field = MutableStateFlow(ShadowsocksUiState())

    override suspend fun ShadowsocksBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                port = serverPort,
                method = method,
                password = password,
                enableMux = serverMux,
                brutal = serverBrutal,
                muxType = serverMuxType,
                muxNumber = serverMuxNumber,
                muxStrategy = serverMuxStrategy,
                muxPadding = serverMuxPadding,
                pluginName = plugin.substringBefore(";"),
                pluginConfig = plugin.substringAfter(";"),
                udpOverTcp = udpOverTcp,
            )
        }
    }

    override fun ShadowsocksBean.loadFromUiState() {
        val state = uiState.value
        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        serverAddress = state.address
        serverPort = state.port
        method = state.method
        password = state.password
        serverMux = state.enableMux
        serverBrutal = state.brutal
        serverMuxType = state.muxType
        serverMuxNumber = state.muxNumber
        serverMuxStrategy = state.muxStrategy
        serverMuxPadding = state.muxPadding
        udpOverTcp = state.udpOverTcp

        plugin = if (state.pluginName.isNotBlank()) {
            "${state.pluginName};${state.pluginConfig}"
        } else {
            ""
        }
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

    fun setMethod(method: String) {
        uiState.update { it.copy(method = method) }
    }

    fun setPassword(password: String) {
        uiState.update { it.copy(password = password) }
    }

    fun setEnableMux(enabled: Boolean) {
        uiState.update { it.copy(enableMux = enabled) }
    }

    fun setBrutal(enabled: Boolean) {
        uiState.update { it.copy(brutal = enabled) }
    }

    fun setMuxType(type: Int) {
        uiState.update { it.copy(muxType = type) }
    }

    fun setMuxNumber(number: Int) {
        uiState.update { it.copy(muxNumber = number) }
    }

    fun setMuxStrategy(strategy: Int) {
        uiState.update { it.copy(muxStrategy = strategy) }
    }

    fun setMuxPadding(enabled: Boolean) {
        uiState.update { it.copy(muxPadding = enabled) }
    }

    fun setPluginName(name: String) {
        uiState.update { it.copy(pluginName = name) }
    }

    fun setPluginConfig(config: String) {
        uiState.update { it.copy(pluginConfig = config) }
    }

    fun setUdpOverTcp(enabled: Boolean) {
        uiState.update { it.copy(udpOverTcp = enabled) }
    }
}