package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.direct.DirectBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class DirectUiState(
    val name: String = "",
    override val customConfig: String = "",
    override val customOutbound: String = "",
) : ProfileEditorUiState

@Stable
internal class DirectSettingsViewModel : ProfileEditorViewModel<DirectBean>() {
    override fun createBean() = DirectBean().applyDefaultValues()

    override val uiState: StateFlow<DirectUiState>
        field = MutableStateFlow(DirectUiState())

    override suspend fun DirectBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
            )
        }
    }

    override fun DirectBean.loadFromUiState() {
        val state = uiState.value

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
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
}
