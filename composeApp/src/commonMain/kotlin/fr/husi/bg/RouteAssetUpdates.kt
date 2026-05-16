package fr.husi.bg

import fr.husi.RuleProvider
import fr.husi.database.AssetEntity
import fr.husi.database.DataStore
import fr.husi.ktx.USER_AGENT
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.kxs
import fr.husi.libcore.CopyCallback
import fr.husi.libcore.HTTPRequest
import fr.husi.libcore.Libcore
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.time.Clock

internal typealias UpdateProgress = (Float) -> Unit

@OptIn(FormatStringsInDatetimeFormats::class)
private val assetVersionFormat = LocalDateTime.Format {
    byUnicodePattern("yyyyMMddHHmmssSSS")
}

internal fun routeGeoDir(externalAssetsDir: File): File {
    return externalAssetsDir.resolve("geo").apply {
        mkdirs()
    }
}

internal fun routeVersionFiles(externalAssetsDir: File): List<File> {
    return listOf(
        externalAssetsDir.resolve("geoip.version.txt"),
        externalAssetsDir.resolve("geosite.version.txt"),
    )
}

internal fun routeAssetVersionFile(externalAssetsDir: File, assetName: String): File {
    return externalAssetsDir.resolve("$assetName.version.txt")
}

internal fun currentAssetVersionText(): String {
    return assetVersionFormat.format(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    )
}

internal suspend fun updateManagedRouteAssets(
    externalAssetsDir: File,
    cacheDir: File,
    checkedAtSeconds: Long = currentEpochSeconds(),
    updateProgress: UpdateProgress = {},
) {
    val destinationDir = routeGeoDir(externalAssetsDir)
    val versionFiles = routeVersionFiles(externalAssetsDir)
    val provider = DataStore.rulesProvider
    val updater = when (provider) {
        RuleProvider.CUSTOM -> CustomAssetUpdater(
            versionFiles = versionFiles,
            updateProgress = updateProgress,
            cacheDir = cacheDir,
            destinationDir = destinationDir,
            links = DataStore.customRuleProvider.lines().filter { it.isNotBlank() },
        )
        RuleProvider.RUNETFREEDOM -> GithubReleaseZipUpdater(
            versionFiles = versionFiles,
            updateProgress = updateProgress,
            cacheDir = cacheDir,
            destinationDir = destinationDir,
            source = GithubReleaseSource(
                repository = GithubRepository(
                    author = "runetfreedom",
                    name = "russia-v2ray-rules-dat",
                ),
                assetName = "sing-box.zip",
                versionFile = versionFiles[0],
            ),
        )
        else -> GithubAssetUpdater(
            versionFiles = versionFiles,
            updateProgress = updateProgress,
            cacheDir = cacheDir,
            destinationDir = destinationDir,
            sources = buildGithubAssetSources(provider, versionFiles),
            useUnstableBranch = RuleProvider.hasUnstableBranch(provider),
        )
    }

    try {
        updater.runUpdateIfAvailable()
        DataStore.routeAssetsLastUpdated = checkedAtSeconds
    } catch (e: NoUpdateException) {
        DataStore.routeAssetsLastUpdated = checkedAtSeconds
        throw e
    }
}

internal suspend fun updateSingleRouteAsset(
    asset: AssetEntity,
    externalAssetsDir: File,
    updateProgress: UpdateProgress = {},
): String {
    val targetFile = routeGeoDir(externalAssetsDir).resolve(asset.name)

    Libcore.newHttpClient().apply {
        keepAlive()
        if (DataStore.serviceState.connected) {
            useSocks5(DataStore.mixedPort, DataStore.inboundUsername, DataStore.inboundPassword)
        }
    }.newRequest().apply {
        setURL(asset.url)
        setUserAgent(USER_AGENT)
    }.execute().writeTo(
        targetFile.absolutePath,
        object : CopyCallback {
            private var saved = 0.0
            private var length = 0.0

            override fun setLength(length: Long) {
                this.length = length.toDouble()
            }

            override fun update(n: Long) {
                if (length <= 0) return
                saved += n.toDouble()
                updateProgress(((saved / length) * 100.0).toFloat())
            }
        },
    )

    val version = currentAssetVersionText()
    routeAssetVersionFile(externalAssetsDir, asset.name).writeText(version)
    return version
}

internal class NoUpdateException : Exception()

@Serializable
private data class GithubRelease(
    @SerialName("tag_name")
    val tagName: String = "",
)

internal data class GithubRepository(
    val author: String,
    val name: String,
    val branch: String = "rule-set",
    val unstableBranch: String? = null,
) {
    val fullName: String
        get() = "$author/$name"

    fun resolveBranch(useUnstableBranch: Boolean): String {
        return if (useUnstableBranch && unstableBranch != null) {
            unstableBranch
        } else {
            branch
        }
    }
}

internal data class GithubAssetSource(
    val repository: GithubRepository,
    val versionFile: File,
)

internal data class GithubReleaseSource(
    val repository: GithubRepository,
    val assetName: String,
    val versionFile: File,
)

