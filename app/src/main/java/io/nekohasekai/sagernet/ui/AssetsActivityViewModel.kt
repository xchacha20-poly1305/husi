package io.nekohasekai.sagernet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.RuleProvider
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.USER_AGENT
import io.nekohasekai.sagernet.ktx.mapX
import io.nekohasekai.sagernet.ktx.readableMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.HTTPRequest
import libcore.Libcore
import org.json.JSONObject
import java.io.File

internal typealias UpdateProgress = (Int) -> Unit

internal sealed class AssetsUiState {
    object Idle : AssetsUiState()
    class Doing(val progress: Int) : AssetsUiState()
    class Done(val e: Exception? = null) : AssetsUiState()
}

internal class AssetsActivityViewModel : ViewModel() {
    private val _uiState: MutableStateFlow<AssetsUiState> = MutableStateFlow(AssetsUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun updateAsset(destinationDir: File, cacheDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateAsset0(destinationDir, cacheDir)
                _uiState.update { AssetsUiState.Done() }
            } catch (e: Exception) {
                _uiState.update { AssetsUiState.Done(e) }
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
            File(assetsDir, "geosite.version.txt")
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
                    "xchacha20-poly1305/sing-geosite"
                )

                RuleProvider.CHOCOLATE4U -> listOf("Chocolate4U/Iran-sing-box-rules")
                else -> throw IllegalStateException("Unknown provider $provider")
            },
            RuleProvider.hasUnstableBranch(provider),
        )

        updater.runUpdateIfAvailable()
    }


    suspend fun importFile(destinationDir: File, sourceFile: File) {
        try {
            tryOpenCompressed(sourceFile.absolutePath, destinationDir.absolutePath)
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
                tryOpenCompressed(file.absolutePath, destinationDir.absolutePath)
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

            for (update in updates) {
                update as UpdateInfo.Github
                val repoIndex = repos.indexOf(update.repo)
                if (repoIndex != -1) {
                    versionFiles[repoIndex].writeText(update.newVersion)
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

private suspend fun tryOpenCompressed(from: String, toDir: String) {
    val exceptions = mutableListOf<Throwable>()
    runCatching {
        Libcore.untargzWithoutDir(from, toDir)
    }.onSuccess { return }
        .onFailure { exceptions += it }
    runCatching {
        Libcore.unzipWithoutDir(from, toDir)
    }.onSuccess { return }
        .onFailure { exceptions += it }
    error(exceptions.joinToString("; ") { it.readableMessage })
}
