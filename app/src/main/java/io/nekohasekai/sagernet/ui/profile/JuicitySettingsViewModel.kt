package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
) : ProfileSettingsUiState

internal class JuicitySettingsViewModel : ProfileSettingsViewModel<JuicityBean>() {
    override fun createBean() = JuicityBean().applyDefaultValues()

    private val _uiState = MutableStateFlow(JuicityUiState())
    override val uiState = _uiState.asStateFlow()

    override fun JuicityBean.writeToUiState() {
        _uiState.update {
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
        val state = _uiState.value

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

    fun setUuid(uuid: String) {
        _uiState.update { it.copy(uuid = uuid) }
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun setSni(sni: String) {
        _uiState.update { it.copy(sni = sni) }
    }

    fun setAllowInsecure(allow: Boolean) {
        _uiState.update { it.copy(allowInsecure = allow) }
    }

    fun setPinSha256(sha256: String) {
        _uiState.update { it.copy(pinSha256 = sha256) }
    }

}
