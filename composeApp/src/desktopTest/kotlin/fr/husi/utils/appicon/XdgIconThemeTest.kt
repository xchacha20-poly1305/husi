package fr.husi.utils.appicon

import fr.husi.ktx.canonicalFile
import fr.husi.ktx.deleteRecursivelyBlocking
import fr.husi.ktx.writeText
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.resolve
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class XdgIconThemeTest {
    @Test
    fun `prefers larger size over smaller size`() {
        withIconTree { home, env, pixmaps ->
            val share = PlatformFile(env.getValue("XDG_DATA_DIRS"))
            writeFile(share.resolve("icons").resolve("Adwaita").resolve("48x48").resolve("apps").resolve("firefox.png"))
            val large = writeFile(
                share.resolve("icons").resolve("Adwaita").resolve("256x256").resolve("apps").resolve("firefox.png"),
            )
            writeGtkTheme(home, "Adwaita")

            val found = assertNotNull(
                XdgIconTheme.findIconFile("firefox", env, home.absolutePath(), pixmaps),
            )
            assertEquals(large.canonicalFile(), found.canonicalFile())
        }
    }

    @Test
    fun `follows Inherits chain`() {
        withIconTree { home, env, pixmaps ->
            val share = PlatformFile(env.getValue("XDG_DATA_DIRS"))
            val inherited = writeFile(
                share.resolve("icons").resolve("Adwaita").resolve("48x48").resolve("apps").resolve("firefox.png"),
            )
            val myTheme = home.resolve(".icons").resolve("MyTheme")
            writeFile(
                myTheme.resolve("index.theme"),
                """
                [Icon Theme]
                Name=MyTheme
                Inherits=Adwaita
                """.trimIndent(),
            )
            writeGtkTheme(home, "MyTheme")

            val found = assertNotNull(
                XdgIconTheme.findIconFile("firefox", env, home.absolutePath(), pixmaps),
            )
            assertEquals(inherited.canonicalFile(), found.canonicalFile())
        }
    }

    @Test
    fun `falls back to hicolor when the current theme has no icon`() {
        withIconTree { home, env, pixmaps ->
            val share = PlatformFile(env.getValue("XDG_DATA_DIRS"))
            val hicolor = writeFile(
                share.resolve("icons").resolve("hicolor").resolve("48x48").resolve("apps").resolve("only-hicolor.png"),
            )
            writeGtkTheme(home, "EmptyTheme")

            val found = assertNotNull(
                XdgIconTheme.findIconFile("only-hicolor", env, home.absolutePath(), pixmaps),
            )
            assertEquals(hicolor.canonicalFile(), found.canonicalFile())
        }
    }

    @Test
    fun `falls back to pixmaps when no theme has the icon`() {
        withIconTree { home, env, pixmaps ->
            val pixmap = writeFile(pixmaps.resolve("legacy.png"))
            writeGtkTheme(home, "Adwaita")

            val found = assertNotNull(
                XdgIconTheme.findIconFile("legacy", env, home.absolutePath(), pixmaps),
            )
            assertEquals(pixmap.canonicalFile(), found.canonicalFile())
        }
    }

    @Test
    fun `reads gtk-3 settings when gtk-4 is missing`() {
        withIconTree { home, env, pixmaps ->
            val share = PlatformFile(env.getValue("XDG_DATA_DIRS"))
            val icon = writeFile(
                share.resolve("icons").resolve("Adwaita").resolve("48x48").resolve("apps").resolve("term.png"),
            )
            val gtk3 = home.resolve(".config").resolve("gtk-3.0")
            gtk3.createDirectories()
            gtk3.resolve("settings.ini").writeText(
                """
                [Settings]
                gtk-icon-theme-name=Adwaita
                """.trimIndent(),
            )

            val found = assertNotNull(
                XdgIconTheme.findIconFile("term", env, home.absolutePath(), pixmaps),
            )
            assertEquals(icon.canonicalFile(), found.canonicalFile())
        }
    }

    @Test
    fun `returns null when nothing matches`() {
        withIconTree { home, env, pixmaps ->
            writeGtkTheme(home, "Adwaita")
            assertNull(XdgIconTheme.findIconFile("missing-icon", env, home.absolutePath(), pixmaps))
        }
    }

    private fun writeGtkTheme(home: PlatformFile, theme: String) {
        val gtk4 = home.resolve(".config").resolve("gtk-4.0")
        gtk4.createDirectories()
        gtk4.resolve("settings.ini").writeText(
            """
            [Settings]
            gtk-icon-theme-name=$theme
            """.trimIndent(),
        )
    }

    private fun writeFile(file: PlatformFile, contents: String = "x"): PlatformFile {
        checkNotNull(file.parent()).createDirectories()
        file.writeText(contents)
        return file
    }

    private fun withIconTree(block: (home: PlatformFile, env: Map<String, String>, pixmaps: PlatformFile) -> Unit) {
        val home = PlatformFile(createTempDirectory("husi-xdg-icons").toString())
        try {
            val share = home.resolve("usr").resolve("share")
            val pixmaps = share.resolve("pixmaps")
            pixmaps.createDirectories()
            val env = mapOf(
                "HOME" to home.absolutePath(),
                "XDG_CONFIG_HOME" to home.resolve(".config").absolutePath(),
                "XDG_DATA_DIRS" to share.absolutePath(),
            )
            block(home, env, pixmaps)
        } finally {
            home.deleteRecursivelyBlocking()
        }
    }
}
