package fr.husi.bg

import fr.husi.BuildConfig
import fr.husi.HUSI_REPOSITORY
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.ktx.blankAsNull
import fr.husi.net.HTTP_TIMEOUT
import fr.husi.net.HttpRequestOptions
import fr.husi.net.buildUserAgent
import fr.husi.net.localHttpProxy
import fr.husi.net.requireOkBody
import fr.husi.net.withHttpDeadline
import fr.husi.platform.PlatformAbis
import fr.husi.utils.AppVersion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private const val APK_SUFFIX = ".apk"

data class AppUpdateInfo(
    val version: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val downloadUrl: String?,
    val assetName: String?,
    val sizeBytes: Long,
    val sha256: String? = null,
)

fun todayEpochDay(): Long = Clock.System.now()
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .date
    .toEpochDays()

fun shouldAutoCheck(
    enabled: Boolean,
    onlyWhenConnected: Boolean,
    connected: Boolean,
    lastCheckEpochDay: Long,
    todayEpochDay: Long,
): Boolean {
    if (!enabled) return false
    if (onlyWhenConnected && !connected) return false
    return lastCheckEpochDay != todayEpochDay
}

fun interface AppUpdateFetcher {
    suspend fun check(): AppUpdateInfo?
}

class AppUpdateAutoChecker(
    private val fetcher: AppUpdateFetcher = AppUpdateFetcher { AppUpdateChecker().check() },
    private val isSupported: Boolean = AppUpdateInstaller.isSupported,
    private val today: () -> Long = ::todayEpochDay,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun checkIfDue(connected: Boolean): AppUpdateInfo? {
        if (!isSupported) return null

        val todayEpochDay = today()
        val due = shouldAutoCheck(
            enabled = DataStore.appUpdateAutoCheck.get(),
            onlyWhenConnected = DataStore.appUpdateOnlyWhenConnected.get(),
            connected = connected,
            lastCheckEpochDay = DataStore.appUpdateLastCheckEpochDay.get(),
            todayEpochDay = todayEpochDay,
        )
        if (!due) return null

        val found = try {
            withContext(ioDispatcher) { fetcher.check() }
        } catch (e: Exception) {
            Logs.e("check app update", e)
            return null
        }
        DataStore.appUpdateLastCheckEpochDay.set(todayEpochDay)
        if (found == null) return null

        if (found.version == DataStore.appUpdateSkippedVersion.get()) return null
        return found
    }
}

private val ABI_PREFERENCE = listOf("x86_64", "x86", "arm64-v8a", "armeabi-v7a")

/**
 * For emulator on x86_64: arm64v8a libcore can't run. So prefer x86_64 if it is based on x86_64.
 */
internal fun sortAbisByPreference(abis: List<String>): List<String> = abis.sortedBy {
    ABI_PREFERENCE.indexOf(it).takeIf { rank -> rank >= 0 } ?: ABI_PREFERENCE.size
}

internal fun selectApkAsset(
    assets: List<GithubReleaseAsset>,
    abis: List<String>,
): GithubReleaseAsset? {
    val installable = assets.filter {
        it.name.endsWith(APK_SUFFIX)
    }
    for (abi in sortAbisByPreference(abis)) {
        installable.firstOrNull { it.name.endsWith("-$abi$APK_SUFFIX") }?.let {
            return it
        }
    }
    return null
}

class AppUpdateChecker internal constructor(
    private val gitHubApi: GitHubApi = resolveGitHubApi(),
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val abis: List<String> = PlatformAbis.supported,
    private val repository: String = HUSI_REPOSITORY,
) {
    constructor() : this(gitHubApi = resolveGitHubApi())

    suspend fun check(): AppUpdateInfo? {
        val acceptPreRelease = DataStore.appUpdatePreRelease.get()
        val release = fetchRelease(acceptPreRelease) ?: return null
        val latestVersion = AppVersion.parse(release.tagName) ?: return null
        val installedVersion = AppVersion.parse(currentVersion) ?: return null
        if (latestVersion <= installedVersion) return null

        val asset = selectApkAsset(release.assets, abis)
        return AppUpdateInfo(
            version = release.tagName,
            releaseNotes = release.body,
            releaseUrl = release.htmlUrl.ifEmpty { githubReleasesPageUrl(repository) },
            downloadUrl = asset?.browserDownloadUrl,
            assetName = asset?.name,
            sizeBytes = asset?.size ?: 0L,
            sha256 = asset?.sha256Hex(),
        )
    }

    private suspend fun fetchRelease(acceptPreRelease: Boolean): GithubRelease? {
        val (owner, repo) = repository.split('/', limit = 2)
        val userAgent = buildUserAgent()
        val authorization = DataStore.appUpdateToken.get().blankAsNull()?.let { "Bearer $it" }
        val options = HttpRequestOptions(proxy = localHttpProxy())
        return if (acceptPreRelease) {
            withHttpDeadline(HTTP_TIMEOUT) {
                gitHubApi.releases(
                    owner = owner,
                    repo = repo,
                    userAgent = userAgent,
                    authorization = authorization,
                    options = options,
                )
            }.requireOkBody().firstOrNull {
                !it.draft && !it.tagName.startsWith(PLUGIN_RELEASE_TAG_PREFIX)
            }
        } else {
            withHttpDeadline(HTTP_TIMEOUT) {
                gitHubApi.latestRelease(
                    owner = owner,
                    repo = repo,
                    userAgent = userAgent,
                    authorization = authorization,
                    options = options,
                )
            }.requireOkBody()
        }
    }
}
