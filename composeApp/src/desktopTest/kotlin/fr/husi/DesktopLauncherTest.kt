package fr.husi

import fr.husi.ktx.deleteRecursivelyBlocking
import fr.husi.ktx.toJavaFile
import fr.husi.ktx.writeText
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.resolve
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopLauncherTest {

    private val binDir: PlatformFile = PlatformFile(createTempDirectory("husi-launcher").toString())

    @AfterTest
    fun tearDown() {
        binDir.deleteRecursivelyBlocking()
    }

    /** The packaged bin directory also holds the core host. */
    private fun writeInstalledBinDir(launcherName: String): PlatformFile {
        val launcher = createExecutable(launcherName)
        createExecutable("husi-core")
        binDir.resolve("desktop-java-opts.conf.template").writeText("")
        return launcher
    }

    private fun createExecutable(name: String): PlatformFile {
        return binDir.resolve(name).also {
            it.writeText("")
            it.toJavaFile().setExecutable(true)
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
        binDir.resolve("fr.husi").toJavaFile().setExecutable(false)

        assertNull(resolveNamedDesktopLauncher(binDir, "fr.husi"))
    }

    @Test
    fun `missing launcher resolves to null`() {
        writeInstalledBinDir("fr.husi")

        assertNull(resolveNamedDesktopLauncher(binDir, "other.app"))
    }
}
