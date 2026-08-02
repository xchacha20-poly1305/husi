package fr.husi.bg

import fr.husi.RuleProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteAssetUpdatesTest {

    private val dummyFiles = listOf(File("geoip.version.txt"), File("geosite.version.txt"))

    private fun createTempDir(): File =
        createTempDirectory("route-asset-updates-test-").toFile().also { it.deleteOnExit() }

    // region hasUnstableBranch

    @Test
    fun `hasUnstableBranch returns true for OFFICIAL and LOYALSOLDIER`() {
        assertTrue(RuleProvider.hasUnstableBranch(RuleProvider.OFFICIAL))
        assertTrue(RuleProvider.hasUnstableBranch(RuleProvider.LOYALSOLDIER))
    }

    @Test
    fun `hasUnstableBranch returns false for CHOCOLATE4U, RUNETFREEDOM, and CUSTOM`() {
        assertFalse(RuleProvider.hasUnstableBranch(RuleProvider.CHOCOLATE4U))
        assertFalse(RuleProvider.hasUnstableBranch(RuleProvider.RUNETFREEDOM))
        assertFalse(RuleProvider.hasUnstableBranch(RuleProvider.CUSTOM))
    }

    // endregion

    // region GithubRepository.resolveBranch

    @Test
    fun `resolveBranch returns unstable branch when useUnstableBranch is true and branch exists`() {
        val repo = GithubRepository(
            author = "SagerNet",
            name = "sing-geosite",
            unstableBranch = "rule-set-unstable",
        )
        assertEquals("rule-set-unstable", repo.resolveBranch(useUnstableBranch = true))
    }

    @Test
    fun `resolveBranch returns default branch when useUnstableBranch is false`() {
        val repo = GithubRepository(
            author = "SagerNet",
            name = "sing-geosite",
            unstableBranch = "rule-set-unstable",
        )
        assertEquals("rule-set", repo.resolveBranch(useUnstableBranch = false))
    }

    @Test
    fun `resolveBranch returns default branch when no unstable branch is set`() {
        val repo = GithubRepository(author = "SagerNet", name = "sing-geoip")
        assertEquals("rule-set", repo.resolveBranch(useUnstableBranch = true))
    }

    // endregion

    // region buildGithubAssetSources

    @Test
    fun `OFFICIAL provider maps to two SagerNet repositories`() {
        val sources = buildGithubAssetSources(RuleProvider.OFFICIAL, dummyFiles)
        assertEquals(2, sources.size)

        assertEquals("SagerNet/sing-geoip", sources[0].repository.fullName)
        assertNull(sources[0].repository.unstableBranch)
        assertEquals(dummyFiles[0], sources[0].versionFile)

        assertEquals("SagerNet/sing-geosite", sources[1].repository.fullName)
        assertEquals("rule-set-unstable", sources[1].repository.unstableBranch)
        assertEquals(dummyFiles[1], sources[1].versionFile)
    }

    @Test
    fun `LOYALSOLDIER provider maps to two 1715173329 repositories`() {
        val sources = buildGithubAssetSources(RuleProvider.LOYALSOLDIER, dummyFiles)
        assertEquals(2, sources.size)

        assertEquals("1715173329/sing-geoip", sources[0].repository.fullName)
        assertEquals(dummyFiles[0], sources[0].versionFile)

        assertEquals("1715173329/sing-geosite", sources[1].repository.fullName)
        assertEquals("rule-set-unstable", sources[1].repository.unstableBranch)
        assertEquals(dummyFiles[1], sources[1].versionFile)
    }

    @Test
    fun `CHOCOLATE4U provider maps to one Iran-sing-box-rules repository`() {
        val sources = buildGithubAssetSources(RuleProvider.CHOCOLATE4U, dummyFiles)
        assertEquals(1, sources.size)
        assertEquals("Chocolate4U/Iran-sing-box-rules", sources.single().repository.fullName)
        assertEquals(dummyFiles[0], sources.single().versionFile)
    }

    @Test
    fun `buildGithubAssetSources throws for unknown provider`() {
        assertFailsWith<IllegalStateException> {
            buildGithubAssetSources(999, dummyFiles)
        }
    }

    // endregion

    // region URL construction

    @Test
    fun `githubApiReleaseUrl includes repository full name`() {
        assertEquals(
            "https://api.github.com/repos/SagerNet/sing-geoip/releases/latest",
            githubApiReleaseUrl("SagerNet/sing-geoip"),
        )
    }

    @Test
    fun `githubCodloadTarGzUrl includes full name and branch`() {
        assertEquals(
            "https://codeload.github.com/SagerNet/sing-geosite/tar.gz/refs/heads/rule-set-unstable",
            githubCodloadTarGzUrl("SagerNet/sing-geosite", "rule-set-unstable"),
        )
    }

    @Test
    fun `githubReleaseDownloadUrl includes tag and asset name`() {
        assertEquals(
            "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/download/v1.2.3/sing-box.zip",
            githubReleaseDownloadUrl(
                "runetfreedom/russia-v2ray-rules-dat",
                "v1.2.3",
                "sing-box.zip",
            ),
        )
    }

    // endregion

    // region GithubAssetUpdater.check

    @Test
    fun `check returns update when remote version differs from local`() = runTest {
        val dir = createTempDir()
        val versionFile = dir.resolve("geoip.version.txt").also { it.writeText("202605151045") }
        val source = GithubAssetSource(
            repository = GithubRepository(author = "SagerNet", name = "sing-geoip"),
            versionFile = versionFile,
        )
        val remoteSource = mockk<RemoteSource>()
        every { remoteSource.fetchString(any()) } returns """{"tag_name":"202605161045"}"""
        val updater = GithubAssetUpdater(
            versionFiles = listOf(versionFile),
            updateProgress = {},
            cacheDir = dir,
            destinationDir = dir,
            sources = listOf(source),
            useUnstableBranch = false,
            remoteSource = remoteSource,
        )

        val updates = updater.check()

        assertEquals(1, updates.size)
        assertEquals("202605161045", (updates[0] as UpdateInfo.Github).newVersion)
        verify { remoteSource.fetchString(githubApiReleaseUrl("SagerNet/sing-geoip")) }
    }

    @Test
    fun `check returns no update when remote version matches local`() = runTest {
        val dir = createTempDir()
        val versionFile = dir.resolve("geoip.version.txt").also { it.writeText("202605161045") }
        val source = GithubAssetSource(
            repository = GithubRepository(author = "SagerNet", name = "sing-geoip"),
            versionFile = versionFile,
        )
        val remoteSource = mockk<RemoteSource>()
        every { remoteSource.fetchString(any()) } returns """{"tag_name":"202605161045"}"""
        val updater = GithubAssetUpdater(
            versionFiles = listOf(versionFile),
            updateProgress = {},
            cacheDir = dir,
            destinationDir = dir,
            sources = listOf(source),
            useUnstableBranch = false,
            remoteSource = remoteSource,
        )

        assertTrue(updater.check().isEmpty())
    }

    @Test
    fun `check skips source when remote version response is empty`() = runTest {
        val dir = createTempDir()
        val source = GithubAssetSource(
            repository = GithubRepository(author = "SagerNet", name = "sing-geoip"),
            versionFile = dir.resolve("geoip.version.txt"),
        )
        val remoteSource = mockk<RemoteSource>()
        every { remoteSource.fetchString(any()) } returns """{"tag_name":""}"""
        val updater = GithubAssetUpdater(
            versionFiles = emptyList(),
            updateProgress = {},
            cacheDir = dir,
            destinationDir = dir,
            sources = listOf(source),
            useUnstableBranch = false,
            remoteSource = remoteSource,
        )

        assertTrue(updater.check().isEmpty())
    }

    @Test
    fun `check fetches correct URL for each source`() = runTest {
        val dir = createTempDir()
        val vf1 = dir.resolve("geoip.version.txt").also { it.writeText("202605151045") }
        val vf2 = dir.resolve("geosite.version.txt").also { it.writeText("202605151045") }
        val sources = listOf(
            GithubAssetSource(GithubRepository("SagerNet", "sing-geoip"), vf1),
            GithubAssetSource(
                GithubRepository(
                    "SagerNet",
                    "sing-geosite",
                    unstableBranch = "rule-set-unstable",
                ),
                vf2,
            ),
        )
        val remoteSource = mockk<RemoteSource>()
        every { remoteSource.fetchString(any()) } returns """{"tag_name":"202605161045"}"""
        val updater = GithubAssetUpdater(
            versionFiles = listOf(vf1, vf2),
            updateProgress = {},
            cacheDir = dir,
            destinationDir = dir,
            sources = sources,
            useUnstableBranch = true,
            remoteSource = remoteSource,
        )

        updater.check()

        verify { remoteSource.fetchString(githubApiReleaseUrl("SagerNet/sing-geoip")) }
        verify { remoteSource.fetchString(githubApiReleaseUrl("SagerNet/sing-geosite")) }
    }

    // endregion

    // region GithubReleaseZipUpdater.check

    @Test
    fun `GithubReleaseZipUpdater check returns update when versions differ`() = runTest {
        val dir = createTempDir()
        val versionFile = dir.resolve("geoip.version.txt").also { it.writeText("202605151045") }
        val source = GithubReleaseSource(
            repository = GithubRepository(author = "runetfreedom", name = "russia-v2ray-rules-dat"),
            assetName = "sing-box.zip",
            versionFile = versionFile,
        )
        val remoteSource = mockk<RemoteSource>()
        every { remoteSource.fetchString(any()) } returns """{"tag_name":"202605161045"}"""
        val updater = GithubReleaseZipUpdater(
            versionFiles = listOf(versionFile),
            updateProgress = {},
            cacheDir = dir,
            destinationDir = dir,
            source = source,
            remoteSource = remoteSource,
        )

        val updates = updater.check()

        assertEquals(1, updates.size)
        assertEquals("202605161045", (updates[0] as UpdateInfo.Github).newVersion)
        verify { remoteSource.fetchString(githubApiReleaseUrl("runetfreedom/russia-v2ray-rules-dat")) }
    }

    @Test
    fun `GithubReleaseZipUpdater check returns empty when versions match`() = runTest {
        val dir = createTempDir()
        val versionFile = dir.resolve("geoip.version.txt").also { it.writeText("202605161045") }
        val source = GithubReleaseSource(
            repository = GithubRepository(author = "runetfreedom", name = "russia-v2ray-rules-dat"),
            assetName = "sing-box.zip",
            versionFile = versionFile,
        )
        val remoteSource = mockk<RemoteSource>()
        every { remoteSource.fetchString(any()) } returns """{"tag_name":"202605161045"}"""
        val updater = GithubReleaseZipUpdater(
            versionFiles = listOf(versionFile),
            updateProgress = {},
            cacheDir = dir,
            destinationDir = dir,
            source = source,
            remoteSource = remoteSource,
        )

        assertTrue(updater.check().isEmpty())
    }

    // endregion

    // region CustomAssetUpdater.check

    @Test
    fun `CustomAssetUpdater check maps each link to UpdateInfo Custom`() = runTest {
        val dir = createTempDir()
        val links = listOf("https://example.com/geoip.db", "https://example.com/geosite.db")
        val updater = CustomAssetUpdater(
            versionFiles = dummyFiles,
            updateProgress = {},
            cacheDir = dir,
            destinationDir = dir,
            links = links,
            remoteSource = mockk(relaxed = true),
        )

        val updates = updater.check()

        assertEquals(2, updates.size)
        assertEquals(UpdateInfo.Custom("https://example.com/geoip.db"), updates[0])
        assertEquals(UpdateInfo.Custom("https://example.com/geosite.db"), updates[1])
    }

    @Test
    fun `CustomAssetUpdater check returns empty list when links are empty`() = runTest {
        val dir = createTempDir()
        val updater = CustomAssetUpdater(
            versionFiles = dummyFiles,
            updateProgress = {},
            cacheDir = dir,
            destinationDir = dir,
            links = emptyList(),
            remoteSource = mockk(relaxed = true),
        )

        assertTrue(updater.check().isEmpty())
    }

    // endregion
}
