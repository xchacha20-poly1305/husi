package fr.husi

import fr.husi.platform.Platform
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPathsTest {
    @Test
    fun `linux data dir uses XDG_CONFIG_HOME first`() {
        val paths = DesktopPaths.resolve(
            platform = Platform.Linux,
            env = mapOf(
                "XDG_CONFIG_HOME" to "/config",
                "HOME" to "/home/alice",
            ),
            userHomeProperty = "/jvm-home",
        )

        assertEquals(File("/config"), paths.configHomeDir)
        assertEquals(File("/config/husi"), paths.dataDir)
        assertEquals(File("/config/autostart"), paths.linuxAutostartDir)
        assertEquals(File("/config/systemd/user"), paths.linuxSystemdUserDir)
    }

    @Test
    fun `linux data dir falls back to HOME config`() {
        val paths = DesktopPaths.resolve(
            platform = Platform.Linux,
            env = mapOf(
                "XDG_CONFIG_HOME" to "",
                "HOME" to "/home/alice",
            ),
            userHomeProperty = "/jvm-home",
        )

        assertEquals(File("/home/alice"), paths.userHomeDir)
        assertEquals(File("/home/alice/.config"), paths.configHomeDir)
        assertEquals(File("/home/alice/.config/husi"), paths.dataDir)
    }

    @Test
    fun `macos data dir uses application support under HOME`() {
        val paths = DesktopPaths.resolve(
            platform = Platform.MacOs,
            env = mapOf("HOME" to "/Users/alice"),
            userHomeProperty = "/jvm-home",
        )

        assertEquals(File("/Users/alice"), paths.userHomeDir)
        assertEquals(File("/Users/alice/Library/Application Support"), paths.configHomeDir)
        assertEquals(File("/Users/alice/Library/Application Support/husi"), paths.dataDir)
        assertEquals(File("/Users/alice/Library/LaunchAgents"), paths.macLaunchAgentsDir)
    }

    @Test
    fun `windows data dir uses APPDATA first`() {
        val paths = DesktopPaths.resolve(
            platform = Platform.Windows,
            env = mapOf(
                "APPDATA" to "C:/Users/Alice/AppData/Roaming",
                "USERPROFILE" to "C:/Users/Alice",
            ),
            userHomeProperty = "C:/JvmHome",
        )

        assertEquals(File("C:/Users/Alice"), paths.userHomeDir)
        assertEquals(File("C:/Users/Alice/AppData/Roaming"), paths.configHomeDir)
        assertEquals(File("C:/Users/Alice/AppData/Roaming/husi"), paths.dataDir)
    }

    @Test
    fun `windows data dir falls back to USERPROFILE roaming appdata`() {
        val paths = DesktopPaths.resolve(
            platform = Platform.Windows,
            env = mapOf("USERPROFILE" to "C:/Users/Alice"),
            userHomeProperty = "C:/JvmHome",
        )

        assertEquals(File("C:/Users/Alice"), paths.userHomeDir)
        assertEquals(File("C:/Users/Alice/AppData/Roaming"), paths.configHomeDir)
        assertEquals(File("C:/Users/Alice/AppData/Roaming/husi"), paths.dataDir)
    }
}
