package io.nekohasekai.sagernet.ui

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.collection.ArraySet
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.iterator
import kotlin.coroutines.coroutineContext

@Stable
internal data class AppListActivityUiState(
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val apps: List<ProxiedApp> = emptyList(), // sorted, for show
)

@Stable
internal sealed interface AppListActivityUIEvent {
    class Snackbar(val message: StringOrRes) : AppListActivityUIEvent
}

@Stable
internal class AppListActivityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppListActivityUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<AppListActivityUIEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private lateinit var packageManager: PackageManager

    private val proxiedUids = ArraySet<Int>()
    private val cachedApps by lazy {
        PackageCache.installedPackages.toMutableMap().apply {
            remove(BuildConfig.APPLICATION_ID)
        }
    }

    fun initialize(pm: PackageManager, packages: Set<String>) {
        PackageCache.awaitLoadSync()
        packageManager = pm

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(isLoading = true, apps = emptyList())
            }
            val cachedApps = cachedApps
            for ((packageName, packageInfo) in cachedApps) {
                if (packages.contains(packageName)) {
                    proxiedUids.add(packageInfo.applicationInfo!!.uid)
                }
            }
            reload(cachedApps)
        }
    }

    private val iconCache = mutableMapOf<String, Drawable>()
    private fun loadIcon(packageManager: PackageManager, packageInfo: PackageInfo): Drawable {
        return iconCache.getOrPut(packageInfo.packageName) {
            packageInfo.applicationInfo!!.loadIcon(packageManager)
        }
    }

    suspend fun reload(cachedApps: Map<String, PackageInfo> = this.cachedApps) {
        val apps = mutableListOf<ProxiedApp>()
        val query = _uiState.value.searchQuery
        for ((packageName, packageInfo) in cachedApps) {
            coroutineContext[Job]!!.ensureActive()

            val applicationInfo = packageInfo.applicationInfo!!
            val name = applicationInfo.loadLabel(packageManager).toString()
            query.blankAsNull()?.lowercase()?.let {
                val hit = packageName.lowercase().contains(it)
                        || name.lowercase().contains(it)
                        || applicationInfo.uid.toString().contains(it)
                if (!hit) continue
            }

            val proxiedApp = ProxiedApp(
                appInfo = applicationInfo,
                packageName = packageName,
                isProxied = proxiedUids.contains(applicationInfo.uid),
                icon = loadIcon(packageManager,packageInfo),
                name = name,
            )
            apps.add(proxiedApp)
        }
        val comparator = compareBy<ProxiedApp>({ !it.isProxied }, { it.name })
        apps.sortWith(comparator)
        _uiState.update {
            it.copy(
                isLoading = false,
                apps = apps,
            )
        }
    }

    fun setSearchQuery(query: String) = viewModelScope.launch {
        _uiState.update { it.copy(searchQuery = query) }
        reload()
    }

    fun invertSections() = viewModelScope.launch {
        val current = ArraySet(proxiedUids) // clone
        val cachedApps = cachedApps
        val allUids = ArraySet<Int>()
        for ((_, packageInfo) in cachedApps) {
            allUids.add(packageInfo.applicationInfo!!.uid)
        }
        proxiedUids.clear()
        proxiedUids.ensureCapacity(cachedApps.size - current.size)
        allUids.filter { it !in current }.forEach { proxiedUids.add(it) }
        reload()
    }

    fun clearSections() = viewModelScope.launch {
        proxiedUids.clear()
        reload()
    }

    fun allPackages(): ArrayList<String> {
        return cachedApps.mapNotNullTo(ArrayList()) { (packageName, packageInfo) ->
            val uid = packageInfo.applicationInfo!!.uid
            if (uid in proxiedUids) {
                packageName
            } else {
                null
            }
        }
    }

    fun export(): String {
        val body = allPackages().joinToString("\n")
        val full = "false\n$body"
        return full
    }

    fun import(raw: String?) = viewModelScope.launch {
        if (raw?.blankAsNull() == null) {
            _uiEvent.emit(AppListActivityUIEvent.Snackbar(StringOrRes.Res(R.string.action_import_err)))
        }
        var bypass = false
        val apps = raw!!.lineSequence().let {
            when (it.firstOrNull()) {
                "false" -> {
                    bypass = false
                    it.drop(1)
                }

                "true" -> {
                    bypass = true
                    it.drop(1)
                }

                else -> it
            }
        }
        proxiedUids.clear()
        val cachedApps = cachedApps
        val proxiedPackageName = mutableListOf<String>()
        // Bypass mode: choose all apps that not mentioned in list
        if (bypass) {
            for (packageName in apps) {
                cachedApps.remove(packageName)
            }
            for ((packageName, packageInfo) in cachedApps) {
                proxiedUids.add(packageInfo.applicationInfo!!.uid)
                proxiedPackageName.add(packageName)
            }
        } else {
            for (packageName in apps) {
                val info = cachedApps[packageName] ?: continue
                proxiedUids.add(info.applicationInfo!!.uid)
                proxiedPackageName.add(packageName)
            }
        }
        _uiEvent.emit(AppListActivityUIEvent.Snackbar(StringOrRes.Res(R.string.action_import_msg)))
        reload()
    }

    fun onItemClick(app: ProxiedApp) = viewModelScope.launch {
        val newIsProxied = !proxiedUids.remove(app.uid)
        if (newIsProxied) {
            proxiedUids.add(app.uid)
        }
        _uiState.update { state ->
            state.copy(apps = state.apps.map {
                if (it.uid == app.uid) {
                    it.copy(isProxied = newIsProxied)
                } else {
                    it
                }
            })
        }
    }

}