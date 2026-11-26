package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.plugin.CustomPluginBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class CustomPluginUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val pluginId: String = "",
    val executablePath: String = "",
    val supportProtect: Boolean = false,
    val serverAddress: String = "",
    val serverPort: Int = 0,
    val commandArgs: String = "",
    val configFileName: String = CustomPluginBean.DEFAULT_CONFIG_FILE_NAME,
    val config: String = "",
    val environment: String = "",
) : ProfileEditorUiState

@Stable
internal class CustomPluginSettingsViewModel : ProfileEditorViewModel<CustomPluginBean>() {

    override fun createBean() = CustomPluginBean().applyDefaultValues()

    override val uiState: StateFlow<CustomPluginUiState>
        field = MutableStateFlow(CustomPluginUiState())

    override suspend fun CustomPluginBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                pluginId = pluginId,
                executablePath = executablePath,
                supportProtect = supportProtect,
                serverAddress = serverAddress,
                serverPort = serverPort,
                commandArgs = commandArgs,
                configFileName = configFileName,
                config = config,
                environment = environment,
            )
        }
    }

    override fun CustomPluginBean.loadFromUiState() {
        val state = uiState.value
        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        pluginId = state.pluginId
        executablePath = state.executablePath
        supportProtect = state.supportProtect
        serverAddress = state.serverAddress
        serverPort = state.serverPort
        commandArgs = state.commandArgs
        configFileName = state.configFileName
        config = state.config
        environment = state.environment
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

    fun setPluginId(pluginId: String) {
        uiState.update { it.copy(pluginId = pluginId) }
    }

    fun setExecutablePath(executablePath: String) {
        uiState.update { it.copy(executablePath = executablePath) }
    }

    fun setSupportProtect(supportProtect: Boolean) {
        uiState.update { it.copy(supportProtect = supportProtect) }
    }

    fun setServerAddress(serverAddress: String) {
        uiState.update { it.copy(serverAddress = serverAddress) }
    }

    fun setServerPort(serverPort: Int) {
        uiState.update { it.copy(serverPort = serverPort) }
    }

    fun setCommandArgs(commandArgs: String) {
        uiState.update { it.copy(commandArgs = commandArgs) }
    }

    fun setConfigFileName(configFileName: String) {
        uiState.update { it.copy(configFileName = configFileName) }
    }

    fun setConfig(config: String) {
        uiState.update { it.copy(config = config) }
    }

    fun setEnvironment(environment: String) {
        uiState.update { it.copy(environment = environment) }
    }
}
