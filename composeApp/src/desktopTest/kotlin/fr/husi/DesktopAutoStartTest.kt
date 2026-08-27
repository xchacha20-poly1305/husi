package fr.husi

import fr.husi.ktx.deleteRecursively
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAutoStartTest {
    @Test
    fun `removeLegacyAutoStartEntry deletes an existing entry`() = runTest {
        val dir = PlatformFile(createTempDirectory("fr.husi.autostart-test").toString())
        try {
            val entry = dir.resolve("fr.husi.desktop")
            entry.writeString("")

            assertTrue(removeLegacyAutoStartEntry(entry))
            assertFalse(entry.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `removeLegacyAutoStartEntry ignores a missing entry`() = runTest {
        val dir = PlatformFile(createTempDirectory("fr.husi.autostart-test").toString())
        try {
            val entry = dir.resolve("fr.husi.desktop")

            assertFalse(removeLegacyAutoStartEntry(entry))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `removeLegacyAutoStartEntry ignores a directory`() = runTest {
        val dir = PlatformFile(createTempDirectory("fr.husi.autostart-test").toString())
        try {
            dir.createDirectories()

            assertFalse(removeLegacyAutoStartEntry(dir))
            assertTrue(dir.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
