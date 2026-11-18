package fr.husi

import fr.husi.ktx.createPlatformTempFile
import fr.husi.ktx.deleteRecursivelyBlocking
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.resolve
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAutoStartTest {
    @Test
    fun `removeLegacyAutoStartEntry deletes an existing entry`() {
        val entry = createPlatformTempFile("fr.husi.autostart-test", ".desktop")

        assertTrue(removeLegacyAutoStartEntry(entry))
        assertFalse(entry.exists())
    }

    @Test
    fun `removeLegacyAutoStartEntry ignores a missing entry`() {
        val dir = PlatformFile(createTempDirectory("fr.husi.autostart-test").toString())
        try {
            val entry = dir.resolve("fr.husi.desktop")

            assertFalse(removeLegacyAutoStartEntry(entry))
        } finally {
            dir.deleteRecursivelyBlocking()
        }
    }

    @Test
    fun `removeLegacyAutoStartEntry ignores a directory`() {
        val dir = PlatformFile(createTempDirectory("fr.husi.autostart-test").toString())
        try {
            assertFalse(removeLegacyAutoStartEntry(dir))
            assertTrue(dir.exists())
        } finally {
            dir.deleteRecursivelyBlocking()
        }
    }
}
