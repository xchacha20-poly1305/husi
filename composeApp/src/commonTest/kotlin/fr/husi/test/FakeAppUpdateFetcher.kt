package fr.husi.test

import fr.husi.bg.AppUpdateFetcher
import fr.husi.bg.AppUpdateInfo

class FakeAppUpdateFetcher(
    var nextResult: AppUpdateInfo? = null,
    var nextThrowable: Throwable? = null,
) : AppUpdateFetcher {

    var checkCount = 0
        private set

    override suspend fun check(): AppUpdateInfo? {
        checkCount++
        nextThrowable?.let { throw it }
        return nextResult
    }
}

fun appUpdateInfo(version: String = "9.9.9") = AppUpdateInfo(
    version = version,
    releaseNotes = "release notes",
    releaseUrl = "https://example.invalid/$version",
    downloadUrl = "https://example.invalid/husi-$version-arm64-v8a.apk",
    assetName = "husi-$version-arm64-v8a.apk",
    sizeBytes = 128L,
)
