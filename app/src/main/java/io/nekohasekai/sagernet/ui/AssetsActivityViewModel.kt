package io.nekohasekai.sagernet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.RuleProvider
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.USER_AGENT
import io.nekohasekai.sagernet.ktx.mapX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.CopyCallback
import libcore.HTTPRequest
import libcore.Libcore
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal typealias UpdateProgress = (Int) -> Unit

internal sealed interface AssetEvent {
    class UpdateItem(val asset: File, val state: AssetItemUiState) : AssetEvent
}

sealed interface AssetItemUiState {
    // object Idle : AssetItemUiState
    class Doing(val progress: Int) : AssetItemUiState
    class Done(val e: Exception? = null) : AssetItemUiState
}

internal sealed interface AssetsUiState {
    object Idle : AssetsUiState
    class Doing(val progress: Int) : AssetsUiState
    class Done(val e: Exception? = null) : AssetsUiState
}

internal class AssetsActivityViewModel : ViewModel() {
    private val _uiState: MutableStateFlow<AssetsUiState> = MutableStateFlow(AssetsUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<AssetEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _assets = MutableStateFlow<List<File>>(emptyList())
    val assets = _assets.asStateFlow()

    private lateinit var assetsDir: File
    private lateinit var geoDir: File

    fun initialize(assetsDir: File, geoDir: File) {
        this.assetsDir = assetsDir
        this.geoDir = geoDir
        refreshAssets()
    }

    fun refreshAssets() {
        val assetFiles = mutableListOf<File>()

        assetFiles.add(File(assetsDir, "geoip.version.txt"))
        assetFiles.add(File(assetsDir, "geosite.version.txt"))

        SagerDatabase.assetDao.getAll().forEach {
            assetFiles.add(File(geoDir, it.name))
        }

        _assets.value = assetFiles
    }

    suspend fun deleteAssets(files: List<File>) {
        for (file in files) {
            file.delete()
            val versionFile = File(file.parentFile!!.parentFile!!, "${file.name}.version.txt")
            if (versionFile.isFile) versionFile.delete()
            SagerDatabase.assetDao.delete(file.name)
        }
        refreshAssets()
    }

    fun updateAsset(destinationDir: File, cacheDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateAsset0(destinationDir, cacheDir)
                _uiState.update { AssetsUiState.Done() }
            } catch (e: Exception) {
                _uiState.update { AssetsUiState.Done(e) }
            } finally {
                refreshAssets()
            }
        }
    }

    private suspend fun updateAsset0(destinationDir: File, cacheDir: File) {
        _uiState.update { AssetsUiState.Doing(0) }

        var process = 0
        val updateProgress: UpdateProgress = { p ->
            process += p
            _uiState.update { AssetsUiState.Doing(process) }
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

    fun updateSingleAsset(asset: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateSingleAsset0(asset)
                _uiEvent.emit(AssetEvent.UpdateItem(asset, AssetItemUiState.Done()))
            } catch (e: Exception) {
                _uiEvent.emit(AssetEvent.UpdateItem(asset, AssetItemUiState.Done(e)))
            }
        }
    }

    private suspend fun updateSingleAsset0(asset: File) {
        val name = asset.name
        val entity = SagerDatabase.assetDao.get(name)!!
        val url = entity.url

        _uiEvent.emit(AssetEvent.UpdateItem(asset, AssetItemUiState.Doing(0)))

        Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            useSocks5(DataStore.mixedPort, DataStore.inboundUsername, DataStore.inboundPassword)
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
                    val progress = ((saved / length) * 100).toInt()
                    viewModelScope.launch {
                        _uiEvent.emit(
                            AssetEvent.UpdateItem(
                                asset, AssetItemUiState.Doing(progress)
                            ),
                        )
                    }
                }

            })

        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
        File(assetsDir, "$name.version.txt").writeText(time)
    }

    suspend fun importFile(sourceFile: File, destinationDir: File) {
        try {
            Libcore.tryUnpack(sourceFile.absolutePath, destinationDir.absolutePath)
        } catch (e: Exception) {
            _uiState.update { AssetsUiState.Done(e) }
            return
        } finally {
            sourceFile.delete()
        }

        val assetsDir =
            destinationDir.parentFile ?: throw IllegalStateException("Destination has no parent")
        val nameList = listOf("geosite", "geoip")
        for (name in nameList) {
            val file = File(assetsDir, "$name.version.txt")
            if (file.isFile) file.delete()
            file.createNewFile()
            file.writeText("Custom")
        }

        refreshAssets()
        _uiState.update { AssetsUiState.Done() }
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
        modernTLS()
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

    override suspend fun check(): List<UpdateInfo> = links.mapX { link ->
        UpdateInfo.Custom(link)
    }

    override suspend fun performUpdate(updates: List<UpdateInfo>) {
        val cacheFiles = ArrayList<File>(updates.size)

        try {
            updateProgress(35)
            for ((i, update) in updates.withIndex()) {
                update as UpdateInfo.Custom
                val response = newRequest(update.link).execute()

                val cacheFile = File(cacheDir, "custom_asset_$i.tmp")
                cacheFile.parentFile?.mkdirs()
                cacheFile.deleteOnExit()

                response.writeTo(cacheFile.absolutePath, null)
                cacheFiles.add(cacheFile)
            }

            updateProgress(25)
            for (file in cacheFiles) {
                Libcore.tryUnpack(file.absolutePath, destinationDir.absolutePath)
            }

            updateProgress(25)
            for (version in versionFiles) {
                version.writeText("custom")
            }
            updateProgress(15)
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
                updateProgress(5)
            }
        }
        return updatesNeeded
    }

    override suspend fun performUpdate(updates: List<UpdateInfo>) {
        val cacheFiles = ArrayList<File>(updates.size)
        val progressTotalDownload = 60
        val progressTotalUnpack = 25

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

            if (updates.size == 1 && versionFiles.size > 1) {
                // One repository like Chocolate4U
                val newVersion = (updates[0] as UpdateInfo.Github).newVersion
                versionFiles.forEach { it.writeText(newVersion) }
            } else {
                // Normal situation
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
