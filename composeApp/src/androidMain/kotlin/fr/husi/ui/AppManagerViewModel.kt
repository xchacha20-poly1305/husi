package fr.husi.ui

import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import fr.husi.database.DataStore
import fr.husi.utils.AppScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
internal data class AppManagerUiState(
    val mode: ProxyMode = ProxyMode.DISABLED,
    val isLoading: Boolean = false,
    val apps: List<ProxiedApp> = emptyList(), // sorted, full
    val filteredApps: List<ProxiedApp> = emptyList(), // sorted, filtered by search
    val scanned: List<String>? = null,
    val scanProcess: Float? = null,
    val snackbarMessage: StringOrRes? = null,
)

@Immutable
internal enum class ProxyMode {
    DISABLED,
    PROXY,
    BYPASS,
}

@Immutable
internal sealed interface AppManagerUiEvent {
    data object Finish : AppManagerUiEvent
}

@Stable
internal class AppManagerViewModel(
    pm: PackageManager,
    appPackageName: String,
) : BaseAppListViewModel() {
    val uiState: StateFlow<AppManagerUiState>
        field = MutableStateFlow(AppManagerUiState())
    val uiEvent: SharedFlow<AppManagerUiEvent>
        field = MutableSharedFlow<AppManagerUiEvent>()

    private var scanJob: Job? = null

    init {
        if (!DataStore.proxyApps.getBlocking()) {
            DataStore.proxyApps.setBlocking(true)
        }
        uiState.update { it.copy(mode = currentProxyMode()) }
        packageManager = pm
        this.appPackageName = appPackageName
        uiState.update {
            it.copy(isLoading = true, apps = emptyList())
        }
        viewModelScope.launch(singleThreadContext) {
            DataStore.packages.flow().collect { packages ->
                proxiedUids.clear()
                val cachedApps = cachedApps
                for ((packageName, packageInfo) in cachedApps) {
                    if (packages.contains(packageName)) {
                        proxiedUids.add(packageInfo.applicationInfo!!.uid)
                    }
                }
                reload(cachedApps)
            }
        }
        collectSearchText()
    }

    override fun updateApps(
        apps: List<ProxiedApp>,
        filteredApps: List<ProxiedApp>,
        isLoading: Boolean,
    ) {
        uiState.update { it.copy(isLoading = isLoading, apps = apps, filteredApps = filteredApps) }
    }

    override fun updateSnackbar(message: StringOrRes?) {
        uiState.update { it.copy(snackbarMessage = message) }
    }

    override suspend fun afterMutation() = writeToDataStore()

    override suspend fun applyImport(bypass: Boolean, apps: Sequence<String>) {
        DataStore.bypassMode.set(bypass)
        super.applyImport(bypass, apps)
    }

    override suspend fun afterItemClick(app: ProxiedApp, newIsProxied: Boolean) = writeToDataStore()

    override fun export(): String {
        val body = DataStore.packages.getBlocking().joinToString("\n")
        val bypassMode = DataStore.bypassMode.getBlocking()
        return "$bypassMode\n$body"
    }

    fun scanChinaApps() {
        scanJob = viewModelScope.launch(singleThreadContext) {
            val cachedApps = cachedApps
            val bypass = DataStore.bypassMode.get()
            uiState.update {
                it.copy(
                    scanned = emptyList(),
                    scanProcess = null,
                )
            }
            for ((packageName, packageInfo) in cachedApps) {
                if (!isActive) {
                    uiState.update {
                        it.copy(
                            scanned = null,
                            scanProcess = null,
                        )
                    }
                    return@launch
                }

                uiState.update { state ->
                    val scanned = state.scanned!! + packageName
                    state.copy(
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
            uiState.emit(uiState.value.copy(scanned = null, scanProcess = null))
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    private suspend fun writeToDataStore() {
        DataStore.packages.set(
            cachedApps.values.asSequence()
                .filter { it.applicationInfo!!.uid in proxiedUids }
                .map { it.packageName }
                .toCollection(LinkedHashSet()),
        )
    }

    private fun currentProxyMode(): ProxyMode {
        return if (!DataStore.proxyApps.getBlocking()) {
            ProxyMode.DISABLED
        } else if (DataStore.bypassMode.getBlocking()) {
            ProxyMode.BYPASS
        } else {
            ProxyMode.PROXY
        }
    }

    fun setProxyMode(mode: ProxyMode) = viewModelScope.launch {
        when (mode) {
            ProxyMode.DISABLED -> {
                DataStore.proxyApps.set(false)
                uiState.update { it.copy(mode = mode) }
                uiEvent.emit(AppManagerUiEvent.Finish)
                return@launch
            }

            ProxyMode.BYPASS -> {
                DataStore.proxyApps.set(true)
                DataStore.bypassMode.set(true)
            }

            ProxyMode.PROXY -> {
                DataStore.proxyApps.set(true)
                DataStore.bypassMode.set(false)
            }
        }
        uiState.update { it.copy(mode = mode) }
    }
}
