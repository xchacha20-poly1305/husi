package fr.husi

import fr.husi.ktx.blankAsNull
import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo
import java.io.File
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
internal fun resolvePackagedLauncherExecutable(): File? {
    resolvePackagedDesktopLauncher()?.let {
        return it
    }

    return System.getProperty("jpackage.app-path")
        ?.blankAsNull()
        ?.let(::File)
        ?.takeIf(File::isFile)
}

internal fun quoteSystemdArgument(argument: String): String {
    if (argument.isEmpty()) return "\"\""
    if (argument.none { it.isWhitespace() || it == '"' || it == '\\' }) return argument

    val escaped = buildString(argument.length) {
        for (char in argument) {
            when (char) {
                '\\', '"' -> {
                    append('\\')
                    append(char)
                }

                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
    return "\"$escaped\""
}

internal fun quoteWindowsArgument(argument: String): String {
    if (argument.isEmpty()) return "\"\""
    if (argument.none { it == ' ' || it == '\t' || it == '"' }) return argument

    val quoted = StringBuilder(argument.length + 2)
    quoted.append('"')
    var slashCount = 0
    for (char in argument) {
        when (char) {
            '\\' -> slashCount++
            '"' -> {
                quoted.append("\\".repeat(slashCount * 2 + 1))
                quoted.append('"')
                slashCount = 0
            }

            else -> {
                if (slashCount > 0) {
                    quoted.append("\\".repeat(slashCount))
                    slashCount = 0
                }
                quoted.append(char)
            }
        }
    }
    if (slashCount > 0) {
        quoted.append("\\".repeat(slashCount * 2))
    }
    quoted.append('"')
    return quoted.toString()
}

internal fun xmlEscape(value: String): String {
    return buildString(value.length) {
        for (char in value) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }
}

/**
 * Resolve based on package path -> try resolving jpackage -> try getting from process
 */
private fun resolveLauncherCommand(): List<String> {
    resolvePackagedLauncherExecutable()?.let {
        return listOf(it.absolutePath)
    }

    resolveCurrentProcessCommand(allowJava = true)?.let {
        return it
    }

    error("Desktop launcher not found")
}

private fun resolvePackagedDesktopLauncher(): File? {
    val codeSource = DesktopAutoStart::class.java.protectionDomain?.codeSource?.location ?: return null
    val runtimePath = runCatching {
        File(codeSource.toURI())
    }.getOrElse {
        File(codeSource.path)
    }
    val appDir = runtimePath.parentFile
        ?.takeIf { runtimePath.isFile && it.name == "app" }
        ?: return null
    val appRoot = appDir.parentFile ?: return null
    return when (PlatformInfo.platform) {
        Platform.Android -> null
        Platform.Linux -> resolveSingleDesktopLauncher(File(appRoot, "bin")) {
            it.canExecute()
        }

        Platform.MacOs -> resolveSingleDesktopLauncher(File(appRoot, "MacOS")) {
            it.canExecute()
        }

        Platform.Windows -> resolveWindowsDesktopLauncher(appRoot)
    }
}

private fun resolveSingleDesktopLauncher(directory: File, predicate: (File) -> Boolean): File? {
    val files = directory.listFiles() ?: return null
    return files.singleOrNull { it.isFile && predicate(it) }
}

private fun resolveWindowsDesktopLauncher(appRoot: File): File? {
    val executables = appRoot.listFiles()
        ?.filter { it.isFile && it.extension.equals("exe", ignoreCase = true) }
        ?: return null
    if (executables.isEmpty()) return null

    val preferredName = System.getProperty("jpackage.app-path")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
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
        ?.let(::File)
        ?.takeIf(File::isFile)
        ?: return null
    val arguments = processInfo.arguments().orElse(null)?.toList().orEmpty()
    val isJavaCommand = command.name.lowercase().startsWith("java")
    if (!isJavaCommand) {
        return buildList {
            add(command.absolutePath)
            addAll(arguments)
        }
    }
    if (!allowJava) return null
    return resolveJavaProcessCommand(command)
}

private fun resolveJavaProcessCommand(javaCommand: File): List<String>? {
    val classPath = System.getProperty("java.class.path")
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return buildList {
        add(javaCommand.absolutePath)
        addAll(ManagementFactory.getRuntimeMXBean().inputArguments)
        add("-cp")
        add(classPath)
        add(DESKTOP_MAIN_CLASS)
    }
}
