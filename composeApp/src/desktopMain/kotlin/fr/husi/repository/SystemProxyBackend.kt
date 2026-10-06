package fr.husi.repository

import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo

internal interface SystemProxyBackend {
    fun enable(host: String, port: Int)
    fun disable()
}

internal fun platformSystemProxyBackend(): SystemProxyBackend {
    return when (PlatformInfo.platform) {
        Platform.Linux -> LinuxSystemProxyBackend()
        Platform.MacOs -> MacOsSystemProxyBackend()
        Platform.Windows -> WindowsSystemProxyBackend
        Platform.Android -> error("system proxy is not controlled by the desktop host on Android")
    }
}

internal fun interface SystemCommandRunner {
    /** Runs [command] and returns its standard output; throws if it exits with a non-zero code. */
    fun run(command: List<String>): String
}

internal object ProcessCommandRunner : SystemCommandRunner {
    override fun run(command: List<String>): String {
        val process = ProcessBuilder(command).start()
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val error = process.errorStream.bufferedReader().use { it.readText().trim() }
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            error.ifBlank { "${command.joinToString(" ")} failed with exit code $exitCode" }
        }
        return output
    }
}
