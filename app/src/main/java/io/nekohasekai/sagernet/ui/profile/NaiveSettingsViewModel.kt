package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class NaiveUiState(
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 443,
    val username: String = "",
    val password: String = "",
    val proto: String = "",
    val sni: String = "",
    val extraHeaders: String = "",
    val insecureConcurrency: Int = 0,
    val udpOverTcp: Boolean = false,
    val noPostQuantum: Boolean = false,
    override val customConfig: String = "",
    override val customOutbound: String = "",
) : ProfileSettingsUiState

internal class NaiveSettingsViewModel : ProfileSettingsViewModel<NaiveBean>() {
    override fun createBean() = NaiveBean().applyDefaultValues()

    private val _uiState = MutableStateFlow(NaiveUiState())
    override val uiState = _uiState.asStateFlow()

    override fun NaiveBean.writeToUiState() {
        _uiState.update {
            it.copy(
                name = name,
                address = serverAddress,
                port = serverPort,
                username = username,
                password = password,
                proto = proto,
                sni = sni,
                extraHeaders = extraHeaders,
                insecureConcurrency = insecureConcurrency,
                udpOverTcp = udpOverTcp,
                noPostQuantum = noPostQuantum,

                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
            )
        }
    }

    override fun NaiveBean.loadFromUiState() {
        val state = _uiState.value

        name = state.name
        serverAddress = state.address
        serverPort = state.port
        username = state.username
        password = state.password
        proto = state.proto
        sni = state.sni
        extraHeaders = state.extraHeaders
        insecureConcurrency = state.insecureConcurrency
        udpOverTcp = state.udpOverTcp
        noPostQuantum = state.noPostQuantum

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
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

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun setProto(proto: String) {
        _uiState.update { it.copy(proto = proto) }
    }

    fun setSni(sni: String) {
        _uiState.update { it.copy(sni = sni) }
    }

    fun setExtraHeaders(extraHeaders: String) {
        _uiState.update { it.copy(extraHeaders = extraHeaders) }
    }

    fun setInsecureConcurrency(concurrency: Int) {
        _uiState.update { it.copy(insecureConcurrency = concurrency) }
    }

    fun setUdpOverTcp(uot: Boolean) {
        _uiState.update { it.copy(udpOverTcp = uot) }
    }

    fun setNoPostQuantum(noPostQuantum: Boolean) {
        _uiState.update { it.copy(noPostQuantum = noPostQuantum) }
    }

}
