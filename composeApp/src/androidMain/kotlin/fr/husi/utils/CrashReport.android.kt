package fr.husi.utils

import android.os.Build
import fr.husi.ktx.Logs
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Properties
import java.util.regex.Pattern

internal actual fun buildPlatformSystemInfoReport(): String {
    var report = ""
    report += "PLATFORM: Android\n"
    report += "OS_VERSION: ${getSystemProperty("os.version")}\n"
    report += "SDK_INT: ${Build.VERSION.SDK_INT}\n"
    report += if ("REL" == Build.VERSION.CODENAME) {
        "RELEASE: ${Build.VERSION.RELEASE}"
    } else {
        "CODENAME: ${Build.VERSION.CODENAME}"
    } + "\n"
    report += "ID: ${Build.ID}\n"
    report += "DISPLAY: ${Build.DISPLAY}\n"
    report += "INCREMENTAL: ${Build.VERSION.INCREMENTAL}\n"

    val systemProperties = getSystemProperties()

    report += "SECURITY_PATCH: ${systemProperties.getProperty("ro.build.version.security_patch")}\n"
    report += "IS_DEBUGGABLE: ${systemProperties.getProperty("ro.debuggable")}\n"
    report += "IS_EMULATOR: ${systemProperties.getProperty("ro.boot.qemu")}\n"
    report += "IS_TREBLE_ENABLED: ${systemProperties.getProperty("ro.treble.enabled")}\n"

    report += "TYPE: ${Build.TYPE}\n"
    report += "TAGS: ${Build.TAGS}\n\n"

    report += "MANUFACTURER: ${Build.MANUFACTURER}\n"
    report += "BRAND: ${Build.BRAND}\n"
    report += "MODEL: ${Build.MODEL}\n"
    report += "PRODUCT: ${Build.PRODUCT}\n"
    report += "BOARD: ${Build.BOARD}\n"
    report += "HARDWARE: ${Build.HARDWARE}\n"
    report += "DEVICE: ${Build.DEVICE}\n"
    report += "SUPPORTED_ABIS: ${
        Build.SUPPORTED_ABIS.filter { it.isNotBlank() }.joinToString(", ")
    }\n\n"
    return report
}

private fun getSystemProperties(): Properties {
    val systemProperties = Properties()
    val propertiesPattern = Pattern.compile("^\\[([^]]+)]: \\[(.+)]$")
    try {
        val process = ProcessBuilder().command("/system/bin/getprop")
            .redirectErrorStream(true)
            .start()
        val inputStream = process.inputStream
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        var key: String?
        var value: String?
        while (bufferedReader.readLine().also { line = it } != null) {
            val matcher = propertiesPattern.matcher(line!!)
            if (matcher.matches()) {
                key = matcher.group(1)
                value = matcher.group(2)
                if (!key.isNullOrBlank() && !value.isNullOrBlank()) {
                    systemProperties[key] = value
                }
            }
        }
        bufferedReader.close()
        process.destroy()
    } catch (e: IOException) {
        Logs.e(
            "Failed to run \"/system/bin/getprop\" to get system properties.", e,
        )
    }
    return systemProperties
}

private fun getSystemProperty(property: String): String? {
    return try {
        System.getProperty(property)
    } catch (e: Exception) {
        Logs.e("Failed to get system property \"$property\":${e.message}")
        null
    }
}
