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
import io.nekohasekai.sagernet.ktx.mapX
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.coroutines.coroutineContext

internal interface AbstractAppListUiState {
    val base: BaseAppListUiState
}

internal data class BaseAppListUiState(
    val apps: List<ProxiedApp> = emptyList(), // sorted
)

internal data class ProxiedApp(
    private val appInfo: ApplicationInfo,
    val packageName: String,
    var isProxied: Boolean,
    val name: String, // cached for sorting
) {
    val uid get() = appInfo.uid

    fun loadIcon(pm: PackageManager): Drawable {
        return appInfo.loadIcon(pm)
    }
}

internal sealed interface AbstractAppListUiEvent {
    class Snackbar(val message: StringOrRes) : AbstractAppListUiEvent
}

internal abstract class AbstractAppListViewModel : ViewModel() {

    abstract val uiState: StateFlow<AbstractAppListUiState>
    protected abstract suspend fun emitBaseState(baseState: BaseAppListUiState)

    private val _uiEvent = MutableSharedFlow<AbstractAppListUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    abstract var packages: Set<String>

    protected lateinit var packageManager: PackageManager

    fun initialize(pm: PackageManager) {
        val initialized = ::packageManager.isInitialized
        packageManager = pm
        if (!initialized) {
            viewModelScope.launch(Dispatchers.IO) {
                val routePackages = packages
                val cachedApps = cachedApps
                for ((packageName, packageInfo) in cachedApps) {
                    if (routePackages.contains(packageName)) {
                        proxiedUids.add(packageInfo.applicationInfo!!.uid)
                    }
                }
                reload(cachedApps)
            }
        }
    }

    var showSystemApp = false
        set(value) {
            field = value
            viewModelScope.launch(Dispatchers.IO) {
                reload()
            }
        }

    var searchText: String? = null
        set(value) {
            field = value
            viewModelScope.launch(Dispatchers.IO) {
                reload()
            }
        }

    protected suspend fun reload(cachedApps: Map<String, PackageInfo> = this.cachedApps) {
        emitBaseState(uiState.value.base.copy(apps = emptyList()))

        val apps = mutableListOf<ProxiedApp>()
        for ((packageName, packageInfo) in cachedApps) {
            coroutineContext[Job]!!.ensureActive()

            val applicationInfo = packageInfo.applicationInfo!!
            if (!showSystemApp && applicationInfo.isSystemApp) continue
            val name = applicationInfo.loadLabel(packageManager).toString()
            searchText?.lowercase()?.let {
                val hit = packageName.lowercase().contains(it)
                        || name.lowercase().contains(it)
                        || applicationInfo.uid.toString().contains(it)
                if (!hit) continue
            }

            val proxiedApp = ProxiedApp(
                applicationInfo,
                packageName,
                proxiedUids.contains(applicationInfo.uid),
                name,
            )
            apps.add(proxiedApp)
        }
        val comparator = compareBy<ProxiedApp>({ !it.isProxied }, { it.name })
        apps.sortWith(comparator)
        emitBaseState(uiState.value.base.copy(apps = apps))
    }

    private val ApplicationInfo.isSystemApp
        get() = flags and ApplicationInfo.FLAG_SYSTEM != 0

    protected val cachedApps by lazy {
        PackageCache.installedPackages.toMutableMap().apply {
            remove(BuildConfig.APPLICATION_ID)
        }
    }

    protected val proxiedUids = ArraySet<Int>()

    suspend fun onItemClick(app: ProxiedApp) {
        val newIsProxied = !proxiedUids.remove(app.uid)
        if (newIsProxied) {
            proxiedUids.add(app.uid)
        }
        val apps = uiState.value.base.apps.mapX {
            if (it.uid == app.uid)
                it.copy(isProxied = newIsProxied)
            else {
                it
            }
        }
        emitBaseState(uiState.value.base.copy(apps = apps))
        writeToDataStore()
    }

    suspend fun invertSelected() {
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

    suspend fun clearSelections() {
        proxiedUids.clear()
        reload()
        writeToDataStore()
    }

    suspend fun importFromClipboard(raw: String?) {
        if (raw.isNullOrBlank()) {
            _uiEvent.emit(
                AbstractAppListUiEvent.Snackbar(StringOrRes.Res(R.string.action_import_err))
            )
            return
        }

        val apps = raw.lineSequence().let {
            if (it.firstOrNull() in setOf("true", "false")) {
                it.drop(1)
            } else {
                it
            }
        }.toSet()
        proxiedUids.clear()
        val cachedApps = cachedApps
        val installedProxiedApps = mutableSetOf<String>()
        for (packageName in apps) {
            cachedApps[packageName]?.applicationInfo?.uid?.let {
                proxiedUids.add(it)
                installedProxiedApps.add(packageName)
            }
        }
        packages = installedProxiedApps
        reload(cachedApps)
        _uiEvent.emit(
            AbstractAppListUiEvent.Snackbar(StringOrRes.Res(R.string.action_import_msg))
        )
    }

    protected suspend fun writeToDataStore() {
        onIoDispatcher {
            packages = cachedApps.values.asSequence()
                .filter { it.applicationInfo!!.uid in proxiedUids }
                .map { it.packageName }
                .toCollection(LinkedHashSet())
        }
    }

}