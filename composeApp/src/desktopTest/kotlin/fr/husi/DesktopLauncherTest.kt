package fr.husi

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopLauncherTest {

    private val binDir: File = createTempDirectory("husi-launcher").toFile()

    @AfterTest
    fun tearDown() {
        binDir.deleteRecursively()
    }

    /** The packaged bin directory also holds the core host pair. */
    private fun writeInstalledBinDir(launcherName: String): File {
        val launcher = createExecutable(launcherName)
        createExecutable("husi-core")
        createExecutable("libhusicore.so")
        binDir.resolve("desktop-java-opts.conf.template").writeText("")
        return launcher
    }

    private fun createExecutable(name: String): File {
        return binDir.resolve(name).also {
            it.writeText("")
            it.setExecutable(true)
        }
    }

    @Test
    fun `launcher is found beside the core host`() {
        val launcher = writeInstalledBinDir("fr.husi")

        assertEquals(launcher, resolveNamedDesktopLauncher(binDir, "fr.husi"))
    }

    @Test
    fun `non executable entry is not a launcher`() {
        writeInstalledBinDir("fr.husi")
        binDir.resolve("fr.husi").setExecutable(false)

        assertNull(resolveNamedDesktopLauncher(binDir, "fr.husi"))
    }

    @Test
    fun `missing launcher resolves to null`() {
        writeInstalledBinDir("fr.husi")

        assertNull(resolveNamedDesktopLauncher(binDir, "other.app"))
    }
}
