package fr.husi

import fr.husi.ktx.blankAsNull
import fr.husi.ktx.canExecute
import fr.husi.ktx.invariantPathString
import fr.husi.ktx.listOrEmpty
import fr.husi.ktx.platformFileFromUrl
import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo
import fr.husi.repository.husiCoreBinaryName
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.nameWithoutExtension
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.resolve
import java.lang.management.ManagementFactory

private const val DESKTOP_MAIN_CLASS = "fr.husi.DesktopMainKt"

internal fun buildLauncherCommand(vararg arguments: String): List<String> {
    return buildList {
        addAll(resolveLauncherCommand())
        addAll(arguments)
    }
}

/**
 * The single launcher executable of a packaged install, or null when running from a
 * dev environment where the app is only reachable through a multi-part java command.
 */
internal fun resolvePackagedLauncherExecutable(): PlatformFile? {
    resolvePackagedDesktopLauncher()?.let {
        return it
    }

    return System.getProperty("jpackage.app-path")
        ?.blankAsNull()
        ?.let(::PlatformFile)
        ?.takeIf { it.isRegularFile() }
}

/**
 * Resolve based on package path -> try resolving jpackage -> try getting from process
 */
private fun resolveLauncherCommand(): List<String> {
    resolvePackagedLauncherExecutable()?.let {
        return listOf(it.invariantPathString())
    }

    resolveCurrentProcessCommand(allowJava = true)?.let {
        return it
    }

    error("Desktop launcher not found")
}

private fun resolvePackagedDesktopLauncher(): PlatformFile? {
    val codeSource = DesktopAutoStart::class.java.protectionDomain?.codeSource?.location ?: return null
    val runtimePath = platformFileFromUrl(codeSource)
    val appDir = runtimePath.parent()
        ?.takeIf { runtimePath.isRegularFile() && it.name == "app" }
        ?: return null
    val appRoot = appDir.parent() ?: return null
    // Linux and macOS packaging name the launcher after the jar it starts, and put the
    // core host pair (husi-core plus its anja library) in the very same directory, so
    // "the only executable here" identifies nothing.
    val launcherName = runtimePath.nameWithoutExtension
    return when (PlatformInfo.platform) {
        Platform.Android -> null
        Platform.Linux -> resolveNamedDesktopLauncher(appRoot.resolve("bin"), launcherName)
        Platform.MacOs -> resolveNamedDesktopLauncher(appRoot.resolve("MacOS"), launcherName)
        Platform.Windows -> resolveWindowsDesktopLauncher(appRoot)
    }
}

internal fun resolveNamedDesktopLauncher(directory: PlatformFile, launcherName: String): PlatformFile? {
    return directory.resolve(launcherName).takeIf { it.isRegularFile() && it.canExecute() }
}

private fun resolveWindowsDesktopLauncher(appRoot: PlatformFile): PlatformFile? {
    val coreHostName = husiCoreBinaryName()
    val executables = appRoot.listOrEmpty()
        .filter { it.isRegularFile() && it.extension.equals("exe", ignoreCase = true) }
        // The core host lives beside the launcher; it is never the launcher.
        .filterNot { it.name.equals(coreHostName, ignoreCase = true) }
    if (executables.isEmpty()) return null

    val preferredName = System.getProperty("jpackage.app-path")
        ?.takeIf { it.isNotBlank() }
        ?.let(::PlatformFile)
        ?.name
        ?.lowercase()
    if (preferredName != null) {
        executables.firstOrNull { it.name.lowercase() == preferredName }?.let { return it }
    }

    executables.firstOrNull { it.nameWithoutExtension.equals(appRoot.name, ignoreCase = true) }?.let { return it }
    return executables.singleOrNull()
}

private fun resolveCurrentProcessCommand(allowJava: Boolean): List<String>? {
    val processInfo = ProcessHandle.current().info()
    val command = processInfo.command().orElse(null)
        ?.takeIf { it.isNotBlank() }
        ?.let(::PlatformFile)
        ?.takeIf { it.isRegularFile() }
        ?: return null
    val arguments = processInfo.arguments().orElse(null)?.toList().orEmpty()
    val isJavaCommand = command.name.lowercase().startsWith("java")
    if (!isJavaCommand) {
        return buildList {
            add(command.invariantPathString())
            addAll(arguments)
        }
    }
    if (!allowJava) return null
    return resolveJavaProcessCommand(command)
}

private fun resolveJavaProcessCommand(javaCommand: PlatformFile): List<String>? {
    val classPath = System.getProperty("java.class.path")
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return buildList {
        add(javaCommand.invariantPathString())
        addAll(ManagementFactory.getRuntimeMXBean().inputArguments)
        add("-cp")
        add(classPath)
        add(DESKTOP_MAIN_CLASS)
    }
}
