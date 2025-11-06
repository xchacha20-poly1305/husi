package io.nekohasekai.sagernet.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.RuleProvider
import io.nekohasekai.sagernet.database.AssetEntity
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.USER_AGENT
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.ktx.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libcore.CopyCallback
import libcore.HTTPRequest
import libcore.Libcore
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal typealias UpdateProgress = (Float) -> Unit

internal data class AssetsUiState(
    val process: Float? = null,
    val assets: List<AssetItem> = emptyList(),
    val pendingDeleteCount: Int = 0,
)

internal data class AssetItem(
    val file: File,
    val version: String,
    val builtIn: Boolean,
    val progress: Float? = null,
)

internal sealed interface AssetsActivityUiEvent {
    class Snackbar(val message: StringOrRes) : AssetsActivityUiEvent
}

internal class AssetsActivityViewModel : ViewModel() {

    companion object {
        fun isBuiltIn(index: Int): Boolean = index < 2
    }

    private val _uiState = MutableStateFlow(AssetsUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<AssetsActivityUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private lateinit var assetsDir: File
    private lateinit var geoDir: File

    private var previousAssetNames = emptySet<String>()

    private var deleteTimer: Job? = null
    private val hiddenAssetsAccess = Mutex()
    private val hiddenAssets = mutableSetOf<String>()

    fun initialize(assetsDir: File, geoDir: File) {
        this.assetsDir = assetsDir
        this.geoDir = geoDir

        viewModelScope.launch {
            SagerDatabase.assetDao.getAll().collect { assets ->
                val currentNames = assets.map { it.name }.toSet()
                val newAssets = currentNames - previousAssetNames

                newAssets.forEach { name ->
                    updateSingleAsset(File(geoDir, name))
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
        val files = buildList {
            add(File(assetsDir, "geoip.version.txt"))
            add(File(assetsDir, "geosite.version.txt"))
            dbAssets.forEach { add(File(geoDir, it.name)) }
        }

        hiddenAssetsAccess.withLock {
            _uiState.update { state ->
                state.copy(
                    assets = files.mapIndexed { i, asset ->
                        buildAssetItem(i, asset)
                    }.filterNot { hiddenAssets.contains(it.file.name) },
                    pendingDeleteCount = hiddenAssets.size,
                    process = null,
                )
            }
        }
    }

    private fun buildAssetItem(index: Int, file: File): AssetItem {
        val isVersionName = file.name.endsWith(".version.txt")
        val versionFile = if (isVersionName) {
            file
        } else {
            File(assetsDir, "${file.name}.version.txt")
        }
        val version = if (versionFile.isFile) {
            versionFile.readText().trim()
        } else {
            versionFile.writeText("Unknown")
            null
        }?.blankAsNull() ?: "Unknown"
        return AssetItem(
            file = file,
            version = version,
            builtIn = isBuiltIn(index),
            progress = null,
        )
    }

    suspend fun deleteAssets(files: List<File>) {
        for (file in files) {
            file.delete()
            val versionFile = File(file.parentFile!!.parentFile!!, "${file.name}.version.txt")
            if (versionFile.isFile) versionFile.delete()
            SagerDatabase.assetDao.delete(file.name)
        }
    }

    fun updateAsset(destinationDir: File, cacheDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateAsset0(destinationDir, cacheDir)
            } catch (e: Exception) {
                Logs.e(e)
                _uiEvent.emit(AssetsActivityUiEvent.Snackbar(StringOrRes.Direct(e.readableMessage)))
            }
            val assets = SagerDatabase.assetDao.getAll().first()
            refreshAssets0(assets)
        }
    }

    private suspend fun updateAsset0(destinationDir: File, cacheDir: File) {
        _uiState.update { it.copy(process = 0f) }

        var process = 0f
        val updateProgress: UpdateProgress = { p ->
            process += p
            _uiState.update { it.copy(process = process) }
        }

        val assetsDir = destinationDir.parentFile!!
        val versionFiles = listOf(
            File(assetsDir, "geoip.version.txt"),
            File(assetsDir, "geosite.version.txt"),
        )
        val provider = DataStore.rulesProvider
        val updater = if (provider == RuleProvider.CUSTOM) {
            CustomAssetUpdater(
                versionFiles,
                updateProgress,
                cacheDir,
                destinationDir,
                DataStore.customRuleProvider.lines(),
            )
        } else GithubAssetUpdater(
            versionFiles,
            updateProgress,
            cacheDir,
            destinationDir,
            when (provider) {
                RuleProvider.OFFICIAL -> listOf("SagerNet/sing-geoip", "SagerNet/sing-geosite")
                RuleProvider.LOYALSOLDIER -> listOf(
                    "xchacha20-poly1305/sing-geoip",
                    "xchacha20-poly1305/sing-geosite",
                )

                RuleProvider.CHOCOLATE4U -> listOf("Chocolate4U/Iran-sing-box-rules")
                else -> throw IllegalStateException("Unknown provider $provider")
            },
            RuleProvider.hasUnstableBranch(provider),
        )

        updater.runUpdateIfAvailable()
    }

    fun updateSingleAsset(asset: File) = viewModelScope.launch(Dispatchers.IO) {
        try {
            updateSingleAsset0(asset)
        } catch (e: Exception) {
            Logs.e(e)
            _uiEvent.emit(AssetsActivityUiEvent.Snackbar(StringOrRes.Direct(e.readableMessage)))
        }
        val assets = SagerDatabase.assetDao.getAll().first()
        refreshAssets0(assets)
    }

    private suspend fun updateSingleAsset0(asset: File) {
        val name = asset.name
        val entity = SagerDatabase.assetDao.get(name)!!
        val url = entity.url

        _uiState.update { state ->
            state.copy(
                assets = state.assets.map {
                    if (it.file == asset) {
                        it.copy(
                            progress = 0f,
                        )
                    } else {
                        it
                    }
                }
            )
        }

        Libcore.newHttpClient().apply {
            keepAlive()
            if (DataStore.serviceState.started) {
                useSocks5(DataStore.mixedPort, DataStore.inboundUsername, DataStore.inboundPassword)
            }
        }.newRequest().apply {
            setURL(url)
            setUserAgent(USER_AGENT)
        }.execute()
            .writeTo(File(geoDir, name).absolutePath, object : CopyCallback {
                var saved: Double = 0.0
                var length: Double = 0.0
                override fun setLength(length: Long) {
                    this.length = length.toDouble()
                }

                override fun update(n: Long) {
                    if (length <= 0) return
                    saved += n.toDouble()
                    val progress = ((saved / length) * 100).toFloat()
                    _uiState.update { state ->
                        state.copy(
                            assets = state.assets.map {
                                if (it.file == asset) {
                                    it.copy(
                                        progress = progress,
                                    )
                                } else {
                                    it
                                }
                            }
                        )
                    }
                }

            })

        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
        File(assetsDir, "$name.version.txt").writeText(time)
    }

    fun importFile(
        contentResolver: ContentResolver,
        uri: Uri?,
        cacheDir: File,
        destinationDir: File,
    ) = viewModelScope.launch(Dispatchers.IO) {
        if (uri == null) return@launch
        val fileName = contentResolver.query(uri, null, null, null, null)
            ?.use { cursor ->
                cursor.moveToFirst()
                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    .let(cursor::getString)
            }
            ?.blankAsNull()
            ?: uri.path
            ?: return@launch

        val tempImportFile = File(cacheDir, fileName).apply {
            parentFile?.mkdirs()
        }
        contentResolver.openInputStream(uri)?.use(tempImportFile.outputStream())
        try {
            Libcore.tryUnpack(tempImportFile.absolutePath, destinationDir.absolutePath)
        } catch (e: Exception) {
            Logs.e(e)
            return@launch
        } finally {
            tempImportFile.delete()
        }

        val assetsDir = destinationDir.parentFile
        val nameList = listOf("geosite", "geoip")
        for (name in nameList) {
            val file = File(assetsDir, "$name.version.txt")
            if (file.isFile) file.delete()
            file.createNewFile()
            file.writeText("Custom")
        }

        refreshAssets()
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
            val toDelete = hiddenAssets.toList()
            hiddenAssets.clear()
            toDelete
        }
        runOnIoDispatcher {
            for (fileName in toDelete) {
                val file = if (fileName.endsWith(".version.txt")) {
                    File(assetsDir, fileName)
                } else {
                    File(geoDir, fileName)
                }
                file.delete()
                if (!fileName.endsWith(".version.txt")) {
                    val versionFile = File(assetsDir, "$fileName.version.txt")
                    if (versionFile.isFile) versionFile.delete()
                    SagerDatabase.assetDao.delete(fileName)
                }
            }
        }
    }

}

internal class NoUpdateException : Exception()

internal sealed class UpdateInfo {
    data class Github(val repo: String, val newVersion: String) : UpdateInfo()
    data class Custom(val link: String) : UpdateInfo()
}

internal abstract class AssetsUpdater(
    val versionFiles: List<File>,
    val updateProgress: UpdateProgress,
    val cacheDir: File,
    val destinationDir: File,
) {
    private val httpClient = Libcore.newHttpClient().apply {
        keepAlive()
        if (DataStore.serviceState.started) {
            useSocks5(DataStore.mixedPort, DataStore.inboundUsername, DataStore.inboundPassword)
        }
    }

    fun newRequest(url: String): HTTPRequest = httpClient.newRequest().apply {
        setURL(url)
        setUserAgent(USER_AGENT)
    }

    suspend fun runUpdateIfAvailable() {
        val updatesToPerform = check()

        if (updatesToPerform.isNotEmpty()) {
            performUpdate(updatesToPerform)
        } else {
            throw NoUpdateException()
        }
    }

    protected abstract suspend fun check(): List<UpdateInfo>

    protected abstract suspend fun performUpdate(updates: List<UpdateInfo>)
}

internal class CustomAssetUpdater(
    versionFiles: List<File>,
    updateProgress: UpdateProgress,
    cacheDir: File,
    destinationDir: File,
    val links: List<String>,
) : AssetsUpdater(versionFiles, updateProgress, cacheDir, destinationDir) {

    override suspend fun check(): List<UpdateInfo> = links.map { link ->
        UpdateInfo.Custom(link)
    }

    override suspend fun performUpdate(updates: List<UpdateInfo>) {
        val cacheFiles = ArrayList<File>(updates.size)

        try {
            updateProgress(35f)
            for ((i, update) in updates.withIndex()) {
                update as UpdateInfo.Custom
                val response = newRequest(update.link).execute()

                val cacheFile = File(cacheDir, "custom_asset_$i.tmp")
                cacheFile.parentFile?.mkdirs()
                cacheFile.deleteOnExit()

                response.writeTo(cacheFile.absolutePath, null)
                cacheFiles.add(cacheFile)
            }

            updateProgress(25f)
            for (file in cacheFiles) {
                Libcore.tryUnpack(file.absolutePath, destinationDir.absolutePath)
            }

            updateProgress(25f)
            for (version in versionFiles) {
                version.writeText("custom")
            }
            updateProgress(15f)
        } finally {
            for (file in cacheFiles) {
                file.runCatching { delete() }
            }
        }
    }
}

internal class GithubAssetUpdater(
    versionFiles: List<File>,
    updateProgress: UpdateProgress,
    parent: File,
    toDir: File,
    val repos: List<String>,
    val unstableBranch: Boolean,
) : AssetsUpdater(versionFiles, updateProgress, parent, toDir) {

    override suspend fun check(): List<UpdateInfo> {
        val updatesNeeded = mutableListOf<UpdateInfo.Github>()

        for ((i, repo) in repos.withIndex()) {
            val latestVersion = fetchVersion(repo)
            val currentVersion =
                versionFiles[i].readText()

            if (latestVersion.isNotEmpty() && latestVersion != currentVersion) {
                updatesNeeded.add(UpdateInfo.Github(repo, latestVersion))
                updateProgress(5f)
            }
        }
        return updatesNeeded
    }

    override suspend fun performUpdate(updates: List<UpdateInfo>) {
        val cacheFiles = ArrayList<File>(updates.size)
        val progressTotalDownload = 60f
        val progressTotalUnpack = 25f

        try {
            val progressPerDownload = progressTotalDownload / updates.size
            for (update in updates) {
                update as UpdateInfo.Github
                // https://codeload.github.com/SagerNet/sing-geosite/tar.gz/refs/heads/rule-set
                var branchName = "rule-set"
                if (unstableBranch && update.repo.endsWith("sing-geosite")) {
                    branchName += "-unstable"
                }
                val url =
                    "https://codeload.github.com/${update.repo}/tar.gz/refs/heads/${branchName}"
                val response = newRequest(url).execute()

                val cacheFile = File(
                    cacheDir,
                    "${update.repo.replace('/', '_')}-${update.newVersion}.tmp"
                )
                cacheFile.parentFile?.mkdirs()
                cacheFile.deleteOnExit()

                response.writeTo(cacheFile.absolutePath, null)
                cacheFiles.add(cacheFile)

                updateProgress(progressPerDownload)
            }

            val progressPerUnpack = progressTotalUnpack / cacheFiles.size
            for (file in cacheFiles) {
                Libcore.untargzWithoutDir(file.absolutePath, destinationDir.absolutePath)
                updateProgress(progressPerUnpack)
            }

            if (repos.size == 1) {
                // Chocolate4U
                val newVersion = (updates.firstOrNull() as? UpdateInfo.Github)?.newVersion ?: return
                versionFiles.forEach { it.writeText(newVersion) }
            } else {
                for (update in updates) {
                    update as UpdateInfo.Github
                    val repoIndex = repos.indexOf(update.repo)
                    if (repoIndex != -1) {
                        versionFiles[repoIndex].writeText(update.newVersion)
                    }
                }
            }
        } finally {
            for (file in cacheFiles) {
                file.runCatching { delete() }
            }
        }
    }

    private fun fetchVersion(repo: String): String {
        val response =
            newRequest("https://api.github.com/repos/$repo/releases/latest").execute()
        return JSONObject(response.contentString.value).optString("tag_name")
    }
}
