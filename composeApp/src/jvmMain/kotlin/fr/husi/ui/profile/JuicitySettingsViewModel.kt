package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.juicity.JuicityBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class JuicityUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    val port: Int = 443,
    val uuid: String = "",
    val password: String = "",
    val sni: String = "",
    val allowInsecure: Boolean = false,
    val pinSha256: String = "",
) : ProfileEditorUiState

@Stable
internal class JuicitySettingsViewModel : ProfileEditorViewModel<JuicityBean>() {
    override fun createBean() = JuicityBean().applyDefaultValues()

    override val uiState: StateFlow<JuicityUiState>
        field = MutableStateFlow(JuicityUiState())

    override suspend fun JuicityBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                port = serverPort,
                uuid = uuid,
                password = password,
                sni = sni,
                allowInsecure = allowInsecure,
                pinSha256 = pinSHA256,
            )
        }
    }

    override fun JuicityBean.loadFromUiState() {
        val state = uiState.value

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        serverAddress = state.address
        serverPort = state.port
        uuid = state.uuid
        password = state.password
        sni = state.sni
        allowInsecure = state.allowInsecure
        pinSHA256 = state.pinSha256
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

    fun setUuid(uuid: String) {
        uiState.update { it.copy(uuid = uuid) }
    }

    fun setPassword(password: String) {
        uiState.update { it.copy(password = password) }
    }

    fun setSni(sni: String) {
        uiState.update { it.copy(sni = sni) }
    }

    fun setAllowInsecure(allow: Boolean) {
        uiState.update { it.copy(allowInsecure = allow) }
    }

    fun setPinSha256(sha256: String) {
        uiState.update { it.copy(pinSha256 = sha256) }
    }

}
