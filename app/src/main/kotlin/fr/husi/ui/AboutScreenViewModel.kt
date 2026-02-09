package fr.husi.ui

import android.content.pm.PackageInfo
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.fmt.PluginEntry
import fr.husi.ktx.Logs
import fr.husi.plugin.PluginManager.loadString
import fr.husi.plugin.Plugins
import fr.husi.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class AboutFragmentUiState(
    val plugins: List<AboutPlugin> = emptyList(),
)

@Immutable
data class AboutPlugin(
    val id: String,
    val packageName: String,
    val version: String,
    val versionCode: Long,
    val provider: String,
    val entry: PluginEntry? = null,
)

@Stable
class AboutScreenViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AboutFragmentUiState())
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
        PackageCache.awaitLoadSync()
        for ((packageName, plugin) in PackageCache.installedPluginPackages) try {
            val id = plugin.providers!![0].loadString(Plugins.METADATA_KEY_ID)
            if (id.isNullOrBlank()) continue

            val old = _uiState.value
            _uiState.emit(
                old.copy(
                    plugins = old.plugins + AboutPlugin(
                        id = id,
                        packageName = packageName,
                        version = plugin.versionName ?: "unknown",
                        versionCode = plugin.versionCodeCompat(),
                        provider = Plugins.displayExeProvider(packageName),
                        entry = PluginEntry.find(id),
                    ),
                ),
            )
        } catch (e: Exception) {
            Logs.w(e)
        }
    }

    private fun PackageInfo.versionCodeCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }
    }
}