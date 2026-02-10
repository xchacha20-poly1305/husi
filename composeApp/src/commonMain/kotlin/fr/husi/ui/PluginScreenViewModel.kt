package fr.husi.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.fmt.PluginEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class PluginScreenUiState(
    val plugins: List<PluginDisplay> = emptyList(),
)

@Immutable
data class PluginDisplay(
    val id: String,
    val packageName: String,
    val version: String,
    val versionCode: Long,
    val provider: String,
    val entry: PluginEntry? = null,
    val path: String? = null,
)

@Stable
class PluginScreenViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PluginScreenUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadPlugins()
    }

    fun loadPlugins() {
        viewModelScope.launch(Dispatchers.IO) {
            loadPlugins0()
        }
    }

    private suspend fun loadPlugins0() {
        loadPlatformPlugins { plugin ->
            val old = _uiState.value
            _uiState.emit(old.copy(plugins = old.plugins + plugin))
        }
    }
}
