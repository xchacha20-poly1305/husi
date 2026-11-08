package fr.husi.bg

internal fun githubRepositoryUrl(fullName: String): String = "https://github.com/$fullName"

internal fun githubReleasesPageUrl(fullName: String): String =
    "${githubRepositoryUrl(fullName)}/releases"

internal fun githubLatestReleasePageUrl(fullName: String): String =
    "${githubReleasesPageUrl(fullName)}/latest"
