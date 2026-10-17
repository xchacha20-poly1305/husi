package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.tailscale.TailcatBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class TailcatUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val privateKey: String = "",
    val serverPublicKey: String = "",
    val serverDiscoKey: String = "",
    val preSharedKey: String = "",
    val derpMapURL: String = "",
    val derpRegion: Int = 0,
    val derpServers: String = "",
) : ProfileEditorUiState

@Stable
internal class TailcatSettingsViewModel : ProfileEditorViewModel<TailcatBean>() {
    override fun createBean() = TailcatBean().applyDefaultValues()

    override val uiState: StateFlow<TailcatUiState>
        field = MutableStateFlow(TailcatUiState())

    override suspend fun TailcatBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                privateKey = privateKey,
                serverPublicKey = serverPublicKey,
                serverDiscoKey = serverDiscoKey,
                preSharedKey = preSharedKey,
                derpMapURL = derpMapURL,
                derpRegion = derpRegion,
                derpServers = derpServers,
            )
        }
    }

    override fun TailcatBean.loadFromUiState() {
        val state = uiState.value

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        privateKey = state.privateKey
        serverPublicKey = state.serverPublicKey
        serverDiscoKey = state.serverDiscoKey
        preSharedKey = state.preSharedKey
        derpMapURL = state.derpMapURL
        derpRegion = state.derpRegion
        derpServers = state.derpServers
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

    fun setPrivateKey(key: String) {
        uiState.update { it.copy(privateKey = key) }
    }

    fun setServerPublicKey(key: String) {
        uiState.update { it.copy(serverPublicKey = key) }
    }

    fun setServerDiscoKey(key: String) {
        uiState.update { it.copy(serverDiscoKey = key) }
    }

    fun setPreSharedKey(key: String) {
        uiState.update { it.copy(preSharedKey = key) }
    }

    fun setDerpMapURL(url: String) {
        uiState.update { it.copy(derpMapURL = url) }
    }

    fun setDerpRegion(region: Int) {
        uiState.update { it.copy(derpRegion = region) }
    }

    fun setDerpServers(servers: String) {
        uiState.update { it.copy(derpServers = servers) }
    }
}
