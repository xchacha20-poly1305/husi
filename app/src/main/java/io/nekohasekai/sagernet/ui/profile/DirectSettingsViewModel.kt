package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.fmt.direct.DirectBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class DirectUiState(
    val name: String = "",
    override val customConfig: String = "",
    override val customOutbound: String = "",
) : ProfileSettingsUiState

internal class DirectSettingsViewModel : ProfileSettingsViewModel<DirectBean>() {
    override fun createBean() = DirectBean().applyDefaultValues()

    private val _uiState = MutableStateFlow(DirectUiState())
    override val uiState = _uiState.asStateFlow()

    override fun DirectBean.writeToUiState() {
        _uiState.update {
            it.copy(
                name = name,
            )
        }
    }

    override fun DirectBean.loadFromUiState() {
        val state = _uiState.value

        name = state.name
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
}
