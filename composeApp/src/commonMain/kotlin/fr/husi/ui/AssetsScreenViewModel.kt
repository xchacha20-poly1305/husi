package fr.husi.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.RuleProvider
import fr.husi.bg.NoUpdateException
import fr.husi.bg.RouteAssetUpdater
import fr.husi.bg.currentEpochSeconds
import fr.husi.bg.routeAssetVersionFile
import fr.husi.bg.updateManagedRouteAssets
import fr.husi.bg.updateSingleRouteAsset
import fr.husi.database.AssetEntity
import fr.husi.database.DataStore
import fr.husi.database.SagerDatabase
import fr.husi.ktx.Logs
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.readableMessage
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.libcore.Libcore
import fr.husi.resources.Res
import fr.husi.resources.route_asset_no_update
import fr.husi.utils.copyBundledRuleSetAssetsIfNeeded
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Immutable
internal data class AssetsUiState(
    val process: Float? = null,
    val assets: List<AssetItem> = emptyList(),
    val pendingDeleteCount: Int = 0,
)

@Immutable
internal data class AssetItem(
    val file: File,
    val version: String,
    val builtIn: Boolean,
    val autoUpdateDelay: Int = 0,
    val progress: Float? = null,
)

@Immutable
internal sealed interface AssetsScreenUiEvent {
    class Snackbar(val message: StringOrRes) : AssetsScreenUiEvent
}

