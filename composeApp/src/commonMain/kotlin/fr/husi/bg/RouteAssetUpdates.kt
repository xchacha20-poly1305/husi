package fr.husi.bg

import fr.husi.RuleProvider
import fr.husi.database.AssetEntity
import fr.husi.database.DataStore
import fr.husi.ktx.USER_AGENT
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.kxs
import fr.husi.libcore.Libcore
import fr.husi.net.HttpFetchRequest
import fr.husi.net.HttpFetcher
import fr.husi.net.localSocks5Proxy
import fr.husi.net.resolveHttpFetcher
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File
import kotlin.time.Clock

internal typealias UpdateProgress = (Float) -> Unit

private val assetVersionFormat = LocalDateTime.Format {
    year()
    monthNumber()
    day()
    hour()
    minute()
    second()
    secondFraction(fixedLength = 3)
}

const val ROUTE_GEO_DIR_NAME = "geo"

/** Where the bundled/managed rule-set pack lives. May not exist yet. */
internal fun routeGeoDir(externalAssetsDir: File): File {
    return externalAssetsDir.resolve(ROUTE_GEO_DIR_NAME)
}

/** [routeGeoDir], created on the way out, for callers that are about to write into it. */
internal fun createRouteGeoDir(externalAssetsDir: File): File {
    return routeGeoDir(externalAssetsDir).apply {
        mkdirs()
    }
}

const val ROUTE_CUSTOM_GEO_DIR_NAME = "geo-custom"

/** Where user-supplied rule sets (rows in the assets table) live. May not exist yet. */
internal fun routeCustomGeoDir(externalAssetsDir: File): File {
    return externalAssetsDir.resolve(ROUTE_CUSTOM_GEO_DIR_NAME)
}

/** [routeCustomGeoDir], created on the way out, for callers that are about to write into it. */
internal fun createRouteCustomGeoDir(externalAssetsDir: File): File {
    return routeCustomGeoDir(externalAssetsDir).apply {
        mkdirs()
    }
}

