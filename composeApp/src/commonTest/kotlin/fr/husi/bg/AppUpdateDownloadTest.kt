package fr.husi.bg

import fr.husi.ktx.sha256Hex
import fr.husi.test.HusiHttpKoinTest
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ASSET_NAME = "husi-9.9.9-arm64-v8a.apk"

private const val ASSET_SIZE = 4096L

private const val ASSET_SHA256 =
    "ad7facb2586fc6e966c004d7d1d16b024f5805ff7cb47c7a85dabd8b48892ca7"

class AppUpdateDownloadTest : HusiHttpKoinTest() {

    private val cacheDir: File = createTempDirectory("husi-update-test").toFile()

    @AfterTest
    fun removeCacheDir() {
        cacheDir.deleteRecursively()
    }

    private fun info(sizeBytes: Long = ASSET_SIZE, sha256: String? = null) = AppUpdateInfo(
        version = "9.9.9",
        releaseNotes = "release notes",
        releaseUrl = "https://example.invalid/9.9.9",
        downloadUrl = "https://example.invalid/$ASSET_NAME",
        assetName = ASSET_NAME,
        sizeBytes = sizeBytes,
        sha256 = sha256,
    )

    @Test
    fun `download keeps a package whose size matches the release asset`() = runTest {
        fakeHttp.nextDownloadBytes = ASSET_SIZE

        val apk = downloadAppUpdate(info(), cacheDir, fakeHttp)

        assertEquals(ASSET_NAME, apk.name)
        assertEquals(ASSET_SIZE, apk.length())
    }

    @Test
    fun `download rejects and deletes a truncated package`() = runTest {
        fakeHttp.nextDownloadBytes = ASSET_SIZE
        fakeHttp.nextWrittenBytes = ASSET_SIZE / 2

        assertFailsWith<AppUpdateSizeMismatch> {
            downloadAppUpdate(info(), cacheDir, fakeHttp)
        }

        assertFalse(appUpdateCacheDir(cacheDir).resolve(ASSET_NAME).exists())
    }

    @Test
    fun `download rejects an empty package when the release size is unknown`() = runTest {
        fakeHttp.nextDownloadBytes = 0L

        assertFailsWith<AppUpdateSizeMismatch> {
            downloadAppUpdate(info(sizeBytes = 0L), cacheDir, fakeHttp)
        }
    }

    @Test
    fun `download accepts any non empty package when the release size is unknown`() = runTest {
        fakeHttp.nextDownloadBytes = ASSET_SIZE

        val apk = downloadAppUpdate(info(sizeBytes = 0L), cacheDir, fakeHttp)

        assertEquals(ASSET_SIZE, apk.length())
    }

    @Test
    fun `download clears the cache directory before writing`() = runTest {
        val stale = appUpdateCacheDir(cacheDir).also { it.mkdirs() }.resolve("husi-1.0.0-x86.apk")
        stale.writeBytes(ByteArray(8))
        val partial = appUpdateCacheDir(cacheDir).resolve(ASSET_NAME)
        partial.writeBytes(ByteArray(8))
        fakeHttp.nextDownloadBytes = ASSET_SIZE

        val apk = downloadAppUpdate(info(), cacheDir, fakeHttp)

        assertFalse(stale.exists())
        assertTrue(apk.exists())
        assertEquals(ASSET_SIZE, apk.length())
    }

    @Test
    fun `download keeps a package whose sha256 matches the release asset digest`() = runTest {
        fakeHttp.nextDownloadBytes = ASSET_SIZE

        val apk = downloadAppUpdate(info(sha256 = ASSET_SHA256), cacheDir, fakeHttp)

        assertEquals(ASSET_SHA256, apk.sha256Hex())
    }

    @Test
    fun `download rejects and deletes a package whose sha256 differs`() = runTest {
        fakeHttp.nextDownloadBytes = ASSET_SIZE

        assertFailsWith<AppUpdateChecksumMismatch> {
            downloadAppUpdate(info(sha256 = "0".repeat(ASSET_SHA256.length)), cacheDir, fakeHttp)
        }

        assertFalse(appUpdateCacheDir(cacheDir).resolve(ASSET_NAME).exists())
    }

    @Test
    fun `download reports progress against the release size when the server sends no length`() =
        runTest {
            fakeHttp.nextDownloadBytes = 0L
            fakeHttp.nextWrittenBytes = ASSET_SIZE
            fakeHttp.nextChunkCount = 1
            val progress = mutableListOf<Float>()

            downloadAppUpdate(info(), cacheDir, fakeHttp, updateProgress = { progress.add(it) })

            assertEquals(listOf(100f), progress)
        }
}
