package io.nekohasekai.sagernet.ui

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.collection.ArraySet
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.onDefaultDispatcher
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.utils.AppScanner
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

@Immutable
internal data class AppManagerUiState(
    val mode: ProxyMode = ProxyMode.DISABLED,
    val isLoading: Boolean = false,
    val apps: List<ProxiedApp> = emptyList(), // sorted
    val scanned: List<String>? = null,
    val scanProcess: Float? = null,
    val snackbarMessage: StringOrRes? = null,
    val shouldFinish: Boolean = false,
)

@Immutable
internal enum class ProxyMode {
    DISABLED,
    PROXY,
    BYPASS,
}

@Stable
internal class AppManagerActivityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppManagerUiState())
    val uiState = _uiState.asStateFlow()

    val textFieldState = TextFieldState()

    private var scanJob: Job? = null

    fun scanChinaApps() {
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val cachedApps = cachedApps
            val bypass = DataStore.bypassMode
            _uiState.update {
                it.copy(
                    scanned = emptyList(),
                    scanProcess = null,
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
                        scanProcess = (scanned.size.toDouble() / cachedApps.size.toDouble()).toFloat(),
                    )
                }

                val appInfo = packageInfo.applicationInfo!!
                if (AppScanner.isChinaApp(packageName, packageManager)) {
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

    fun initialize(pm: PackageManager) {
        if (!DataStore.proxyApps) {
            DataStore.proxyApps = true
        }
        _uiState.update { it.copy(mode = currentProxyMode()) }
        packageManager = pm
        _uiState.update {
            it.copy(isLoading = true, apps = emptyList())
        }
        viewModelScope.launch(Dispatchers.IO) {
            DataStore.configurationStore.stringSetFlow(Key.PACKAGES).collect { packages ->
                proxiedUids.clear()
                val cachedApps = cachedApps
                for ((packageName, packageInfo) in cachedApps) {
                    if (packages.contains(packageName)) {
                        proxiedUids.add(packageInfo.applicationInfo!!.uid)
                    }
                }
                onDefaultDispatcher {
                    reload(cachedApps)
                }
            }
        }
        viewModelScope.launch {
            snapshotFlow { textFieldState.text.toString() }
                .drop(1)
                .distinctUntilChanged()
                .collect { reload() }
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
        val query = textFieldState.text.toString()
        for ((packageName, packageInfo) in cachedApps) {
            currentCoroutineContext()[Job]!!.ensureActive()

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

    fun invertSections() = viewModelScope.launch {
        val current = ArraySet(proxiedUids)
        val cachedApps = cachedApps
        val allUids = ArraySet<Int>()
        for ((_, packageInfo) in cachedApps) {
            allUids.add(packageInfo.applicationInfo!!.uid)
        }
        proxiedUids.clear()
        proxiedUids.ensureCapacity(cachedApps.size - current.size)
        allUids.filter { it !in current }.forEach { proxiedUids.add(it) }
        writeToDataStore()
    }

    fun clearSections() = viewModelScope.launch {
        proxiedUids.clear()
        writeToDataStore()
    }

    fun export(): String {
        val body = DataStore.packages.joinToString("\n")
        val full = "${DataStore.bypassMode}\n${body}"
        return full
    }

    fun import(raw: String?) = viewModelScope.launch {
        if (raw?.blankAsNull() == null) {
            _uiState.update { state ->
                state.copy(
                    snackbarMessage = StringOrRes.Res(R.string.action_import_err),
                )
            }
            return@launch
        }
        var bypass = false
        val apps = raw.lineSequence().let {
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

        if (bypass) {
            // Bypass mode: proxy all apps NOT in the import list
            val bypassSet = apps.toSet()
            for ((packageName, packageInfo) in cachedApps) {
                if (packageName !in bypassSet) {
                    proxiedUids.add(packageInfo.applicationInfo!!.uid)
                }
            }
        } else {
            // Proxy mode: proxy only apps in the import list
            for (packageName in apps) {
                val info = cachedApps[packageName]?.applicationInfo ?: continue
                proxiedUids.add(info.uid)
            }
        }
        _uiState.update { state ->
            state.copy(
                snackbarMessage = StringOrRes.Res(R.string.action_import_msg),
            )
        }
        writeToDataStore()
    }

    fun onItemClick(app: ProxiedApp) = viewModelScope.launch {
        val newIsProxied = !proxiedUids.remove(app.uid)
        if (newIsProxied) {
            proxiedUids.add(app.uid)
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
                _uiState.update { state ->
                    state.copy(
                        shouldFinish = true,
                    )
                }
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