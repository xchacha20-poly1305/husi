package fr.husi

import dev.nucleusframework.autolaunch.AutoLaunch
import dev.nucleusframework.autolaunch.AutoLaunchConfig
import dev.nucleusframework.autolaunch.AutoLaunchResult
import fr.husi.ktx.Logs
import fr.husi.ktx.deleteIfExists
import fr.husi.ktx.invariantPathString
import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.resolve
import kotlinx.coroutines.runBlocking

/** Outcome of a [DesktopAutoStart.setEnabled] call, for the settings row to report. */
internal enum class AutoStartResult {
    /** The system now agrees with the requested state. */
    Applied,

    /** No packaged launcher to register, which is every dev run. */
    Unsupported,

    /** The user forbade auto-start in system settings; those settings were opened. */
    BlockedByUser,

    Failed,
}

internal object DesktopAutoStart {

    private const val EXECUTABLE_TYPE_PROPERTY = "nucleus.executable.type"

    /** Null in dev runs, where the app is only reachable through a multi-part java command. */
    private var packagedLauncher: PlatformFile? = null
    private var initialized = false

    private fun ensureInitialized() {
        if (initialized) return
        initialized = true

        val launcher = resolvePackagedLauncherExecutable()
        packagedLauncher = launcher
        if (launcher == null) {
            Logs.w("desktop auto-start is unavailable outside packaged installs")
            return
        }

        AutoLaunchConfig.executablePath = launcher.invariantPathString()
        // Consumed on Windows only, appended verbatim to the Run registry command line so
        // both flags arrive as separate arguments. The Linux systemd unit and the macOS
        // SMAppService registration cannot carry arguments; wasStartedAtLogin covers them.
        AutoLaunchConfig.autostartArgument = "--autostart --background"
        // The value name the previous handwritten implementation registered.
        AutoLaunchConfig.registryValueName = "Husi"
        // AutoLaunch short-circuits login detection when the executable type resolves to
        // DEV (the default without the Nucleus Gradle plugin); any non-DEV, non-sandbox
        // value lifts it. Left unset on Windows: notification-windows derives its Start
        // Menu shortcut policy from this property, and detection there relies on the CLI
        // flags instead.
        when (PlatformInfo.platform) {
            Platform.Linux -> System.setProperty(EXECUTABLE_TYPE_PROPERTY, "DEB")
            Platform.MacOs -> System.setProperty(EXECUTABLE_TYPE_PROPERTY, "DMG")
            else -> {}
        }

        migrateLegacyAutoStartEntry()
    }

    fun setEnabled(enabled: Boolean): AutoStartResult {
        ensureInitialized()
        if (packagedLauncher == null) {
            Logs.w("ignore desktop auto-start toggle: unsupported in this install")
            return AutoStartResult.Unsupported
        }
        return runCatching {
            val result = if (enabled) {
                AutoLaunch.enable()
            } else {
                AutoLaunch.disable()
            }
            when (result) {
                AutoLaunchResult.OK, AutoLaunchResult.UNCHANGED -> AutoStartResult.Applied

                AutoLaunchResult.BLOCKED_BY_USER -> {
                    Logs.w("desktop auto-start is disabled by the user in system settings")
                    AutoLaunch.openSystemSettings()
                    AutoStartResult.BlockedByUser
                }

                else -> {
                    Logs.w("update desktop auto-start: $result")
                    AutoStartResult.Failed
                }
            }
        }.getOrElse {
            Logs.e("update desktop auto-start", it)
            AutoStartResult.Failed
        }
    }

    fun wasStartedAtLogin(args: Array<String>): Boolean {
        ensureInitialized()
        if (packagedLauncher == null) return false
        return runCatching {
            AutoLaunch.wasStartedAtLogin(args)
        }.getOrElse {
            Logs.w("detect login launch", it)
            false
        }
    }

    private fun migrateLegacyAutoStartEntry() {
        val legacyEntry = when (PlatformInfo.platform) {
            Platform.Linux -> DesktopPaths.linuxAutostartDir.resolve("fr.husi.desktop")
            Platform.MacOs -> DesktopPaths.macLaunchAgentsDir.resolve("fr.husi.desktop.autostart.plist")
            // Windows reuses the same Run value name, so the old entry carries over as is.
            else -> return
        }
        if (!removeLegacyAutoStartEntry(legacyEntry)) return
        Logs.i("removed legacy auto-start entry ${legacyEntry.invariantPathString()}")
        // The legacy entry only existed while auto-start was enabled; carry that over.
        runCatching {
            AutoLaunch.enable()
        }.onFailure {
            Logs.w("migrate desktop auto-start entry", it)
        }
    }
}

/** @return whether a legacy auto-start entry existed and was removed. */
internal fun removeLegacyAutoStartEntry(entry: PlatformFile): Boolean {
    if (!entry.isRegularFile()) return false
    return runBlocking {
        runCatching {
            entry.deleteIfExists()
            !entry.exists()
        }.getOrDefault(false)
    }
}
