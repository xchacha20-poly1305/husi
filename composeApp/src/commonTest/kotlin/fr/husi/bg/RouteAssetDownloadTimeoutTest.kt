package fr.husi.bg

import fr.husi.database.AssetEntity
import fr.husi.test.FakeHTTPRequest
import fr.husi.test.HusiHttpKoinTest
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteAssetDownloadTimeoutTest : HusiHttpKoinTest() {

    private val externalAssetsDir: File = createTempDirectory("husi-route-asset-test").toFile()

    @AfterTest
    fun removeExternalAssetsDir() {
        externalAssetsDir.deleteRecursively()
    }

    @Test
    fun `a custom asset download asks for no overall timeout`() = runTest {
        val asset = AssetEntity(
            name = "geoip.db",
            url = "https://example.invalid/geoip.db",
        )

        updateSingleRouteAsset(asset, externalAssetsDir)

        assertEquals(0, fakeHttp.lastClient?.lastRequest?.timeout)
    }

    @Test
    fun `only rule set downloads drop the overall timeout`() {
        val updater = CustomAssetUpdater(
            versionFiles = routeVersionFiles(externalAssetsDir),
            updateProgress = {},
            cacheDir = externalAssetsDir,
            destinationDir = externalAssetsDir,
            links = emptyList(),
        )

        val versionRequest = updater.newRequest("https://example.invalid/version")
        val downloadRequest = updater.newDownloadRequest("https://example.invalid/rules.tar.gz")

        assertNull((versionRequest as FakeHTTPRequest).timeout)
        assertEquals(0, (downloadRequest as FakeHTTPRequest).timeout)
    }
}
