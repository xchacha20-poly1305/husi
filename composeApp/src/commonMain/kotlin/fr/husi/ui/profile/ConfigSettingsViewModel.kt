package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.config.ConfigBean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class ConfigUiState(
    val name: String = "",
    val type: Int = ConfigBean.TYPE_OUTBOUND,
    override val customConfig: String = "",
    override val customOutbound: String = "",
) : ProfileEditorUiState

@Stable
internal class ConfigSettingsViewModel : ProfileEditorViewModel<ConfigBean>() {

    override fun createBean() = ConfigBean()

    override val uiState: StateFlow<ConfigUiState>
        field = MutableStateFlow(ConfigUiState())


    override suspend fun ConfigBean.writeToUiState() {
        uiState.update {
            it.copy(
                name = name,
                type = type,
                customConfig = when (type) {
                    ConfigBean.TYPE_CONFIG -> config
                    ConfigBean.TYPE_OUTBOUND -> ""
                    else -> error("impossible")
                },
                customOutbound = when (type) {
                    ConfigBean.TYPE_CONFIG -> ""
                    ConfigBean.TYPE_OUTBOUND -> config
                    else -> error("impossible")
                },
            )
        }
    }

    override fun ConfigBean.loadFromUiState() {
        val state = uiState.value
        name = state.name
        type = state.type
        config = when (type) {
            ConfigBean.TYPE_CONFIG -> state.customConfig
            ConfigBean.TYPE_OUTBOUND -> state.customOutbound
            else -> error("impossible")
        }
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

    fun setType(type: Int) {
        uiState.update { it.copy(type = type) }
    }

    fun setConfigForResult(config: String) {
        uiState.update {
            when (it.type) {
                ConfigBean.TYPE_CONFIG -> it.copy(customConfig = config)
                ConfigBean.TYPE_OUTBOUND -> it.copy(customOutbound = config)
                else -> error("impossible")
            }
        }
    }

}