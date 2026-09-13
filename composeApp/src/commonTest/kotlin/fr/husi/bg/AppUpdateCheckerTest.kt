package fr.husi.bg

import fr.husi.database.DataStore
import fr.husi.test.HusiHttpKoinTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TEST_REPOSITORY = "xchacha20-poly1305/husi"

private const val ASSET_SHA256 =
    "ad7facb2586fc6e966c004d7d1d16b024f5805ff7cb47c7a85dabd8b48892ca7"

class AppUpdateCheckerTest : HusiHttpKoinTest() {

    private fun release(
        tag: String,
        assets: List<String> = listOf(
            "husi-9.9.9-arm64-v8a.apk",
            "husi-9.9.9-armeabi-v7a.apk",
            "husi-9.9.9-x86_64.apk",
            "husi-9.9.9-x86.apk",
        ),
        digest: String? = "sha256:$ASSET_SHA256",
    ): String {
        val digestJson = digest?.let { ""","digest":"$it"""" }.orEmpty()
        val assetsJson = assets.joinToString(",") { name ->
            """{"name":"$name","browser_download_url":"https://example.invalid/$name",""" +
                """"size":128$digestJson}"""
        }
        return """
            {
              "tag_name": "$tag",
              "body": "release notes",
              "draft": false,
              "html_url": "https://example.invalid/$tag",
              "assets": [$assetsJson]
            }
        """.trimIndent()
    }

    private fun checker(
        currentVersion: String = "1.0.0",
        abis: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
    ) = AppUpdateChecker(
        httpClientFactory = fakeHttp,
        currentVersion = currentVersion,
        abis = abis,
        repository = TEST_REPOSITORY,
    )

    // region shouldAutoCheck

    @Test
    fun `shouldAutoCheck is false when disabled`() {
        assertFalse(
            shouldAutoCheck(
                enabled = false,
                onlyWhenConnected = false,
                connected = true,
                lastCheckEpochDay = 0L,
                todayEpochDay = 100L,
            ),
        )
    }

    @Test
    fun `shouldAutoCheck is false when the proxy is required but down`() {
        assertFalse(
            shouldAutoCheck(
                enabled = true,
                onlyWhenConnected = true,
                connected = false,
                lastCheckEpochDay = 0L,
                todayEpochDay = 100L,
            ),
        )
    }

    @Test
    fun `shouldAutoCheck is false on the same calendar day`() {
        assertFalse(
            shouldAutoCheck(
                enabled = true,
                onlyWhenConnected = false,
                connected = false,
                lastCheckEpochDay = 100L,
                todayEpochDay = 100L,
            ),
        )
    }

    @Test
    fun `shouldAutoCheck is true on a new calendar day`() {
        assertTrue(
            shouldAutoCheck(
                enabled = true,
                onlyWhenConnected = false,
                connected = false,
                lastCheckEpochDay = 99L,
                todayEpochDay = 100L,
            ),
        )
    }

    @Test
    fun `shouldAutoCheck ignores elapsed hours within a calendar day`() {
        assertTrue(
            shouldAutoCheck(
                enabled = true,
                onlyWhenConnected = true,
                connected = true,
                lastCheckEpochDay = 100L,
                todayEpochDay = 101L,
            ),
        )
    }

    // endregion

    // region selectApkAsset

    @Test
    fun `sortAbisByPreference ranks x86 above arm`() {
        assertEquals(
            listOf("x86_64", "x86", "arm64-v8a", "armeabi-v7a"),
            sortAbisByPreference(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")),
        )
    }

    @Test
    fun `sortAbisByPreference keeps unknown abis last in device order`() {
        assertEquals(
            listOf("arm64-v8a", "riscv64", "mips"),
            sortAbisByPreference(listOf("riscv64", "mips", "arm64-v8a")),
        )
    }

    @Test
    fun `selectApkAsset prefers x86_64 over an arm build the device also runs`() {
        val assets = listOf(
            GithubReleaseAsset("husi-9.9.9-arm64-v8a.apk", "https://example.invalid/a", 1L),
            GithubReleaseAsset("husi-9.9.9-x86_64.apk", "https://example.invalid/b", 2L),
        )
        val selected = selectApkAsset(assets, listOf("arm64-v8a", "x86_64"))
        assertEquals("husi-9.9.9-x86_64.apk", selected?.name)
    }

    @Test
    fun `selectApkAsset never picks an abi the device does not support`() {
        val assets = listOf(
            GithubReleaseAsset("husi-9.9.9-x86_64.apk", "https://example.invalid/a", 1L),
            GithubReleaseAsset("husi-9.9.9-arm64-v8a.apk", "https://example.invalid/b", 2L),
        )
        val selected = selectApkAsset(assets, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("husi-9.9.9-arm64-v8a.apk", selected?.name)
    }

    @Test
    fun `selectApkAsset prefers arm64 over armeabi`() {
        val assets = listOf(
            GithubReleaseAsset("husi-9.9.9-armeabi-v7a.apk", "https://example.invalid/a", 1L),
            GithubReleaseAsset("husi-9.9.9-arm64-v8a.apk", "https://example.invalid/b", 2L),
        )
        val selected = selectApkAsset(assets, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("husi-9.9.9-arm64-v8a.apk", selected?.name)
    }

    @Test
    fun `selectApkAsset does not confuse x86 with x86_64`() {
        val assets = listOf(
            GithubReleaseAsset("husi-9.9.9-x86_64.apk", "https://example.invalid/a", 1L),
        )
        assertNull(selectApkAsset(assets, listOf("x86")))
    }

    @Test
    fun `selectApkAsset ignores non apk assets`() {
        val assets = listOf(
            GithubReleaseAsset("husi-9.9.9-arm64-v8a.apk.sha256", "https://example.invalid/a", 1L),
        )
        assertNull(selectApkAsset(assets, listOf("arm64-v8a")))
    }

    // endregion

    // region check

    @Test
    fun `check requests the latest release when pre-releases are off`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeHttp.nextResponseContent = release("9.9.9").encodeToByteArray()

        checker().check()

        assertEquals(
            githubApiLatestReleaseUrl(TEST_REPOSITORY),
            fakeHttp.lastClient?.lastRequest?.url,
        )
    }

    @Test
    fun `check requests the release list when pre-releases are on`() = runTest {
        DataStore.appUpdatePreRelease.set(true)
        fakeHttp.nextResponseContent = "[${release("9.9.9")}]".encodeToByteArray()

        checker().check()

        assertEquals(
            githubApiReleasesUrl(TEST_REPOSITORY),
            fakeHttp.lastClient?.lastRequest?.url,
        )
    }

    @Test
    fun `check sends the github token as a bearer header`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        DataStore.appUpdateToken.set("secret-token")
        fakeHttp.nextResponseContent = release("9.9.9").encodeToByteArray()

        checker().check()

        assertEquals(
            "Bearer secret-token",
            fakeHttp.lastClient?.lastRequest?.headers["Authorization"],
        )
    }

    @Test
    fun `check sends no authorization header without a token`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeHttp.nextResponseContent = release("9.9.9").encodeToByteArray()

        checker().check()

        assertNull(fakeHttp.lastClient?.lastRequest?.headers["Authorization"])
    }

    @Test
    fun `check returns the matching asset for this device`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeHttp.nextResponseContent = release("9.9.9").encodeToByteArray()

        val info = assertNotNull(checker().check())

        assertEquals("9.9.9", info.version)
        assertEquals("release notes", info.releaseNotes)
        assertEquals("husi-9.9.9-arm64-v8a.apk", info.assetName)
        assertEquals("https://example.invalid/husi-9.9.9-arm64-v8a.apk", info.downloadUrl)
        assertEquals(128L, info.sizeBytes)
        assertEquals(ASSET_SHA256, info.sha256)
    }

