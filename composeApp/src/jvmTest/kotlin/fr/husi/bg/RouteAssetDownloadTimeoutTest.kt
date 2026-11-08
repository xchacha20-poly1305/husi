package fr.husi.bg

import fr.husi.database.AssetEntity
import fr.husi.test.HusiHttpKoinTest
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteAssetDownloadTimeoutTest : HusiHttpKoinTest() {

    private val externalAssetsDir: File = createTempDirectory("husi-route-asset-test").toFile()

    @AfterTest
    fun removeExternalAssetsDir() {
        externalAssetsDir.deleteRecursively()
    }

    @Test
    fun `a custom asset download asks for no overall deadline`() = runTest {
        val asset = AssetEntity(
            name = "geoip.db",
            url = "https://example.invalid/geoip.db",
        )

        updateSingleRouteAsset(asset, externalAssetsDir)

        assertEquals(true, fakeHttp.lastRequest?.noOverallDeadline)
    }

    @Test
    fun `only rule set downloads drop the overall deadline`() = runTest {
        val updater = CustomAssetUpdater(
            versionFiles = routeVersionFiles(externalAssetsDir),
            updateProgress = {},
            cacheDir = externalAssetsDir,
            destinationDir = externalAssetsDir,
            links = emptyList(),
        )

        val versionRequest = updater.request("https://example.invalid/version")
        val downloadRequest = updater.downloadRequest("https://example.invalid/rules.tar.gz")

        assertFalse(versionRequest.noOverallDeadline)
        assertTrue(downloadRequest.noOverallDeadline)
    }
}
