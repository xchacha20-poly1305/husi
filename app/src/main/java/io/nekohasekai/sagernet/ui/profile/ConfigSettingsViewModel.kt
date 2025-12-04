package io.nekohasekai.sagernet.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.nekohasekai.sagernet.fmt.config.ConfigBean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class ConfigUiState(
    val name: String = "",
    val type: Int = ConfigBean.TYPE_OUTBOUND,
    override val customConfig: String = "",
    override val customOutbound: String = "",
) : ProfileSettingsUiState

@Stable
internal class ConfigSettingsViewModel : ProfileSettingsViewModel<ConfigBean>() {

    override fun createBean() = ConfigBean()

    private val _uiState = MutableStateFlow(ConfigUiState())
    override val uiState = _uiState.asStateFlow()


    override fun ConfigBean.writeToUiState() {
        _uiState.update {
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
        val state = _uiState.value
        name = state.name
        type = state.type
        config = when (type) {
            ConfigBean.TYPE_CONFIG -> state.customConfig
            ConfigBean.TYPE_OUTBOUND -> state.customOutbound
            else -> error("impossible")
        }
    }

    override fun setCustomConfig(config: String) {
        _uiState.update { it.copy(customConfig = config) }
    }

    override fun setCustomOutbound(outbound: String) {
        _uiState.update { it.copy(customOutbound = outbound) }
    }

    fun setName(name: String) {
        _uiState.update { it.copy(name=name) }
    }

    fun setType(type: Int) {
        _uiState.update { it.copy(type=type) }
    }

    fun setConfig(config: String) {
        _uiState.update {
            when (it.type) {
                ConfigBean.TYPE_CONFIG -> it.copy(customConfig = config)
                ConfigBean.TYPE_OUTBOUND -> it.copy(customOutbound = config)
                else -> error("impossible")
            }
        }
    }

}