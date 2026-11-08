package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.naive.NaiveBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class NaiveUiState(
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 443,
    val username: String = "",
    val password: String = "",
    val proto: String = "",
    val quicCongestionControl: String = "",
    val sni: String = "",
    val extraHeaders: String = "",
    val insecureConcurrency: Int = 0,
    val tunnelTimeout: Int = 0,
    val idleTimeout: Int = 0,
    val udpOverTcp: Boolean = false,
    val noPostQuantum: Boolean = false,
    val enableEch: Boolean = false,
    val echConfig: String = "",
    val echQueryServerName: String = "",
    override val customConfig: String = "",
    override val customOutbound: String = "",
) : ProfileEditorUiState

@Stable
internal class NaiveSettingsViewModel : ProfileEditorViewModel<NaiveBean>() {
    override fun createBean() = NaiveBean().applyDefaultValues()

    override val uiState: StateFlow<NaiveUiState>
        field = MutableStateFlow(NaiveUiState())

    override suspend fun NaiveBean.writeToUiState() {
        uiState.update {
            it.copy(
                name = name,
                address = serverAddress,
                port = serverPort,
                username = username,
                password = password,
                proto = proto,
                quicCongestionControl = quicCongestionControl,
                sni = sni,
                extraHeaders = extraHeaders,
                insecureConcurrency = insecureConcurrency,
                tunnelTimeout = tunnelTimeout,
                idleTimeout = idleTimeout,
                udpOverTcp = udpOverTcp,
                noPostQuantum = noPostQuantum,
                enableEch = enableEch,
                echConfig = echConfig,
                echQueryServerName = echQueryServerName,

                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
            )
        }
    }

    override fun NaiveBean.loadFromUiState() {
        val state = uiState.value

        name = state.name
        serverAddress = state.address
        serverPort = state.port
        username = state.username
        password = state.password
        proto = state.proto
        quicCongestionControl = state.quicCongestionControl
        sni = state.sni
        extraHeaders = state.extraHeaders
        insecureConcurrency = state.insecureConcurrency
        tunnelTimeout = state.tunnelTimeout
        idleTimeout = state.idleTimeout
        udpOverTcp = state.udpOverTcp
        noPostQuantum = state.noPostQuantum
        enableEch = state.enableEch
        echConfig = state.echConfig
        echQueryServerName = state.echQueryServerName

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
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

    fun setPassword(password: String) {
        uiState.update { it.copy(password = password) }
    }

    fun setProto(proto: String) {
        uiState.update { it.copy(proto = proto) }
    }

    fun setQuicCongestionControl(quicCongestionControl: String) {
        uiState.update { it.copy(quicCongestionControl = quicCongestionControl) }
    }

    fun setSni(sni: String) {
        uiState.update { it.copy(sni = sni) }
    }

    fun setExtraHeaders(extraHeaders: String) {
        uiState.update { it.copy(extraHeaders = extraHeaders) }
    }

    fun setInsecureConcurrency(concurrency: Int) {
        uiState.update { it.copy(insecureConcurrency = concurrency) }
    }

    fun setTunnelTimeout(timeout: Int) {
        uiState.update { it.copy(tunnelTimeout = timeout) }
    }

    fun setIdleTimeout(timeout: Int) {
        uiState.update { it.copy(idleTimeout = timeout) }
    }

    fun setUdpOverTcp(uot: Boolean) {
        uiState.update { it.copy(udpOverTcp = uot) }
    }

    fun setNoPostQuantum(noPostQuantum: Boolean) {
        uiState.update { it.copy(noPostQuantum = noPostQuantum) }
    }

    fun setEnableEch(enableEch: Boolean) {
        uiState.update { it.copy(enableEch = enableEch) }
    }

    fun setEchConfig(echConfig: String) {
        uiState.update { it.copy(echConfig = echConfig) }
    }

    fun setEchQueryServerName(echQueryServerName: String) {
        uiState.update { it.copy(echQueryServerName = echQueryServerName) }
    }

}
