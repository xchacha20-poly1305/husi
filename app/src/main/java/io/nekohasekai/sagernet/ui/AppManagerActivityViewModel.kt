package io.nekohasekai.sagernet.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.collection.ArraySet
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.utils.AppScanner
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.coroutines.coroutineContext

internal data class AppManagerUiState(
    val searchQuery: String = "",
    val mode: ProxyMode = ProxyMode.DISABLED,
    val isLoading: Boolean = false,
    val apps: List<ProxiedApp> = emptyList(), // sorted
    val scanned: List<String>? = null,
    val scanProcess: Int? = null,
)

internal sealed interface AppManagerUiEvent {
    class Snackbar(val message: StringOrRes) : AppManagerUiEvent
    object Finish : AppManagerUiEvent
}

internal enum class ProxyMode {
    DISABLED,
    BYPASS,
    PROXY,
}

internal class AppManagerActivityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppManagerUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<AppManagerUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private var scanJob: Job? = null

    fun scanChinaApps() {
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val chinaApps = mutableListOf<Pair<ApplicationInfo, String>>()
            val cachedApps = cachedApps
            val bypass = DataStore.bypassMode
            _uiState.update {
                it.copy(
                    scanned = emptyList(),
                    scanProcess = 0,
                )
            }
            for ((packageName, packageInfo) in cachedApps) {
                if (!isActive) {
                    _uiState.update {
                        it.copy(
                            scanned = null,
                            scanProcess = null,
                        )
                    }
                    return@launch
                }

                val old = _uiState.value
                val scanned = old.scanned!! + packageName
                _uiState.update {
                    it.copy(
                        scanned = scanned,
                        scanProcess = (scanned.size.toDouble() / cachedApps.size.toDouble() * 100).toInt(),
                    )
                }

                val appInfo = packageInfo.applicationInfo!!
                if (AppScanner.isChinaApp(packageName, packageManager)) {
                    chinaApps.add(
                        appInfo to appInfo.loadLabel(packageManager).toString()
                    )
                    if (bypass) {
                        proxiedUids.add(appInfo.uid)
                    } else {
                        proxiedUids.remove(appInfo.uid)
                    }
                } else {
                    if (!bypass) {
                        proxiedUids.add(appInfo.uid)
                    } else {
                        proxiedUids.remove(appInfo.uid)
                    }
                }
            }
            reload(cachedApps)
            writeToDataStore()
            _uiState.emit(_uiState.value.copy(scanned = null, scanProcess = null))
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    private lateinit var packageManager: PackageManager

    private val proxiedUids = ArraySet<Int>()
    private val cachedApps by lazy {
        PackageCache.installedPackages.toMutableMap().apply {
            remove(BuildConfig.APPLICATION_ID)
        }
    }

    fun initialize(pm: PackageManager) = viewModelScope.launch(Dispatchers.IO) {
        if (!DataStore.proxyApps) {
            DataStore.proxyApps = true
        }
        _uiState.update { it.copy(mode = currentProxyMode()) }
        packageManager = pm
        _uiState.update {
            it.copy(isLoading = true, apps = emptyList())
        }
        val packages = DataStore.packages
        val cachedApps = cachedApps
        for ((packageName, packageInfo) in cachedApps) {
            if (packages.contains(packageName)) {
                proxiedUids.add(packageInfo.applicationInfo!!.uid)
            }
        }
        reload(cachedApps)
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
                icon = loadIcon(packageManager, packageInfo),
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
        writeToDataStore()
    }

    fun clearSections() = viewModelScope.launch {
        proxiedUids.clear()
        reload()
        writeToDataStore()
    }

    fun export(): String {
        val body = DataStore.packages.joinToString("\n")
        val full = "${DataStore.bypassMode}\n${body}"
        return full
    }

    fun import(raw: String?) = viewModelScope.launch {
        if (raw?.blankAsNull() == null) {
            _uiEvent.emit(AppManagerUiEvent.Snackbar(StringOrRes.Res(R.string.action_import_err)))
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
        DataStore.bypassMode = bypass
        proxiedUids.clear()
        val cachedApps = cachedApps
        val proxiedPackageName = mutableListOf<String>()
        for (packageName in apps) {
            val info = cachedApps[packageName]?.applicationInfo ?: continue
            proxiedUids.add(info.uid)
            proxiedPackageName.add(packageName)
        }
        _uiEvent.emit(AppManagerUiEvent.Snackbar(StringOrRes.Res(R.string.action_import_msg)))
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
        writeToDataStore()
    }

    private suspend fun writeToDataStore() {
        onIoDispatcher {
            DataStore.packages = cachedApps.values.asSequence()
                .filter { it.applicationInfo!!.uid in proxiedUids }
                .map { it.packageName }
                .toCollection(LinkedHashSet())
        }
    }

    private fun currentProxyMode(): ProxyMode {
        return if (!DataStore.proxyApps) {
            ProxyMode.DISABLED
        } else if (DataStore.bypassMode) {
            ProxyMode.BYPASS
        } else {
            ProxyMode.PROXY
        }
    }

    fun setProxyMode(mode: ProxyMode) = viewModelScope.launch {
        when (mode) {
            ProxyMode.DISABLED -> {
                DataStore.proxyApps = false
                _uiEvent.emit(AppManagerUiEvent.Finish)
            }

            ProxyMode.BYPASS -> {
                DataStore.proxyApps = true
                DataStore.bypassMode = true
            }

            ProxyMode.PROXY -> {
                DataStore.proxyApps = true
                DataStore.bypassMode = false
            }
        }
        _uiState.update { it.copy(mode = mode) }
    }
}