package fr.husi.bg

import fr.husi.ktx.blankAsNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val RELEASE_LIST_PAGE_SIZE = 10

@Serializable
internal data class GithubRelease(
    @SerialName("tag_name")
    val tagName: String = "",
    val body: String = "",
    val draft: Boolean = false,
    @SerialName("html_url")
    val htmlUrl: String = "",
    val assets: List<GithubReleaseAsset> = emptyList(),
)

@Serializable
internal data class GithubReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
    val size: Long = 0L,
    val digest: String? = null,
)

private const val SHA256_DIGEST_PREFIX = "sha256:"

internal fun GithubReleaseAsset.sha256Hex(): String? = digest
    ?.takeIf { it.startsWith(SHA256_DIGEST_PREFIX) }
    ?.removePrefix(SHA256_DIGEST_PREFIX)
    ?.lowercase()
    ?.blankAsNull()

internal fun githubApiLatestReleaseUrl(fullName: String): String =
    "https://api.github.com/repos/$fullName/releases/latest"

internal fun githubApiReleasesUrl(fullName: String): String =
    "https://api.github.com/repos/$fullName/releases?per_page=$RELEASE_LIST_PAGE_SIZE"

internal fun githubReleaseDownloadUrl(fullName: String, tag: String, assetName: String): String =
    "https://github.com/$fullName/releases/download/$tag/$assetName"

internal fun githubReleasesPageUrl(fullName: String): String =
    "https://github.com/$fullName/releases"

internal fun githubLatestReleasePageUrl(fullName: String): String =
    "${githubReleasesPageUrl(fullName)}/latest"