@Stable
internal class AssetsScreenViewModel(
    assetsDir: File,
    geoDir: File,
) : ViewModel() {

    companion object {
        fun isBuiltIn(index: Int): Boolean = index < 2
    }

    private val _uiState = MutableStateFlow(AssetsUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<AssetsScreenUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private lateinit var assetsDir: File
    private lateinit var geoDir: File

    private var previousAssetNames = emptySet<String>()
    private var initializedFor: Pair<String, String>? = null
    private var assetsObserveJob: Job? = null

    private var deleteTimer: Job? = null
    private val hiddenAssetsAccess = Mutex()
    private val hiddenAssets = mutableSetOf<String>()

    init {
        initialize(assetsDir, geoDir)
    }

    fun initialize(assetsDir: File, geoDir: File) {
        val args = assetsDir.absolutePath to geoDir.absolutePath
        if (initializedFor == args && assetsObserveJob?.isActive == true) return
        initializedFor = args
        assetsObserveJob?.cancel()
        this.assetsDir = assetsDir
        this.geoDir = geoDir

        assetsObserveJob = viewModelScope.launch {
            SagerDatabase.assetDao.getAll().collectLatest { assets ->
                val currentNames = assets.map { it.name }.toSet()
                val newAssets = currentNames - previousAssetNames

                newAssets.forEach { name ->
                    updateSingleAsset(geoDir.resolve(name))
                }

                previousAssetNames = currentNames
                refreshAssets0(assets)
            }
        }
    }

    fun refreshAssets() = viewModelScope.launch {
        val assets = SagerDatabase.assetDao.getAll().first()
        refreshAssets0(assets)
    }

    private suspend fun refreshAssets0(dbAssets: List<AssetEntity>) {
        val assetsByName = dbAssets.associateBy(AssetEntity::name)
        val files = buildList {
            add(assetsDir.resolve("geoip.version.txt"))
            add(assetsDir.resolve("geosite.version.txt"))
            dbAssets.forEach { add(geoDir.resolve(it.name)) }
        }

        hiddenAssetsAccess.withLock {
            _uiState.update { state ->
                state.copy(
                    assets = files.mapIndexed { index, file ->
                        buildAssetItem(index, file, assetsByName[file.name])
                    }.filterNot { hiddenAssets.contains(it.file.name) },
                    pendingDeleteCount = hiddenAssets.size,
                    process = null,
                )
            }
        }
    }

    private fun buildAssetItem(index: Int, file: File, entity: AssetEntity?): AssetItem {
        val builtIn = isBuiltIn(index)
        val version = if (builtIn) {
            file.takeIf(File::isFile)
                ?.readText()
                ?.trim()
                .blankAsNull()
                ?: "Unknown"
        } else {
            entity?.version.blankAsNull()
                ?: routeAssetVersionFile(assetsDir, file.name).takeIf(File::isFile)
                    ?.readText()
                    ?.trim()
                    .blankAsNull()
                ?: "Unknown"
        }
        return AssetItem(
            file = file,
            version = version,
            builtIn = builtIn,
            autoUpdateDelay = entity?.autoUpdateDelay ?: 0,
            progress = null,
        )
    }

    suspend fun deleteAssets(files: List<File>) {
        for (file in files) {
            file.delete()
            val versionFile = routeAssetVersionFile(assetsDir, file.name)
            if (versionFile.isFile) versionFile.delete()
            SagerDatabase.assetDao.delete(file.name)
        }
        RouteAssetUpdater.reconfigureUpdater()
    }

    fun updateAsset(cacheDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateAsset0(cacheDir)
            } catch (_: NoUpdateException) {
                _uiEvent.emit(AssetsScreenUiEvent.Snackbar(StringOrRes.Res(Res.string.route_asset_no_update)))
            } catch (e: Exception) {
                Logs.e(e)
                _uiEvent.emit(AssetsScreenUiEvent.Snackbar(StringOrRes.Direct(e.readableMessage)))
            }
            RouteAssetUpdater.reconfigureUpdater()
            val assets = SagerDatabase.assetDao.getAll().first()
            refreshAssets0(assets)
        }
    }

    private suspend fun updateAsset0(cacheDir: File) {
        _uiState.update { it.copy(process = 0f) }

        var process = 0f
        updateManagedRouteAssets(
            externalAssetsDir = assetsDir,
            cacheDir = cacheDir,
        ) { progressDelta ->
            process += progressDelta
            _uiState.update { it.copy(process = process) }
        }
    }

    fun resetRuleSet() = viewModelScope.launch(Dispatchers.IO) {
        if (DataStore.rulesProvider != RuleProvider.OFFICIAL) return@launch
        _uiState.update { it.copy(process = 0f) }
        try {
            copyBundledRuleSetAssetsIfNeeded()
            assetsDir.resolve("geoip.version.txt").delete()
            assetsDir.resolve("geosite.version.txt").delete()
            Libcore.extractAssets()
            DataStore.routeAssetsLastUpdated = currentEpochSeconds()
            RouteAssetUpdater.reconfigureUpdater()
        } catch (e: Exception) {
            Logs.e(e)
            _uiEvent.emit(AssetsScreenUiEvent.Snackbar(StringOrRes.Direct(e.readableMessage)))
        }
        val assets = SagerDatabase.assetDao.getAll().first()
        refreshAssets0(assets)
    }

    fun updateSingleAsset(asset: File) = viewModelScope.launch(Dispatchers.IO) {
        try {
            updateSingleAsset0(asset)
        } catch (e: Exception) {
            Logs.e(e)
            _uiEvent.emit(AssetsScreenUiEvent.Snackbar(StringOrRes.Direct(e.readableMessage)))
        }
        RouteAssetUpdater.reconfigureUpdater()
        val assets = SagerDatabase.assetDao.getAll().first()
        refreshAssets0(assets)
    }

    private suspend fun updateSingleAsset0(asset: File) {
        val entity = SagerDatabase.assetDao.get(asset.name) ?: return

        _uiState.update { state ->
            state.copy(
                assets = state.assets.map {
                    if (it.file == asset) {
                        it.copy(progress = 0f)
                    } else {
                        it
                    }
                },
            )
        }

        entity.version = updateSingleRouteAsset(entity, assetsDir) { progress ->
            _uiState.update { state ->
                state.copy(
                    assets = state.assets.map {
                        if (it.file == asset) {
                            it.copy(progress = progress)
                        } else {
                            it
                        }
                    },
                )
            }
        }
        entity.lastUpdated = currentEpochSeconds()
        SagerDatabase.assetDao.update(entity)
    }

    fun undoableRemove(fileName: String) = viewModelScope.launch {
        hiddenAssetsAccess.withLock {
            _uiState.update { state ->
                val assets = state.assets.toMutableList()
                val assetIndex = assets.indexOfFirst { it.file.name == fileName }
                if (assetIndex >= 0) {
                    val asset = assets.removeAt(assetIndex)
                    hiddenAssets.add(asset.file.name)
                }
                state.copy(
                    assets = assets,
                    pendingDeleteCount = hiddenAssets.size,
                )
            }
        }
        startDeleteTimer()
    }

    private fun startDeleteTimer() {
        deleteTimer?.cancel()
        deleteTimer = viewModelScope.launch {
            delay(5000)
            commit()
        }
    }

    fun undo() = viewModelScope.launch {
        deleteTimer?.cancel()
        deleteTimer = null
        hiddenAssetsAccess.withLock {
            hiddenAssets.clear()
        }
        refreshAssets()
    }

    fun commit() = runOnDefaultDispatcher {
        deleteTimer?.cancel()
        deleteTimer = null
        val toDelete = hiddenAssetsAccess.withLock {
            val pending = hiddenAssets.toList()
            hiddenAssets.clear()
            pending
        }
        runOnIoDispatcher {
            for (fileName in toDelete) {
                val file = if (fileName.endsWith(".version.txt")) {
                    assetsDir.resolve(fileName)
                } else {
                    geoDir.resolve(fileName)
                }
                file.delete()
                if (!fileName.endsWith(".version.txt")) {
                    val versionFile = routeAssetVersionFile(assetsDir, fileName)
                    if (versionFile.isFile) versionFile.delete()
                    SagerDatabase.assetDao.delete(fileName)
                }
            }
            RouteAssetUpdater.reconfigureUpdater()
        }
    }
}
