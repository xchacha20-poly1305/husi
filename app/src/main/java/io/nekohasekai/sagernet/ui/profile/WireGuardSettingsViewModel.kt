package io.nekohasekai.sagernet.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class WireGuardUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 51820,
    val localAddress: String = "",
    val privateKey: String = "",
    val publicKey: String = "",
    val preSharedKey: String = "",
    val mtu: Int = 1420,
    val reserved: String = "",
    val listenPort: Int = 0,
    val persistentKeepaliveInterval: Int = 0,
) : ProfileSettingsUiState

@Stable
internal class WireGuardSettingsViewModel : ProfileSettingsViewModel<WireGuardBean>() {
    override fun createBean() = WireGuardBean().applyDefaultValues()

    private val _uiState = MutableStateFlow(WireGuardUiState())
    override val uiState = _uiState.asStateFlow()

    override fun WireGuardBean.writeToUiState() {
        _uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                port = serverPort,
                localAddress = localAddress,
                privateKey = privateKey,
                publicKey = publicKey,
                preSharedKey = preSharedKey,
                mtu = mtu,
                reserved = reserved,
                listenPort = listenPort,
                persistentKeepaliveInterval = persistentKeepaliveInterval,
            )
        }
    }

    override fun WireGuardBean.loadFromUiState() {
        val state = _uiState.value

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        serverAddress = state.address
        serverPort = state.port
        localAddress = state.localAddress
        privateKey = state.privateKey
        publicKey = state.publicKey
        preSharedKey = state.preSharedKey
        mtu = state.mtu
        reserved = state.reserved
        listenPort = state.listenPort
        persistentKeepaliveInterval = state.persistentKeepaliveInterval
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

    fun setLocalAddress(address: String) {
        _uiState.update { it.copy(localAddress = address) }
    }

    fun setPrivateKey(key: String) {
        _uiState.update { it.copy(privateKey = key) }
    }

    fun setPublicKey(key: String) {
        _uiState.update { it.copy(publicKey = key) }
    }

    fun setPreSharedKey(key: String) {
        _uiState.update { it.copy(preSharedKey = key) }
    }

    fun setMtu(mtu: Int) {
        _uiState.update { it.copy(mtu = mtu) }
    }

    fun setReserved(reserved: String) {
        _uiState.update { it.copy(reserved = reserved) }
    }

    fun setListenPort(port: Int) {
        _uiState.update { it.copy(listenPort = port) }
    }

    fun setPersistentKeepaliveInterval(interval: Int) {
        _uiState.update { it.copy(persistentKeepaliveInterval = interval) }
    }
}