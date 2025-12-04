package io.nekohasekai.sagernet.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.nekohasekai.sagernet.MuxStrategy
import io.nekohasekai.sagernet.MuxType
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ProfileSettingsUiState

@Stable
internal class ShadowsocksSettingsViewModel : ProfileSettingsViewModel<ShadowsocksBean>() {
    override fun createBean() = ShadowsocksBean().applyDefaultValues()

    private val _uiState = MutableStateFlow(ShadowsocksUiState())
    override val uiState = _uiState.asStateFlow()

    override suspend fun ShadowsocksBean.writeToUiState() {
        _uiState.update {
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
        val state = _uiState.value
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

    fun setMethod(method: String) {
        _uiState.update { it.copy(method = method) }
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun setEnableMux(enabled: Boolean) {
        _uiState.update { it.copy(enableMux = enabled) }
    }

    fun setBrutal(enabled: Boolean) {
        _uiState.update { it.copy(brutal = enabled) }
    }

    fun setMuxType(type: Int) {
        _uiState.update { it.copy(muxType = type) }
    }

    fun setMuxNumber(number: Int) {
        _uiState.update { it.copy(muxNumber = number) }
    }

    fun setMuxStrategy(strategy: Int) {
        _uiState.update { it.copy(muxStrategy = strategy) }
    }

    fun setMuxPadding(enabled: Boolean) {
        _uiState.update { it.copy(muxPadding = enabled) }
    }

    fun setPluginName(name: String) {
        _uiState.update { it.copy(pluginName = name) }
    }

    fun setPluginConfig(config: String) {
        _uiState.update { it.copy(pluginConfig = config) }
    }

    fun setUdpOverTcp(enabled: Boolean) {
        _uiState.update { it.copy(udpOverTcp = enabled) }
    }
}