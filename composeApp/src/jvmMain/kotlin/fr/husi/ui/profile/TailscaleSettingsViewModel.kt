package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.tailscale.TailscaleBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class TailscaleUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val stateDirectory: String = "",
    val authKey: String = "",
    val controlURL: String = "",
    val ephemeral: Boolean = false,
    val hostname: String = "",
    val acceptRoutes: Boolean = false,
    val exitNode: String = "",
    val exitNodeAllowLANAccess: Boolean = false,
    val advertiseRoutes: String = "",
    val advertiseExitNode: Boolean = false,
    val advertiseTags: String = "",
    val listenPort: Int = 0,
    val advertiseFakeDNSRoutes: Boolean = false,
    val sshServer: Boolean = false,
    val taildropDirectory: String = "",
) : ProfileEditorUiState

@Stable
internal class TailscaleSettingsViewModel : ProfileEditorViewModel<TailscaleBean>() {
    override fun createBean() = TailscaleBean().applyDefaultValues()

    override val uiState: StateFlow<TailscaleUiState>
        field = MutableStateFlow(TailscaleUiState())

    override suspend fun TailscaleBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                stateDirectory = stateDirectory,
                authKey = authKey,
                controlURL = controlURL,
                ephemeral = ephemeral,
                hostname = hostname,
                acceptRoutes = acceptRoutes,
                exitNode = exitNode,
                exitNodeAllowLANAccess = exitNodeAllowLANAccess,
                advertiseRoutes = advertiseRoutes,
                advertiseExitNode = advertiseExitNode,
                advertiseTags = advertiseTags,
                listenPort = listenPort,
                advertiseFakeDNSRoutes = advertiseFakeDNSRoutes,
                sshServer = sshServer,
                taildropDirectory = taildropDirectory,
            )
        }
    }

    override fun TailscaleBean.loadFromUiState() {
        val state = uiState.value

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        stateDirectory = state.stateDirectory
        authKey = state.authKey
        controlURL = state.controlURL
        ephemeral = state.ephemeral
        hostname = state.hostname
        acceptRoutes = state.acceptRoutes
        exitNode = state.exitNode
        exitNodeAllowLANAccess = state.exitNodeAllowLANAccess
        advertiseRoutes = state.advertiseRoutes
        advertiseExitNode = state.advertiseExitNode
        advertiseTags = state.advertiseTags
        listenPort = state.listenPort
        advertiseFakeDNSRoutes = state.advertiseFakeDNSRoutes
        sshServer = state.sshServer
        taildropDirectory = state.taildropDirectory
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

    fun setStateDirectory(directory: String) {
        uiState.update { it.copy(stateDirectory = directory) }
    }

    fun setAuthKey(key: String) {
        uiState.update { it.copy(authKey = key) }
    }

    fun setControlURL(url: String) {
        uiState.update { it.copy(controlURL = url) }
    }

    fun setEphemeral(ephemeral: Boolean) {
        uiState.update { it.copy(ephemeral = ephemeral) }
    }

    fun setHostname(hostname: String) {
        uiState.update { it.copy(hostname = hostname) }
    }

    fun setAcceptRoutes(accept: Boolean) {
        uiState.update { it.copy(acceptRoutes = accept) }
    }

    fun setExitNode(exitNode: String) {
        uiState.update { it.copy(exitNode = exitNode) }
    }

    fun setExitNodeAllowLANAccess(allow: Boolean) {
        uiState.update { it.copy(exitNodeAllowLANAccess = allow) }
    }

    fun setAdvertiseRoutes(routes: String) {
        uiState.update { it.copy(advertiseRoutes = routes) }
    }

    fun setAdvertiseExitNode(advertise: Boolean) {
        uiState.update { it.copy(advertiseExitNode = advertise) }
    }

    fun setAdvertiseTags(tags: String) {
        uiState.update { it.copy(advertiseTags = tags) }
    }

    fun setListenPort(port: Int) {
        uiState.update { it.copy(listenPort = port) }
    }

    fun setAdvertiseFakeDNSRoutes(advertise: Boolean) {
        uiState.update { it.copy(advertiseFakeDNSRoutes = advertise) }
    }

    fun setSSHServer(enabled: Boolean) {
        uiState.update { it.copy(sshServer = enabled) }
    }

    fun setTaildropDirectory(directory: String) {
        uiState.update { it.copy(taildropDirectory = directory) }
    }
}
