package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.snell.SnellBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class SnellUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 443,
    val version: Int = SnellBean.VERSION_4,
    val psk: String = "",
    val userKey: String = "",
    val reuse: Boolean = false,
    val obfsMode: String = "",
    val obfsHost: String = "",
    val mode: String = "",
) : ProfileEditorUiState

@Stable
internal class SnellSettingsViewModel : ProfileEditorViewModel<SnellBean>() {
    override fun createBean() = SnellBean().applyDefaultValues()

    override val uiState: StateFlow<SnellUiState>
        field = MutableStateFlow(SnellUiState())

    override suspend fun SnellBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                port = serverPort,
                version = version,
                psk = psk,
                userKey = userKey,
                reuse = reuse,
                obfsMode = obfsMode,
                obfsHost = obfsHost,
                mode = mode,
            )
        }
    }

    override fun SnellBean.loadFromUiState() {
        val state = uiState.value
        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        serverAddress = state.address
        serverPort = state.port
        version = state.version
        psk = state.psk
        userKey = state.userKey
        reuse = state.reuse
        obfsMode = state.obfsMode
        obfsHost = state.obfsHost
        mode = state.mode
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

    fun setVersion(version: Int) {
        uiState.update { it.copy(version = version) }
    }

    fun setPsk(psk: String) {
        uiState.update { it.copy(psk = psk) }
    }

    fun setUserKey(userKey: String) {
        uiState.update { it.copy(userKey = userKey) }
    }

    fun setReuse(reuse: Boolean) {
        uiState.update { it.copy(reuse = reuse) }
    }

    fun setObfsMode(obfsMode: String) {
        uiState.update { it.copy(obfsMode = obfsMode) }
    }

    fun setObfsHost(obfsHost: String) {
        uiState.update { it.copy(obfsHost = obfsHost) }
    }

    fun setMode(mode: String) {
        uiState.update { it.copy(mode = mode) }
    }
}
