package fr.husi

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAutoStartTest {
    @Test
    fun `removeLegacyAutoStartEntry deletes an existing entry`() {
        val entry = File.createTempFile("fr.husi.autostart-test", ".desktop")

        assertTrue(removeLegacyAutoStartEntry(entry))
        assertFalse(entry.exists())
    }

    @Test
    fun `removeLegacyAutoStartEntry ignores a missing entry`() {
        val dir = createTempDirectory("fr.husi.autostart-test").toFile()
        try {
            val entry = dir.resolve("fr.husi.desktop")

            assertFalse(removeLegacyAutoStartEntry(entry))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `removeLegacyAutoStartEntry ignores a directory`() {
        val dir = createTempDirectory("fr.husi.autostart-test").toFile()
        try {
            assertFalse(removeLegacyAutoStartEntry(dir))
            assertTrue(dir.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