    @Test
    fun `check returns no sha256 when the asset carries no digest`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeHttp.nextResponseContent = release("9.9.9", digest = null).encodeToByteArray()

        assertNull(assertNotNull(checker().check()).sha256)
    }

    @Test
    fun `check ignores a digest that is not sha256`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeHttp.nextResponseContent =
            release("9.9.9", digest = "md5:$ASSET_SHA256").encodeToByteArray()

        assertNull(assertNotNull(checker().check()).sha256)
    }

    @Test
    fun `check lowercases an uppercase digest`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeHttp.nextResponseContent =
            release("9.9.9", digest = "sha256:${ASSET_SHA256.uppercase()}").encodeToByteArray()

        assertEquals(ASSET_SHA256, assertNotNull(checker().check()).sha256)
    }

    @Test
    fun `check returns no download url when no asset matches`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeHttp.nextResponseContent = release("9.9.9", assets = emptyList()).encodeToByteArray()

        val info = assertNotNull(checker().check())

        assertNull(info.downloadUrl)
        assertNull(info.assetName)
        assertEquals("https://example.invalid/9.9.9", info.releaseUrl)
    }

    @Test
    fun `check returns null when the release matches the current version`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeHttp.nextResponseContent = release("1.0.0").encodeToByteArray()

        assertNull(checker().check())
    }

    @Test
    fun `check returns null when the release is older`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeHttp.nextResponseContent = release("0.9.0").encodeToByteArray()

        assertNull(checker(currentVersion = "1.0.0").check())
    }

    @Test
    fun `check accepts a tag with a leading v`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeHttp.nextResponseContent = release("v1.0.1").encodeToByteArray()

        assertEquals("v1.0.1", checker().check()?.version)
    }

    @Test
    fun `check skips draft releases in the release list`() = runTest {
        DataStore.appUpdatePreRelease.set(true)
        val draft = release("9.9.9").replace("\"draft\": false", "\"draft\": true")
        fakeHttp.nextResponseContent = "[$draft,${release("2.0.0")}]".encodeToByteArray()

        assertEquals("2.0.0", checker().check()?.version)
    }

    // endregion
}
