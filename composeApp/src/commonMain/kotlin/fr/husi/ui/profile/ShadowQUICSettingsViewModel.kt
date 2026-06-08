package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.shadowquic.ShadowQUICBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val blackholeDetection: Boolean = false,
    val subProtocol: Int = ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC,

    val extraPaths: String = "",
    val maxPaths: Int = 0,
    val certificates: String = "",
) : ProfileEditorUiState

@Stable
internal class ShadowQUICSettingsViewModel : ProfileEditorViewModel<ShadowQUICBean>() {
    override fun createBean() = ShadowQUICBean().applyDefaultValues()

    override val uiState: StateFlow<ShadowQUICUiState>
        field = MutableStateFlow(ShadowQUICUiState())

    override suspend fun ShadowQUICBean.writeToUiState() {
        uiState.update {
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
                blackholeDetection = blackholeDetection,
                subProtocol = subProtocol,

                extraPaths = extraPaths,
                maxPaths = maxPaths.coerceIn(0, extraPaths.lines().count { path -> path.isNotBlank() }),
                certificates = certificates,
            )
        }
    }

    override fun ShadowQUICBean.loadFromUiState() {
        val state = uiState.value
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
        blackholeDetection = state.blackholeDetection
        subProtocol = state.subProtocol

        extraPaths = state.extraPaths
        maxPaths = state.maxPaths
        certificates = state.certificates
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

    fun setUsername(username: String) {
        uiState.update { it.copy(username = username) }
    }

    fun setPassword(password: String) {
        uiState.update { it.copy(password = password) }
    }

    fun setSni(sni: String) {
        uiState.update { it.copy(sni = sni) }
    }

    fun setAlpn(alpn: String) {
        uiState.update { it.copy(alpn = alpn) }
    }

    fun setInitialMtu(mtu: Int) {
        uiState.update { it.copy(initialMtu = mtu) }
    }

    fun setMinMtu(mtu: Int) {
        uiState.update { it.copy(minMtu = mtu) }
    }

    fun setCongestionControl(control: String) {
        uiState.update { it.copy(congestionControl = control) }
    }

    fun setZeroRTT(enabled: Boolean) {
        uiState.update { it.copy(zeroRTT = enabled) }
    }

    fun setUdpOverStream(enabled: Boolean) {
        uiState.update { it.copy(udpOverStream = enabled) }
    }

    fun setGso(enabled: Boolean) {
        uiState.update { it.copy(gso = enabled) }
    }

    fun setKeepAliveInterval(interval: Int) {
        uiState.update { it.copy(keepAliveInterval = interval) }
    }

    fun setMtuDiscovery(enabled: Boolean) {
        uiState.update { it.copy(mtuDiscovery = enabled) }
    }

    fun setBlackholeDetection(enabled: Boolean) {
        uiState.update { it.copy(blackholeDetection = enabled) }
    }

    fun setSubProtocol(protocol: Int) {
        uiState.update { it.copy(subProtocol = protocol) }
    }

    fun setExtraPaths(extraPaths: String) {
        uiState.update { state ->
            state.copy(
                extraPaths = extraPaths,
                maxPaths = state.maxPaths.coerceIn(0, extraPaths.lines().count { path -> path.isNotBlank() }),
            )
        }
    }

    fun setMaxPaths(maxPaths: Int) {
        uiState.update { state ->
            state.copy(
                maxPaths = maxPaths.coerceIn(
                    0,
                    state.extraPaths.lines().count { path -> path.isNotBlank() },
                ),
            )
        }
    }

    fun setCertificates(certificates: String) {
        uiState.update { it.copy(certificates = certificates) }
    }
}
