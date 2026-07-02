package fr.husi

import fr.husi.ktx.blankAsNull
import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo
import java.io.File

internal object DesktopPaths {
    private val current: DesktopPathSet by lazy {
        resolve()
    }

    val userHomeDir: File by lazy {
        current.userHomeDir
    }

    val configHomeDir: File by lazy {
        current.configHomeDir
    }

    val dataDir: File by lazy {
        current.dataDir
    }

    val linuxAutostartDir: File by lazy {
        current.linuxAutostartDir
    }

    val linuxSystemdUserDir: File by lazy {
        current.linuxSystemdUserDir
    }

    val macLaunchAgentsDir: File by lazy {
        current.macLaunchAgentsDir
    }

    fun resolve(
        platform: Platform = PlatformInfo.platform,
        env: Map<String, String> = System.getenv(),
        userHomeProperty: String? = System.getProperty("user.home"),
    ): DesktopPathSet {
        fun envFile(name: String): File? = env[name]?.blankAsNull()?.let(::File)

        val userHomeDir = when (platform) {
            Platform.Android -> error("Unsupported desktop platform")
            Platform.Windows -> envFile("USERPROFILE")
                ?: envFile("HOME")
                ?: userHomeProperty?.blankAsNull()?.let(::File)
            Platform.Linux, Platform.MacOs -> envFile("HOME") ?: userHomeProperty?.blankAsNull()?.let(::File)
        } ?: error("Unable to resolve desktop user home directory")

        val configHomeDir = when (platform) {
            Platform.Android -> error("Unsupported desktop platform")
            Platform.Linux -> envFile("XDG_CONFIG_HOME") ?: userHomeDir.resolve(".config")
            Platform.MacOs -> userHomeDir.resolve("Library").resolve("Application Support")
            Platform.Windows -> envFile("APPDATA") ?: userHomeDir.resolve("AppData").resolve("Roaming")
        }

        return DesktopPathSet(
            userHomeDir = userHomeDir,
            configHomeDir = configHomeDir,
        )
    }
}

internal class DesktopPathSet(
    val userHomeDir: File,
    val configHomeDir: File,
) {
    val dataDir: File by lazy {
        configHomeDir.resolve("husi")
    }

    val linuxAutostartDir: File by lazy {
        configHomeDir.resolve("autostart")
    }

    val linuxSystemdUserDir: File by lazy {
        configHomeDir.resolve("systemd").resolve("user")
    }

    val macLaunchAgentsDir: File by lazy {
        userHomeDir.resolve("Library").resolve("LaunchAgents")
    }
}