internal fun migrateCustomRouteAssets(externalAssetsDir: File, assetNames: List<String>) {
    val geoDir = routeGeoDir(externalAssetsDir)
    val customGeoDir = routeCustomGeoDir(externalAssetsDir)
    var customDirCreated = false
    for (name in assetNames) {
        runCatching {
            val source = geoDir.resolve(name)
            if (!source.isFile) return@runCatching
            val destination = customGeoDir.resolve(name)
            if (destination.exists()) return@runCatching
            if (!customDirCreated) {
                customGeoDir.mkdirs()
                customDirCreated = true
            }
            if (source.renameTo(destination)) return@runCatching
            if (destination.exists()) return@runCatching
            source.copyTo(destination)
            source.delete()
        }
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
    val destinationDir = createRouteGeoDir(externalAssetsDir)
    val versionFiles = routeVersionFiles(externalAssetsDir)
    val provider = DataStore.rulesProvider.get()
    val updater = when (provider) {
        RuleProvider.CUSTOM -> CustomAssetUpdater(
            versionFiles = versionFiles,
            updateProgress = updateProgress,
            cacheDir = cacheDir,
            destinationDir = destinationDir,
            links = DataStore.customRuleProvider.get().lines().filter { it.isNotBlank() },
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
        DataStore.routeAssetsLastUpdated.set(checkedAtSeconds)
    } catch (e: NoUpdateException) {
        DataStore.routeAssetsLastUpdated.set(checkedAtSeconds)
        throw e
    }
}

internal suspend fun updateSingleRouteAsset(
    asset: AssetEntity,
    externalAssetsDir: File,
    updateProgress: UpdateProgress = {},
): String {
    val targetFile = createRouteCustomGeoDir(externalAssetsDir).resolve(asset.name)

    val request = HttpFetchRequest(
        url = asset.url,
        userAgent = USER_AGENT,
        noOverallDeadline = true,
        socks5 = localSocks5Proxy(),
    )
    resolveHttpFetcher().download(request, targetFile) { copiedBytes, contentLength ->
        if (contentLength > 0) {
            updateProgress(copiedBytes * 100f / contentLength)
        }
    }

    val version = currentAssetVersionText()
    routeAssetVersionFile(externalAssetsDir, asset.name).writeText(version)
    return version
}

internal class NoUpdateException : Exception()

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

internal fun githubCodloadTarGzUrl(fullName: String, branchName: String): String =
    "https://codeload.github.com/$fullName/tar.gz/refs/heads/$branchName"

internal sealed class UpdateInfo {
    data class Github(val source: GithubAssetSource, val newVersion: String) : UpdateInfo()
    data class Custom(val link: String) : UpdateInfo()
}

internal abstract class AssetsUpdater(
    val versionFiles: List<File>,
    val updateProgress: UpdateProgress,
    val cacheDir: File,
    val destinationDir: File,
    private val httpFetcher: HttpFetcher = resolveHttpFetcher(),
) {
    suspend fun request(url: String): HttpFetchRequest = HttpFetchRequest(
        url = url,
        userAgent = USER_AGENT,
        socks5 = localSocks5Proxy(),
    )

    /**
     * [request] for a rule set archive download, which must not be cut off by an
     * overall deadline; the small version and API requests keep the default one.
     */
    suspend fun downloadRequest(url: String): HttpFetchRequest =
        request(url).copy(noOverallDeadline = true)

    protected suspend fun fetchString(url: String): String =
        httpFetcher.fetchText(request(url)).content

    protected suspend fun download(url: String, target: File) {
        httpFetcher.download(downloadRequest(url), target)
    }

    suspend fun runUpdateIfAvailable() {
        val updatesToPerform = check()

        if (updatesToPerform.isNotEmpty()) {
            performUpdate(updatesToPerform)
        } else {
            throw NoUpdateException()
        }
    }

    internal abstract suspend fun check(): List<UpdateInfo>

    internal abstract suspend fun performUpdate(updates: List<UpdateInfo>)
}

internal class CustomAssetUpdater(
    versionFiles: List<File>,
    updateProgress: UpdateProgress,
    cacheDir: File,
    destinationDir: File,
    val links: List<String>,
    httpFetcher: HttpFetcher = resolveHttpFetcher(),
) : AssetsUpdater(versionFiles, updateProgress, cacheDir, destinationDir, httpFetcher) {

    override suspend fun check(): List<UpdateInfo> = links.map { link ->
        UpdateInfo.Custom(link)
    }

    override suspend fun performUpdate(updates: List<UpdateInfo>) {
        val cacheFiles = ArrayList<File>(updates.size)

        try {
            updateProgress(35f)
            for ((index, update) in updates.withIndex()) {
                update as UpdateInfo.Custom
                val cacheFile = cacheDir.resolve("custom_asset_$index.tmp")
                cacheFile.parentFile?.mkdirs()
                cacheFile.deleteOnExit()

                download(update.link, cacheFile)
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
    httpFetcher: HttpFetcher = resolveHttpFetcher(),
) : AssetsUpdater(versionFiles, updateProgress, cacheDir, destinationDir, httpFetcher) {

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
                val url = githubCodloadTarGzUrl(source.repository.fullName, branchName)
                val cacheFile = cacheDir.resolve(
                    "${source.repository.fullName.replace('/', '_')}-${update.newVersion}.tmp",
                )
                cacheFile.parentFile?.mkdirs()
                cacheFile.deleteOnExit()

                download(url, cacheFile)
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

    private suspend fun fetchVersion(repository: GithubRepository): String {
        val body = fetchString(githubApiLatestReleaseUrl(repository.fullName))
        return kxs.decodeFromString<GithubRelease>(body).tagName.blankAsNull().orEmpty()
    }
}

internal class GithubReleaseZipUpdater(
    versionFiles: List<File>,
    updateProgress: UpdateProgress,
    cacheDir: File,
    destinationDir: File,
    val source: GithubReleaseSource,
    httpFetcher: HttpFetcher = resolveHttpFetcher(),
) : AssetsUpdater(versionFiles, updateProgress, cacheDir, destinationDir, httpFetcher) {

    override suspend fun check(): List<UpdateInfo> {
        val body = fetchString(githubApiLatestReleaseUrl(source.repository.fullName))
        val latestVersion = kxs.decodeFromString<GithubRelease>(body)
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
        val url = githubReleaseDownloadUrl(source.repository.fullName, tag, source.assetName)
        val cacheFile = cacheDir.resolve("${source.repository.name}-$tag.tmp")
        cacheFile.parentFile?.mkdirs()
        cacheFile.deleteOnExit()
        try {
            updateProgress(10f)
            download(url, cacheFile)
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
