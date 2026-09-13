package fr.husi.bg

import fr.husi.BuildConfig
import fr.husi.HUSI_REPOSITORY
import fr.husi.database.DataStore
import fr.husi.ktx.USER_AGENT
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.kxs
import fr.husi.libcore.HttpClientFactory
import fr.husi.libcore.Libcore
import fr.husi.libcore.resolveHttpClientFactory
import fr.husi.platform.PlatformAbis
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

class AppUpdateChecker(
    private val httpClientFactory: HttpClientFactory = resolveHttpClientFactory(),
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val abis: List<String> = PlatformAbis.supported,
    private val repository: String = HUSI_REPOSITORY,
) {

    suspend fun check(): AppUpdateInfo? {
        val acceptPreRelease = DataStore.appUpdatePreRelease.get()
        val release = fetchRelease(acceptPreRelease) ?: return null
        if (!Libcore.compareSemver(release.tagName, currentVersion)) return null

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
        val url = if (acceptPreRelease) {
            githubApiReleasesUrl(repository)
        } else {
            githubApiLatestReleaseUrl(repository)
        }
        val body = fetchString(url)
        return if (acceptPreRelease) {
            kxs.decodeFromString<List<GithubRelease>>(body).firstOrNull { !it.draft }
        } else {
            kxs.decodeFromString<GithubRelease>(body)
        }
    }

    private suspend fun fetchString(url: String): String {
        val token = DataStore.appUpdateToken.get()
        return httpClientFactory.newHttpClient().apply {
            keepAlive()
            if (DataStore.serviceState.connected) {
                useSocks5(
                    DataStore.mixedPort.get(),
                    DataStore.inboundUsername.get(),
                    DataStore.inboundPassword.get(),
                )
            }
        }.newRequest().apply {
            setURL(url)
            setUserAgent(USER_AGENT)
            token.blankAsNull()?.let {
                setHeader("Authorization", "Bearer $it")
            }
        }.execute().contentString
    }
}
