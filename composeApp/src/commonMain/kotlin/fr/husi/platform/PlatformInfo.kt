package fr.husi.platform

enum class Platform {
    Android,
    Linux,
    MacOs,
    Windows,
}

expect object PlatformInfo {
    val platform: Platform
    val isAndroid: Boolean
    val isLinux: Boolean
    val isMacOs: Boolean
    val isWindows: Boolean
}
