package fr.husi.bg

import fr.husi.database.DataStore
import fr.husi.test.HusiHttpKoinTest
import fr.husi.test.RecordedGitHubCall
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
        draft: Boolean = false,
    ): GithubRelease {
        return GithubRelease(
            tagName = tag,
            body = "release notes",
            draft = draft,
            htmlUrl = "https://example.invalid/$tag",
            assets = assets.map { name ->
                GithubReleaseAsset(
                    name = name,
                    browserDownloadUrl = "https://example.invalid/$name",
                    size = 128L,
                    digest = digest,
                )
            },
        )
    }

    private fun checker(
        currentVersion: String = "1.0.0",
        abis: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
    ) = AppUpdateChecker(
        gitHubApi = fakeGitHub,
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
        fakeGitHub.nextLatestRelease = release("9.9.9")

        checker().check()

        val call = assertNotNull(fakeGitHub.lastCall)
        assertEquals(RecordedGitHubCall.Kind.LATEST_RELEASE, call.kind)
        assertEquals("xchacha20-poly1305", call.owner)
        assertEquals("husi", call.repo)
    }

    @Test
    fun `check requests the release list when pre-releases are on`() = runTest {
        DataStore.appUpdatePreRelease.set(true)
        fakeGitHub.nextReleases = listOf(release("9.9.9"))

        checker().check()

        val call = assertNotNull(fakeGitHub.lastCall)
        assertEquals(RecordedGitHubCall.Kind.RELEASES, call.kind)
        assertEquals("xchacha20-poly1305", call.owner)
        assertEquals("husi", call.repo)
        assertEquals(RELEASE_LIST_PAGE_SIZE, call.perPage)
    }

    @Test
    fun `check sends the github token as a bearer header`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        DataStore.appUpdateToken.set("secret-token")
        fakeGitHub.nextLatestRelease = release("9.9.9")

        checker().check()

        assertEquals(
            "Bearer secret-token",
            fakeGitHub.lastCall?.authorization,
        )
    }

    @Test
    fun `check sends no authorization header without a token`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeGitHub.nextLatestRelease = release("9.9.9")

        checker().check()

        assertNull(fakeGitHub.lastCall?.authorization)
    }

    @Test
    fun `check returns the matching asset for this device`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeGitHub.nextLatestRelease = release("9.9.9")

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
        fakeGitHub.nextLatestRelease = release("9.9.9", digest = null)

        assertNull(assertNotNull(checker().check()).sha256)
    }

    @Test
    fun `check ignores a digest that is not sha256`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeGitHub.nextLatestRelease = release("9.9.9", digest = "md5:$ASSET_SHA256")

        assertNull(assertNotNull(checker().check()).sha256)
    }

    @Test
    fun `check lowercases an uppercase digest`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeGitHub.nextLatestRelease = release("9.9.9", digest = "sha256:${ASSET_SHA256.uppercase()}")

        assertEquals(ASSET_SHA256, assertNotNull(checker().check()).sha256)
    }

    @Test
    fun `check returns no download url when no asset matches`() = runTest {
        DataStore.appUpdatePreRelease.set(false)
        fakeGitHub.nextLatestRelease = release("9.9.9", assets = emptyList())

        val info = assertNotNull(checker().check())

        assertNull(info.downloadUrl)
        assertNull(info.assetName)
        assertEquals("https://example.invalid/9.9.9", info.releaseUrl)
    }

    @Test
    fun `check skips plugin releases in the release list`() = runTest {
        DataStore.appUpdatePreRelease.set(true)
        val plugin = release("plugin-mieru-v3.37.0-0", assets = listOf("mieru-plugin.apk"))
        fakeGitHub.nextReleases = listOf(plugin, release("2.0.0"))

        assertEquals("2.0.0", checker().check()?.version)
    }

    @Test
    fun `check skips draft releases in the release list`() = runTest {
        DataStore.appUpdatePreRelease.set(true)
        val draft = release("9.9.9", draft = true)
        fakeGitHub.nextReleases = listOf(draft, release("2.0.0"))

        assertEquals("2.0.0", checker().check()?.version)
    }

    // endregion
}
