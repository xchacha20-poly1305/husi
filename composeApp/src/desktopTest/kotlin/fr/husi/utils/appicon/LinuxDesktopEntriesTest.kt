package fr.husi.utils.appicon

import fr.husi.ktx.deleteRecursively
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LinuxDesktopEntriesTest {
    @Test
    fun `strips field codes and quotes from Exec`() {
        val token = LinuxDesktopEntries.firstExecToken("\"/usr/lib/firefox/firefox\" %u")
        assertEquals("/usr/lib/firefox/firefox", token)
    }

    @Test
    fun `indexes TryExec ahead of Exec and matches both path styles`() = runTest {
        withTempDesktopTree { home, env ->
            val applications = PlatformFile(env.getValue("XDG_DATA_HOME")).resolve("applications")
            val absoluteBin = home.resolve("opt").resolve("app").resolve("bin").resolve("foo")
            absoluteBin.parent()?.createDirectories()
            absoluteBin.writeString("")
            val execFallback = home.resolve("usr").resolve("bin").resolve("foo")
            execFallback.parent()?.createDirectories()
            execFallback.writeString("")

            writeDesktop(
                applications.resolve("foo.desktop"),
                """
                [Desktop Entry]
                Type=Application
                Name=Foo App
                Icon=foo
                Exec=${execFallback.absolutePath()} %u
                TryExec=${absoluteBin.absolutePath()}
                """.trimIndent(),
            )

            val index = LinuxDesktopEntries.buildIndex(env, home.absolutePath())
            val byTryExec = assertNotNull(index.find(absoluteBin.absolutePath()))
            val byExec = assertNotNull(index.find(execFallback.absolutePath()))
            assertEquals("Foo App", byTryExec.name)
            assertEquals("foo", byTryExec.iconName)
            assertEquals(byTryExec, byExec)
        }
    }

    @Test
    fun `matches basename when Exec is not an absolute path`() = runTest {
        withTempDesktopTree { home, env ->
            val applications = PlatformFile(env.getValue("XDG_DATA_HOME")).resolve("applications")
            writeDesktop(
                applications.resolve("firefox.desktop"),
                """
                [Desktop Entry]
                Type=Application
                Name=Firefox
                Icon=firefox
                Exec=firefox %u
                """.trimIndent(),
            )

            val index = LinuxDesktopEntries.buildIndex(env, home.absolutePath())
            val found = assertNotNull(
                index.find(home.resolve("somewhere").resolve("firefox").absolutePath()),
            )
            assertEquals("Firefox", found.name)
        }
    }

    @Test
    fun `resolves symlink to the canonical Exec path`() = runTest {
        withTempDesktopTree { home, env ->
            val applications = PlatformFile(env.getValue("XDG_DATA_HOME")).resolve("applications")
            val real = home.resolve("lib").resolve("firefox").resolve("firefox")
            real.parent()?.createDirectories()
            real.writeString("")
            val linkDir = home.resolve("bin")
            linkDir.createDirectories()
            val link = linkDir.resolve("firefox")
            Files.createSymbolicLink(
                Path.of(link.absolutePath()),
                Path.of(real.absolutePath()),
            )

            writeDesktop(
                applications.resolve("firefox.desktop"),
                """
                [Desktop Entry]
                Type=Application
                Name=Firefox
                Icon=firefox
                Exec=${real.absolutePath()} %u
                """.trimIndent(),
            )

            val index = LinuxDesktopEntries.buildIndex(env, home.absolutePath())
            val found = assertNotNull(index.find(link.absolutePath()))
            assertEquals("Firefox", found.name)
        }
    }

    @Test
    fun `skips NoDisplay and non-Application entries`() = runTest {
        withTempDesktopTree { home, env ->
            val applications = PlatformFile(env.getValue("XDG_DATA_HOME")).resolve("applications")
            writeDesktop(
                applications.resolve("hidden.desktop"),
                """
                [Desktop Entry]
                Type=Application
                Name=Hidden
                Exec=/usr/bin/hidden
                NoDisplay=true
                """.trimIndent(),
            )
            writeDesktop(
                applications.resolve("link.desktop"),
                """
                [Desktop Entry]
                Type=Link
                Name=Website
                Exec=/usr/bin/website
                """.trimIndent(),
            )

            val index = LinuxDesktopEntries.buildIndex(env, home.absolutePath())
            assertNull(index.find("/usr/bin/hidden"))
            assertNull(index.find("/usr/bin/website"))
        }
    }

    @Test
    fun `ignores localized Name keys`() {
        val parsed = LinuxDesktopEntries.parseDesktopEntryText(
            """
            [Desktop Entry]
            Type=Application
            Name=Firefox
            Name[zh_CN]=火狐
            Icon=firefox
            Exec=firefox
            """.trimIndent(),
        )
        assertNotNull(parsed)
        assertEquals("Firefox", parsed.name)
    }

    private suspend fun writeDesktop(file: PlatformFile, contents: String) {
        file.parent()?.createDirectories()
        file.writeString(contents)
    }

    private suspend fun withTempDesktopTree(
        block: suspend (home: PlatformFile, env: Map<String, String>) -> Unit,
    ) {
        val home = PlatformFile(createTempDirectory("husi-desktop-entries").toString())
        try {
            val dataHome = home.resolve("local").resolve("share")
            dataHome.createDirectories()
            val env = mapOf(
                "HOME" to home.absolutePath(),
                "XDG_DATA_HOME" to dataHome.absolutePath(),
                "XDG_DATA_DIRS" to home.resolve("usr").resolve("share").absolutePath(),
            )
            block(home, env)
        } finally {
            home.deleteRecursively()
        }
    }
}
