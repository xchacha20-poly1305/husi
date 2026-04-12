package fr.husi.compose

import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo

internal fun validateMacOsTunInterfaceName(value: String): String? {
    if (value.isBlank()) return null
    if (value.lines().size > 1) return "Unexpected new line"
    return if (value.matches(Regex("""utun\d+"""))) {
        null
    } else {
        "Must match utun<number>"
    }
}

internal fun validateLinuxTunInterfaceName(value: String): String? {
    if (value.isBlank()) return null
    if (value.lines().size > 1) return "Unexpected new line"
    return when {
        value.length > 15 -> "Must be 15 characters or fewer"
        value.any { it == '/' || it == ' ' || it == '@' } -> "Characters '/', space, and '@' are not allowed"
        value.any { it.code !in 0x21..0x7E } -> "Only printable ASCII is allowed"
        else -> null
    }
}

internal fun validateWindowsTunInterfaceName(value: String): String? {
    if (value.isBlank()) return null
    if (value.lines().size > 1) return "Unexpected new line"
    return when {
        value.length > 255 -> "Must be 255 characters or fewer"
        value.any { it in "\\/:*?\"<>|" } -> "Characters \\ / : * ? \" < > | are not allowed"
        else -> null
    }
}

internal fun validateTunInterfaceName(value: String): String? {
    return when (PlatformInfo.platform) {
        Platform.Android -> error("unreachable")
        Platform.Linux -> validateLinuxTunInterfaceName(value)
        Platform.MacOs -> validateMacOsTunInterfaceName(value)
        Platform.Windows -> validateWindowsTunInterfaceName(value)
    }
}
