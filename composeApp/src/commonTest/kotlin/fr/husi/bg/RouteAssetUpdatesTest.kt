package fr.husi.bg

import fr.husi.RuleProvider
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteAssetUpdatesTest {

    private val dummyFiles = listOf(File("geoip.version.txt"), File("geosite.version.txt"))

    // region RuleProvider constants

    @Test
    fun `RUNETFREEDOM value does not collide with other providers`() {
        val values = listOf(
            RuleProvider.OFFICIAL,
            RuleProvider.LOYALSOLDIER,
            RuleProvider.CHOCOLATE4U,
            RuleProvider.CUSTOM,
            RuleProvider.RUNETFREEDOM,
        )
        assertEquals(values.size, values.toSet().size)
    }

    // endregion

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
        assertEquals("SagerNet/sing-geosite", sources[1].repository.fullName)
    }

    @Test
    fun `LOYALSOLDIER provider maps to two 1715173329 repositories`() {
        val sources = buildGithubAssetSources(RuleProvider.LOYALSOLDIER, dummyFiles)
        assertEquals(2, sources.size)
        assertEquals("1715173329/sing-geoip", sources[0].repository.fullName)
        assertEquals("1715173329/sing-geosite", sources[1].repository.fullName)
    }

    @Test
    fun `CHOCOLATE4U provider maps to one Iran-sing-box-rules repository`() {
        val sources = buildGithubAssetSources(RuleProvider.CHOCOLATE4U, dummyFiles)
        assertEquals(1, sources.size)
        assertEquals("Chocolate4U/Iran-sing-box-rules", sources[0].repository.fullName)
    }

    @Test
    fun `buildGithubAssetSources throws for unknown provider`() {
        assertFailsWith<IllegalStateException> {
            buildGithubAssetSources(999, dummyFiles)
        }
    }

    // endregion

    // region GithubReleaseSource for RUNETFREEDOM

    @Test
    fun `GithubReleaseSource has expected repository and asset name`() {
        val source = GithubReleaseSource(
            repository = GithubRepository(
                author = "runetfreedom",
                name = "russia-v2ray-rules-dat",
            ),
            assetName = "sing-box.zip",
            versionFile = dummyFiles[0],
        )
        assertEquals("runetfreedom/russia-v2ray-rules-dat", source.repository.fullName)
        assertEquals("sing-box.zip", source.assetName)
    }

    // endregion
}
