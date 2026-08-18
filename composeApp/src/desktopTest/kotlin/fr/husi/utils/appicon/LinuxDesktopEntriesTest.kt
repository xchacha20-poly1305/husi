package fr.husi.utils.appicon

import java.io.File
import java.nio.file.Files
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
    fun `indexes TryExec ahead of Exec and matches both path styles`() {
        withTempDesktopTree { home, env ->
            val applications = File(env.getValue("XDG_DATA_HOME"), "applications")
            val absoluteBin = home.resolve("opt").resolve("app").resolve("bin").resolve("foo")
            absoluteBin.parentFile.mkdirs()
            absoluteBin.writeText("")
            val execFallback = home.resolve("usr").resolve("bin").resolve("foo")
            execFallback.parentFile.mkdirs()
            execFallback.writeText("")

            writeDesktop(
                applications.resolve("foo.desktop"),
                """
                [Desktop Entry]
                Type=Application
                Name=Foo App
                Icon=foo
                Exec=${execFallback.absolutePath} %u
                TryExec=${absoluteBin.absolutePath}
                """.trimIndent(),
            )

            val index = LinuxDesktopEntries.buildIndex(env, home.absolutePath)
            val byTryExec = assertNotNull(index.find(absoluteBin.absolutePath))
            val byExec = assertNotNull(index.find(execFallback.absolutePath))
            assertEquals("Foo App", byTryExec.name)
            assertEquals("foo", byTryExec.iconName)
            assertEquals(byTryExec, byExec)
        }
    }

    @Test
    fun `matches basename when Exec is not an absolute path`() {
        withTempDesktopTree { home, env ->
            val applications = File(env.getValue("XDG_DATA_HOME"), "applications")
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

            val index = LinuxDesktopEntries.buildIndex(env, home.absolutePath)
            val found = assertNotNull(index.find(home.resolve("somewhere").resolve("firefox").absolutePath))
            assertEquals("Firefox", found.name)
        }
    }

    @Test
    fun `resolves symlink to the canonical Exec path`() {
        withTempDesktopTree { home, env ->
            val applications = File(env.getValue("XDG_DATA_HOME"), "applications")
            val real = home.resolve("lib").resolve("firefox").resolve("firefox")
            real.parentFile.mkdirs()
            real.writeText("")
            val linkDir = home.resolve("bin")
            linkDir.mkdirs()
            val link = linkDir.resolve("firefox")
            Files.createSymbolicLink(link.toPath(), real.toPath())

            writeDesktop(
                applications.resolve("firefox.desktop"),
                """
                [Desktop Entry]
                Type=Application
                Name=Firefox
                Icon=firefox
                Exec=${real.absolutePath} %u
                """.trimIndent(),
            )

            val index = LinuxDesktopEntries.buildIndex(env, home.absolutePath)
            val found = assertNotNull(index.find(link.absolutePath))
            assertEquals("Firefox", found.name)
        }
    }

    @Test
    fun `skips NoDisplay and non-Application entries`() {
        withTempDesktopTree { home, env ->
            val applications = File(env.getValue("XDG_DATA_HOME"), "applications")
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

            val index = LinuxDesktopEntries.buildIndex(env, home.absolutePath)
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

    private fun writeDesktop(file: File, contents: String) {
        file.parentFile.mkdirs()
        file.writeText(contents)
    }

    private fun withTempDesktopTree(block: (home: File, env: Map<String, String>) -> Unit) {
        val home = createTempDirectory("husi-desktop-entries").toFile()
        try {
            val dataHome = home.resolve("local").resolve("share")
            dataHome.mkdirs()
            val env = mapOf(
                "HOME" to home.absolutePath,
                "XDG_DATA_HOME" to dataHome.absolutePath,
                "XDG_DATA_DIRS" to home.resolve("usr").resolve("share").absolutePath,
            )
            block(home, env)
        } finally {
            home.deleteRecursively()
        }
    }
}