internal fun buildGithubAssetSources(provider: Int, versionFiles: List<File>): List<GithubAssetSource> {
    return when (provider) {
        RuleProvider.OFFICIAL -> listOf(
            GithubAssetSource(
                repository = GithubRepository(
                    author = "SagerNet",
                    name = "sing-geoip",
                ),
                versionFile = versionFiles[0],
            ),
            GithubAssetSource(
                repository = GithubRepository(
                    author = "SagerNet",
                    name = "sing-geosite",
                    unstableBranch = "rule-set-unstable",
                ),
                versionFile = versionFiles[1],
            ),
        )

        RuleProvider.LOYALSOLDIER -> listOf(
            GithubAssetSource(
                repository = GithubRepository(
                    author = "1715173329",
                    name = "sing-geoip",
                ),
                versionFile = versionFiles[0],
            ),
            GithubAssetSource(
                repository = GithubRepository(
                    author = "1715173329",
                    name = "sing-geosite",
                    unstableBranch = "rule-set-unstable",
                ),
                versionFile = versionFiles[1],
            ),
        )

        RuleProvider.CHOCOLATE4U -> listOf(
            GithubAssetSource(
                repository = GithubRepository(
                    author = "Chocolate4U",
                    name = "Iran-sing-box-rules",
                ),
                versionFile = versionFiles[0],
            ),
        )

        else -> throw IllegalStateException("Unknown provider $provider")
    }
}

internal sealed class UpdateInfo {
    data class Github(val source: GithubAssetSource, val newVersion: String) : UpdateInfo()
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
        if (DataStore.serviceState.connected) {
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
            for ((index, update) in updates.withIndex()) {
                update as UpdateInfo.Custom
                val response = newRequest(update.link).execute()

                val cacheFile = cacheDir.resolve("custom_asset_$index.tmp")
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
            for (versionFile in versionFiles) {
                versionFile.writeText("custom")
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
    cacheDir: File,
    destinationDir: File,
    val sources: List<GithubAssetSource>,
    val useUnstableBranch: Boolean,
) : AssetsUpdater(versionFiles, updateProgress, cacheDir, destinationDir) {

    override suspend fun check(): List<UpdateInfo> {
        val updatesNeeded = mutableListOf<UpdateInfo.Github>()

        for (source in sources) {
            val latestVersion = fetchVersion(source.repository)
            val currentVersion = source.versionFile.takeIf(File::isFile)
                ?.readText()
                ?.trim()
                .orEmpty()

            if (latestVersion.isNotEmpty() && latestVersion != currentVersion) {
                updatesNeeded.add(UpdateInfo.Github(source, latestVersion))
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
                val source = update.source
                val branchName = source.repository.resolveBranch(useUnstableBranch)
                val url =
                    "https://codeload.github.com/${source.repository.fullName}/tar.gz/refs/heads/$branchName"
                val response = newRequest(url).execute()

                val cacheFile = cacheDir.resolve(
                    "${source.repository.fullName.replace('/', '_')}-${update.newVersion}.tmp",
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

            if (sources.size == 1) {
                val newVersion = (updates.firstOrNull() as? UpdateInfo.Github)?.newVersion ?: return
                versionFiles.forEach { it.writeText(newVersion) }
            } else {
                for (update in updates) {
                    update as UpdateInfo.Github
                    update.source.versionFile.writeText(update.newVersion)
                }
            }
        } finally {
            for (file in cacheFiles) {
                file.runCatching { delete() }
            }
        }
    }

    private fun fetchVersion(repository: GithubRepository): String {
        val response =
            newRequest("https://api.github.com/repos/${repository.fullName}/releases/latest").execute()
        return kxs.decodeFromString<GithubRelease>(response.contentString).tagName.blankAsNull().orEmpty()
    }
}

internal class GithubReleaseZipUpdater(
    versionFiles: List<File>,
    updateProgress: UpdateProgress,
    cacheDir: File,
    destinationDir: File,
    val source: GithubReleaseSource,
) : AssetsUpdater(versionFiles, updateProgress, cacheDir, destinationDir) {

    override suspend fun check(): List<UpdateInfo> {
        val response = newRequest(
            "https://api.github.com/repos/${source.repository.fullName}/releases/latest",
        ).execute()
        val latestVersion = kxs.decodeFromString<GithubRelease>(response.contentString)
            .tagName.blankAsNull().orEmpty()
        val currentVersion = source.versionFile.takeIf(File::isFile)
            ?.readText()?.trim().orEmpty()
        return if (latestVersion.isNotEmpty() && latestVersion != currentVersion) {
            listOf(UpdateInfo.Github(GithubAssetSource(source.repository, source.versionFile), latestVersion))
        } else emptyList()
    }

    override suspend fun performUpdate(updates: List<UpdateInfo>) {
        val update = updates.firstOrNull() as? UpdateInfo.Github ?: return
        val tag = update.newVersion
        val url = "https://github.com/${source.repository.fullName}/releases/download/$tag/${source.assetName}"
        val cacheFile = cacheDir.resolve("${source.repository.name}-$tag.tmp")
        cacheFile.parentFile?.mkdirs()
        cacheFile.deleteOnExit()
        try {
            updateProgress(10f)
            newRequest(url).execute().writeTo(cacheFile.absolutePath, null)
            updateProgress(60f)
            Libcore.tryUnpack(cacheFile.absolutePath, destinationDir.absolutePath)
            updateProgress(25f)
            versionFiles.forEach { it.writeText(tag) }
            updateProgress(5f)
        } finally {
            cacheFile.runCatching { delete() }
        }
    }
}
