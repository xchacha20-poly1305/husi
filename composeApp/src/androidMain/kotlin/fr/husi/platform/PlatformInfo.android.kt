package fr.husi.platform

actual object PlatformInfo {
    actual val platform: Platform = Platform.Android
    actual val isAndroid: Boolean
        get() = platform == Platform.Android
    actual val isLinux: Boolean
        get() = platform == Platform.Linux
    actual val isMacOs: Boolean
        get() = platform == Platform.MacOs
    actual val isWindows: Boolean
        get() = platform == Platform.Windows
}
