package fr.husi.ui

import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class AppListUiState(
    val isLoading: Boolean = false,
    val apps: List<ProxiedApp> = emptyList(), // sorted, full
    val filteredApps: List<ProxiedApp> = emptyList(), // sorted, filtered by search
    val snackbarMessage: StringOrRes? = null,
)

@Stable
internal class AppListViewModel(
    pm: PackageManager,
    appPackageName: String,
    packages: Set<String>,
) : BaseAppListViewModel() {
    val uiState: StateFlow<AppListUiState>
        field = MutableStateFlow(AppListUiState())

    init {
        packageManager = pm
        this.appPackageName = appPackageName
        collectSearchText()
        initialize(packages)
    }

    private fun initialize(packages: Set<String>) {
        viewModelScope.launch(singleThreadContext) {
            uiState.update { it.copy(isLoading = true, apps = emptyList()) }
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

    override fun updateApps(apps: List<ProxiedApp>, filteredApps: List<ProxiedApp>, isLoading: Boolean) {
        uiState.update { it.copy(isLoading = isLoading, apps = apps, filteredApps = filteredApps) }
    }

    override fun updateSnackbar(message: StringOrRes?) {
        uiState.update { it.copy(snackbarMessage = message) }
    }

    override suspend fun afterMutation() = reload()

    override suspend fun afterItemClick(app: ProxiedApp, newIsProxied: Boolean) {
        fun List<ProxiedApp>.updateProxiedState(): List<ProxiedApp> {
            return map {
                if (it.uid == app.uid) it.copy(isProxied = newIsProxied) else it
            }
        }
        uiState.update { state ->
            state.copy(
                apps = state.apps.updateProxiedState(),
                filteredApps = state.filteredApps.updateProxiedState(),
            )
        }
    }

    fun allPackages(): ArrayList<String> {
        return cachedApps.mapNotNullTo(ArrayList()) { (packageName, packageInfo) ->
            val uid = packageInfo.applicationInfo!!.uid
            if (uid in proxiedUids) packageName else null
        }
    }

    override fun export(): String {
        val body = allPackages().joinToString("\n")
        return "false\n$body"
    }

}
