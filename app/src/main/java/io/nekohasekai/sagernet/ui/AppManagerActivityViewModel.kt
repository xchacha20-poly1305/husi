package io.nekohasekai.sagernet.ui

import android.content.pm.ApplicationInfo
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.utils.AppScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class AppManagerUiState(
    override val base: BaseAppListUiState = BaseAppListUiState(),
    val scanned: List<String>? = null,
    val scanProcess: Int? = null
) : AbstractAppListUiState

internal class AppManagerActivityViewModel : AbstractAppListViewModel() {
    val _uiState = MutableStateFlow(AppManagerUiState())
    override val uiState = _uiState.asStateFlow()

    override suspend fun emitBaseState(baseState: BaseAppListUiState) {
        _uiState.emit(_uiState.value.copy(base = baseState))
    }

    override var packages
        get() = DataStore.packages
        set(value) {
            DataStore.packages = value
        }

    private var scanJob: Job? = null

    fun scanChinaApps() {
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val chinaApps = mutableListOf<Pair<ApplicationInfo, String>>()
            val cachedApps = cachedApps
            val bypass = DataStore.bypassMode
            _uiState.emit(
                _uiState.value.copy(
                    scanned = emptyList(),
                    scanProcess = 0,
                )
            )
            for ((packageName, packageInfo) in cachedApps) {
                if (!isActive) {
                    _uiState.emit(_uiState.value.copy(scanned = null, scanProcess = null))
                    return@launch
                }

                val old = _uiState.value
                val scanned = old.scanned!! + packageName
                _uiState.emit(
                    old.copy(
                        scanned = scanned,
                        scanProcess = (scanned.size.toDouble() / cachedApps.size.toDouble() * 100).toInt(),
                    )
                )

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
}