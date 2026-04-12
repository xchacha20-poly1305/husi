package fr.husi.ui

import fr.husi.RuleProvider
import fr.husi.bg.GithubRepository
import fr.husi.bg.buildGithubAssetSources
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AssetsScreenViewModelTest {

    private fun createVersionFiles(): List<File> {
        val directory = createTempDirectory("assets-screen-view-model-test-").toFile().apply {
            deleteOnExit()
        }
        return listOf(
            File(directory, "geoip.version.txt"),
            File(directory, "geosite.version.txt"),
        )
    }

    @Test
    fun `GithubRepository should expose fullName and resolve stable branch`() {
        val repository = GithubRepository(
            author = "SagerNet",
            name = "sing-geoip",
        )

        assertEquals("SagerNet/sing-geoip", repository.fullName)
        assertEquals("rule-set", repository.resolveBranch(useUnstableBranch = false))
        assertEquals("rule-set", repository.resolveBranch(useUnstableBranch = true))
    }

    @Test
    fun `GithubRepository should resolve unstable branch when enabled`() {
        val repository = GithubRepository(
            author = "SagerNet",
            name = "sing-geosite",
            unstableBranch = "rule-set-unstable",
        )

        assertEquals("rule-set", repository.resolveBranch(useUnstableBranch = false))
        assertEquals("rule-set-unstable", repository.resolveBranch(useUnstableBranch = true))
    }

    @Test
    fun `buildGithubAssetSources should map official provider to two SagerNet repos`() {
        val versionFiles = createVersionFiles()

        val sources = buildGithubAssetSources(RuleProvider.OFFICIAL, versionFiles)

        assertEquals(2, sources.size)
        assertEquals("SagerNet/sing-geoip", sources[0].repository.fullName)
        assertNull(sources[0].repository.unstableBranch)
        assertEquals(versionFiles[0], sources[0].versionFile)

        assertEquals("SagerNet/sing-geosite", sources[1].repository.fullName)
        assertEquals("rule-set-unstable", sources[1].repository.unstableBranch)
        assertEquals(versionFiles[1], sources[1].versionFile)
    }

    @Test
    fun `buildGithubAssetSources should map loyalsoldier provider to dedicated author repos`() {
        val versionFiles = createVersionFiles()

        val sources = buildGithubAssetSources(RuleProvider.LOYALSOLDIER, versionFiles)

        assertEquals(2, sources.size)
        assertEquals("1715173329/sing-geoip", sources[0].repository.fullName)
        assertEquals("1715173329/sing-geosite", sources[1].repository.fullName)
        assertEquals("rule-set-unstable", sources[1].repository.unstableBranch)
    }

    @Test
    fun `buildGithubAssetSources should map chocolate4u provider to single repo`() {
        val versionFiles = createVersionFiles()

        val sources = buildGithubAssetSources(RuleProvider.CHOCOLATE4U, versionFiles)

        assertEquals(1, sources.size)
        assertEquals("Chocolate4U/Iran-sing-box-rules", sources.single().repository.fullName)
        assertEquals(versionFiles[0], sources.single().versionFile)
    }

    @Test
    fun `buildGithubAssetSources should throw for unknown provider`() {
        val versionFiles = createVersionFiles()

        assertFailsWith<IllegalStateException> {
            buildGithubAssetSources(Int.MIN_VALUE, versionFiles)
        }
    }
}
