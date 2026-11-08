package fr.husi.bg

import fr.husi.HUSI_REPOSITORY
import fr.husi.ktx.urlSafe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val OBTAINIUM_SCHEME = "obtainium"

private const val OBTAINIUM_ADD_APP_PREFIX = "$OBTAINIUM_SCHEME://app/"

private const val NON_PLUGIN_RELEASE_TITLE_PATTERN = "^(?!$PLUGIN_RELEASE_TAG_PREFIX)"

fun obtainiumAddAppLink(packageName: String,includePreReleases: Boolean): String {
    val additionalSettings = buildJsonObject {
        put("filterReleaseTitlesByRegEx", NON_PLUGIN_RELEASE_TITLE_PATTERN)
        put("includePrereleases", includePreReleases)
    }
    val app = buildJsonObject {
        put("id", packageName)
        put("url", githubRepositoryUrl(HUSI_REPOSITORY))
        put("author", HUSI_REPOSITORY.substringBefore('/'))
        put("name", HUSI_REPOSITORY.substringAfter('/'))
        put("preferredApkIndex", 0)
        put("additionalSettings", Json.encodeToString(additionalSettings))
    }
    return OBTAINIUM_ADD_APP_PREFIX + Json.encodeToString(app).urlSafe()
}
