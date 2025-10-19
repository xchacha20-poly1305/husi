package io.nekohasekai.sagernet.ui

import android.content.pm.PackageInfo
import android.os.Build
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.fmt.PluginEntry
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.plugin.PluginManager.loadString
import io.nekohasekai.sagernet.plugin.Plugins
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
internal data class AboutFragmentUiState(
    val plugins: List<AboutPlugin> = emptyList(),
)

@Stable
internal data class AboutPlugin(
    val id: String,
    val packageName: String,
    val version: String,
    val versionCode: Long,
    val provider: String,
    val entry: PluginEntry? = null,
)

@Stable
internal class AboutFragmentViewModel : ViewModel() {
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
            _uiState.emit(old.copy(
                plugins = old.plugins + AboutPlugin(
                    id = id,
                    packageName = packageName,
                    version = plugin.versionName ?: "unknown",
                    versionCode = plugin.versionCodeCompat(),
                    provider = Plugins.displayExeProvider(packageName),
                    entry = PluginEntry.find(id),
                )
            ))
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