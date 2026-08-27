package fr.husi

import fr.husi.ktx.deleteRecursively
import fr.husi.ktx.setExecutable
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopLauncherTest {

    private val binDir: PlatformFile = PlatformFile(createTempDirectory("husi-launcher").toString())

    @AfterTest
    fun tearDown() {
        runBlocking { binDir.deleteRecursively() }
    }

    /** The packaged bin directory also holds the core host pair. */
    private suspend fun writeInstalledBinDir(launcherName: String): PlatformFile {
        val launcher = createExecutable(launcherName)
        createExecutable("husi-core")
        createExecutable("libhusicore.so")
        binDir.resolve("desktop-java-opts.conf.template").writeString("")
        return launcher
    }

    private suspend fun createExecutable(name: String): PlatformFile {
        val file = binDir.resolve(name)
        file.writeString("")
        file.setExecutable(true)
        return file
    }

    @Test
    fun `launcher is found beside the core host`() = runTest {
        val launcher = writeInstalledBinDir("fr.husi")

        assertEquals(launcher, resolveNamedDesktopLauncher(binDir, "fr.husi"))
    }

    @Test
    fun `non executable entry is not a launcher`() = runTest {
        writeInstalledBinDir("fr.husi")
        binDir.resolve("fr.husi").setExecutable(false)

        assertNull(resolveNamedDesktopLauncher(binDir, "fr.husi"))
    }

    @Test
    fun `missing launcher resolves to null`() = runTest {
        writeInstalledBinDir("fr.husi")

        assertNull(resolveNamedDesktopLauncher(binDir, "other.app"))
    }
}
