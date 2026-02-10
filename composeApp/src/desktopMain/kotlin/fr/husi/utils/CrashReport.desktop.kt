package fr.husi.utils

import java.lang.management.ManagementFactory
import java.util.Locale

private const val MEBI_BYTES = 1024L * 1024L

internal actual fun buildPlatformSystemInfoReport(): String {
    val runtime = Runtime.getRuntime()
    val runtimeMXBean = ManagementFactory.getRuntimeMXBean()
    var report = ""
    report += "PLATFORM: Desktop\n"
    report += "OS_NAME: ${getSystemProperty("os.name")}\n"
    report += "OS_VERSION: ${getSystemProperty("os.version")}\n"
    report += "OS_ARCH: ${getSystemProperty("os.arch")}\n"
    report += "JVM_NAME: ${getSystemProperty("java.vm.name")}\n"
    report += "JVM_VENDOR: ${getSystemProperty("java.vm.vendor")}\n"
    report += "JVM_VERSION: ${getSystemProperty("java.vm.version")}\n"
    report += "JAVA_RUNTIME_VERSION: ${getSystemProperty("java.runtime.version")}\n"
    report += "JAVA_VENDOR: ${getSystemProperty("java.vendor")}\n"
    report += "JAVA_HOME: ${getSystemProperty("java.home")}\n"
    report += "DEFAULT_LOCALE: ${Locale.getDefault().toLanguageTag()}\n"
    report += "CPU_CORES: ${runtime.availableProcessors()}\n"
    report += "MAX_MEMORY_MIB: ${runtime.maxMemory() / MEBI_BYTES}\n"
    report += "TOTAL_MEMORY_MIB: ${runtime.totalMemory() / MEBI_BYTES}\n"
    report += "FREE_MEMORY_MIB: ${runtime.freeMemory() / MEBI_BYTES}\n"
    report += "JVM_UPTIME_MS: ${runtimeMXBean.uptime}\n"
    report += "JVM_ARGS: ${
        runtimeMXBean.inputArguments.takeIf { it.isNotEmpty() }?.joinToString(" ") ?: "(empty)"
    }\n\n"
    return report
}

private fun getSystemProperty(key: String): String {
    return System.getProperty(key).orEmpty()
}
