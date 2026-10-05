package fr.husi.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {

    private fun version(text: String): AppVersion {
        return AppVersion.parse(text) ?: error("$text should be a valid version")
    }

    private fun assertAscending(versions: List<String>) {
        versions.zipWithNext().forEach { (older, newer) ->
            assertTrue(version(newer) > version(older), "$newer should be newer than $older")
            assertTrue(version(older) < version(newer), "$older should be older than $newer")
        }
    }

    // region parse

    @Test
    fun `parse reads a final release`() {
        assertEquals(AppVersion(2, 2, 0), version("2.2.0"))
    }

    @Test
    fun `parse accepts the leading v used by release tags`() {
        assertEquals(version("2.2.0"), version("v2.2.0"))
    }

    @Test
    fun `parse trims surrounding spaces`() {
        assertEquals(version("2.2.0"), version(" v2.2.0 \n"))
    }

    @Test
    fun `parse reads dotted pre-release numbers`() {
        assertEquals(
            AppVersion(2, 2, 0, PreRelease(PreReleaseChannel.ALPHA, 1)),
            version("v2.2.0-alpha.1"),
        )
        assertEquals(
            AppVersion(1, 2, 0, PreRelease(PreReleaseChannel.BETA, 3)),
            version("1.2.0-beta.3"),
        )
        assertEquals(
            AppVersion(2, 0, 0, PreRelease(PreReleaseChannel.RC, 7)),
            version("v2.0.0-rc.7"),
        )
    }

    @Test
    fun `parse reads pre-release numbers without a dot`() {
        assertEquals(
            AppVersion(0, 5, 0, PreRelease(PreReleaseChannel.ALPHA, 2)),
            version("v0.5.0-alpha2"),
        )
        assertEquals(
            AppVersion(0, 4, 0, PreRelease(PreReleaseChannel.BETA, 3)),
            version("v0.4.0-beta3"),
        )
        assertEquals(
            AppVersion(0, 5, 0, PreRelease(PreReleaseChannel.RC, 1)),
            version("v0.5.0-rc1"),
        )
    }

    @Test
    fun `parse reads a bare pre-release label without a number`() {
        assertEquals(
            AppVersion(0, 14, 0, PreRelease(PreReleaseChannel.ALPHA)),
            version("0.14.0-alpha"),
        )
        assertEquals(
            AppVersion(0, 14, 0, PreRelease(PreReleaseChannel.RC)),
            version("0.14.0-rc"),
        )
    }

    @Test
    fun `parse ignores build metadata`() {
        assertEquals(version("2.1.0-beta.1"), version("2.1.0-beta.1+build.123"))
        assertEquals(version("1.2.3"), version("1.2.3+build.456"))
    }

    @Test
    fun `parse rejects plugin versions and tags`() {
        assertNull(AppVersion.parse("2.13.0-0"))
        assertNull(AppVersion.parse("127.0.6533.64-3"))
        assertNull(AppVersion.parse("plugin-mieru-v3.38.0-0"))
        assertNull(AppVersion.parse("plugin-shadowquic-v0.4.1-0"))
    }

    @Test
    fun `parse rejects an unknown pre-release label`() {
        assertNull(AppVersion.parse("v0.11.5-md3-3"))
        assertNull(AppVersion.parse("1.0.0-gamma.1"))
        assertNull(AppVersion.parse("1.0.0-Alpha.1"))
    }

    @Test
    fun `parse rejects malformed versions`() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("husi"))
        assertNull(AppVersion.parse("1.0"))
        assertNull(AppVersion.parse("1.0.0.0"))
        assertNull(AppVersion.parse("1.0.0-"))
        assertNull(AppVersion.parse("1.0.0-alpha."))
        assertNull(AppVersion.parse("1.0.0-alpha.1.2"))
        assertNull(AppVersion.parse("V1.0.0"))
        assertNull(AppVersion.parse("vv1.0.0"))
        assertNull(AppVersion.parse("1.0.0-alpha.-1"))
        assertNull(AppVersion.parse("1.0.0-alpha..1"))
        assertNull(AppVersion.parse("1.0.0-rc-1"))
        assertNull(AppVersion.parse("1.0.-1"))
        assertNull(AppVersion.parse("1.0.+1"))
    }

    // endregion

    // region isPreRelease

    @Test
    fun `isPreRelease is true for every pre-release style the project has published`() {
        listOf(
            "2.2.0-alpha.1",
            "2.0.0-beta.1",
            "2.0.0-rc.7",
            "0.5.0-alpha0",
            "0.4.0-beta3",
            "0.5.0-rc1",
            "0.15.0-alpha",
            "0.15.0-beta",
            "0.15.0-rc",
        ).forEach {
            assertTrue(version(it).isPreRelease, "$it should be a pre-release")
        }
    }

    @Test
    fun `isPreRelease is false for a final release`() {
        assertFalse(version("2.2.0").isPreRelease)
        assertFalse(version("v2.1.6").isPreRelease)
        assertFalse(version("1.2.3+build.456").isPreRelease)
    }

    // endregion

    // region compareTo

    @Test
    fun `compareTo orders version cores numerically`() {
        assertAscending(listOf("0.9.3", "0.10.0", "0.10.2", "0.10.10", "0.10.20", "1.0.0", "2.0.0"))
    }

    @Test
    fun `compareTo orders pre-release numbers numerically`() {
        assertAscending(listOf("0.11.0-alpha.2", "0.11.0-alpha.9", "0.11.0-alpha.11"))
    }

    @Test
    fun `compareTo follows the published order of a dotted pre-release cycle`() {
        assertAscending(
            listOf(
                "v1.4.3",
                "v2.0.0-alpha.0",
                "v2.0.0-alpha.7",
                "v2.0.0-beta.0",
                "v2.0.0-beta.1",
                "v2.0.0-rc.3",
                "v2.0.0-rc.7",
                "v2.0.0",
                "v2.0.1",
            ),
        )
    }

    @Test
    fun `compareTo follows the published order of an undotted pre-release cycle`() {
        assertAscending(
            listOf(
                "v0.4.1",
                "v0.5.0-alpha0",
                "v0.5.0-alpha1",
                "v0.5.0-alpha2",
                "v0.5.0-beta0",
                "v0.5.0-rc0",
                "v0.5.0-rc1",
                "v0.5.0",
            ),
        )
    }

    @Test
    fun `compareTo follows the published order of a bare label pre-release cycle`() {
        assertAscending(
            listOf("v0.14.1", "v0.15.0-alpha", "v0.15.0-beta", "v0.15.0-rc", "v0.15.0", "v0.15.1"),
        )
    }

    @Test
    fun `compareTo follows the published order of the latest release cycle`() {
        assertAscending(listOf("v2.1.6", "v2.2.0-alpha.0", "v2.2.0-alpha.1", "v2.2.0"))
    }

    @Test
    fun `compareTo ranks a later channel above a higher number in an earlier channel`() {
        assertAscending(listOf("1.0.0-alpha.11", "1.0.0-beta.0", "1.0.0-rc.0"))
    }

    @Test
    fun `compareTo ranks a pre-release of the next version above the current release`() {
        assertAscending(listOf("2.1.6", "2.2.0-alpha.0"))
    }

    @Test
    fun `compareTo treats dotted and undotted pre-release numbers as equal`() {
        assertEquals(0, version("0.5.0-rc.1").compareTo(version("0.5.0-rc1")))
        assertEquals(0, version("0.5.0-alpha.0").compareTo(version("0.5.0-alpha0")))
    }

    @Test
    fun `compareTo ranks a bare pre-release label below its number 0`() {
        assertAscending(listOf("0.15.0-alpha", "0.15.0-alpha.0", "0.15.0-alpha.1"))
        assertAscending(listOf("0.15.0-rc", "0.15.0-rc0", "0.15.0-rc.1"))
    }

    @Test
    fun `compareTo ranks a bare pre-release label above every number in an earlier channel`() {
        assertAscending(listOf("0.15.0-alpha.11", "0.15.0-beta", "0.15.0-beta.0"))
        assertAscending(listOf("0.15.0-beta.5", "0.15.0-rc", "0.15.0"))
    }

    @Test
    fun `compareTo treats a tag and its property version as equal`() {
        assertEquals(0, version("v2.2.0-alpha.1").compareTo(version("2.2.0-alpha.1")))
    }

    // endregion
}
