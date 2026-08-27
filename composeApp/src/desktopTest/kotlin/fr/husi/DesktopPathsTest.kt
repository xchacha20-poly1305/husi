package fr.husi

import fr.husi.platform.Platform
import io.github.vinceglb.filekit.PlatformFile
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

        assertEquals(PlatformFile("/config"), paths.configHomeDir)
        assertEquals(PlatformFile("/config/husi"), paths.dataDir)
        assertEquals(PlatformFile("/config/autostart"), paths.linuxAutostartDir)
        assertEquals(PlatformFile("/config/systemd/user"), paths.linuxSystemdUserDir)
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

        assertEquals(PlatformFile("/home/alice"), paths.userHomeDir)
        assertEquals(PlatformFile("/home/alice/.config"), paths.configHomeDir)
        assertEquals(PlatformFile("/home/alice/.config/husi"), paths.dataDir)
    }

    @Test
    fun `macos data dir uses application support under HOME`() {
        val paths = DesktopPaths.resolve(
            platform = Platform.MacOs,
            env = mapOf("HOME" to "/Users/alice"),
            userHomeProperty = "/jvm-home",
        )

        assertEquals(PlatformFile("/Users/alice"), paths.userHomeDir)
        assertEquals(PlatformFile("/Users/alice/Library/Application Support"), paths.configHomeDir)
        assertEquals(PlatformFile("/Users/alice/Library/Application Support/husi"), paths.dataDir)
        assertEquals(PlatformFile("/Users/alice/Library/LaunchAgents"), paths.macLaunchAgentsDir)
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

        assertEquals(PlatformFile("C:/Users/Alice"), paths.userHomeDir)
        assertEquals(PlatformFile("C:/Users/Alice/AppData/Roaming"), paths.configHomeDir)
        assertEquals(PlatformFile("C:/Users/Alice/AppData/Roaming/husi"), paths.dataDir)
    }

    @Test
    fun `windows data dir falls back to USERPROFILE roaming appdata`() {
        val paths = DesktopPaths.resolve(
            platform = Platform.Windows,
            env = mapOf("USERPROFILE" to "C:/Users/Alice"),
            userHomeProperty = "C:/JvmHome",
        )

        assertEquals(PlatformFile("C:/Users/Alice"), paths.userHomeDir)
        assertEquals(PlatformFile("C:/Users/Alice/AppData/Roaming"), paths.configHomeDir)
        assertEquals(PlatformFile("C:/Users/Alice/AppData/Roaming/husi"), paths.dataDir)
    }
}
