package fr.husi

import fr.husi.ktx.blankAsNull
import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.resolve

internal object DesktopPaths {
    private val current: DesktopPathSet by lazy {
        resolve()
    }

    val userHomeDir: PlatformFile by lazy {
        current.userHomeDir
    }

    val configHomeDir: PlatformFile by lazy {
        current.configHomeDir
    }

    val dataDir: PlatformFile by lazy {
        current.dataDir
    }

    val linuxAutostartDir: PlatformFile by lazy {
        current.linuxAutostartDir
    }

    val linuxSystemdUserDir: PlatformFile by lazy {
        current.linuxSystemdUserDir
    }

    val macLaunchAgentsDir: PlatformFile by lazy {
        current.macLaunchAgentsDir
    }

    fun resolve(
        platform: Platform = PlatformInfo.platform,
        env: Map<String, String> = System.getenv(),
        userHomeProperty: String? = System.getProperty("user.home"),
    ): DesktopPathSet {
        fun envFile(name: String): PlatformFile? = env[name]?.blankAsNull()?.let(::PlatformFile)

        val userHomeDir = when (platform) {
            Platform.Android -> error("Unsupported desktop platform")
            Platform.Windows -> envFile("USERPROFILE")
                ?: envFile("HOME")
                ?: userHomeProperty?.blankAsNull()?.let(::PlatformFile)
            Platform.Linux, Platform.MacOs -> envFile("HOME") ?: userHomeProperty?.blankAsNull()?.let(::PlatformFile)
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
    val userHomeDir: PlatformFile,
    val configHomeDir: PlatformFile,
) {
    val dataDir: PlatformFile by lazy {
        configHomeDir.resolve("husi")
    }

    val linuxAutostartDir: PlatformFile by lazy {
        configHomeDir.resolve("autostart")
    }

    val linuxSystemdUserDir: PlatformFile by lazy {
        configHomeDir.resolve("systemd").resolve("user")
    }

    val macLaunchAgentsDir: PlatformFile by lazy {
        userHomeDir.resolve("Library").resolve("LaunchAgents")
    }
}
