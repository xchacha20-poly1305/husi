package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.shadowquic.ShadowQUICBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class ShadowQUICUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 443,
    val username: String = "",
    val password: String = "",
    val sni: String = "",
    val alpn: String = "h3",
    val initialMtu: Int = 1300,
    val minMtu: Int = 1290,
    val congestionControl: String = "bbr",
    val zeroRTT: Boolean = false,
    val udpOverStream: Boolean = false,
    val gso: Boolean = false,
    val keepAliveInterval: Int = 0,
    val mtuDiscovery: Boolean = false,
    val subProtocol: Int = ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC,

    val extraPaths: String = "",
    val maxPaths: Int = 0,
) : ProfileSettingsUiState

@Stable
internal class ShadowQUICSettingsViewModel : ProfileSettingsViewModel<ShadowQUICBean>() {
    override fun createBean() = ShadowQUICBean().applyDefaultValues()

    private val _uiState = MutableStateFlow(ShadowQUICUiState())
    override val uiState = _uiState.asStateFlow()

    override suspend fun ShadowQUICBean.writeToUiState() {
        _uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                port = serverPort,
                username = username,
                password = password,
                sni = sni,
                alpn = alpn,
                initialMtu = initialMTU,
                minMtu = minimumMTU,
                congestionControl = congestionControl,
                zeroRTT = zeroRTT,
                udpOverStream = udpOverStream,
                gso = gso,
                keepAliveInterval = keepAliveInterval,
                mtuDiscovery = mtuDiscovery,
                subProtocol = subProtocol,

                extraPaths = extraPaths,
                maxPaths = maxPaths,
            )
        }
    }

    override fun ShadowQUICBean.loadFromUiState() {
        val state = _uiState.value
        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        serverAddress = state.address
        serverPort = state.port
        username = state.username
        password = state.password
        sni = state.sni
        alpn = state.alpn
        initialMTU = state.initialMtu
        minimumMTU = state.minMtu
        congestionControl = state.congestionControl
        zeroRTT = state.zeroRTT
        udpOverStream = state.udpOverStream
        gso = state.gso
        keepAliveInterval = state.keepAliveInterval
        mtuDiscovery = state.mtuDiscovery
        subProtocol = state.subProtocol

        extraPaths = state.extraPaths
        maxPaths = state.maxPaths
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

    fun setUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun setSni(sni: String) {
        _uiState.update { it.copy(sni = sni) }
    }

    fun setAlpn(alpn: String) {
        _uiState.update { it.copy(alpn = alpn) }
    }

    fun setInitialMtu(mtu: Int) {
        _uiState.update { it.copy(initialMtu = mtu) }
    }

    fun setMinMtu(mtu: Int) {
        _uiState.update { it.copy(minMtu = mtu) }
    }

    fun setCongestionControl(control: String) {
        _uiState.update { it.copy(congestionControl = control) }
    }

    fun setZeroRTT(enabled: Boolean) {
        _uiState.update { it.copy(zeroRTT = enabled) }
    }

    fun setUdpOverStream(enabled: Boolean) {
        _uiState.update { it.copy(udpOverStream = enabled) }
    }

    fun setGso(enabled: Boolean) {
        _uiState.update { it.copy(gso = enabled) }
    }

    fun setKeepAliveInterval(interval: Int) {
        _uiState.update { it.copy(keepAliveInterval = interval) }
    }

    fun setMtuDiscovery(enabled: Boolean) {
        _uiState.update { it.copy(mtuDiscovery = enabled) }
    }

    fun setSubProtocol(protocol: Int) {
        _uiState.update { it.copy(subProtocol = protocol) }
    }

    fun setExtraPaths(extraPaths: String) {
        _uiState.update { it.copy(extraPaths = extraPaths) }
    }

    fun setMaxPaths(maxPaths: Int) {
        _uiState.update { it.copy(maxPaths = maxPaths) }
    }
}
