package fr.husi.platform

import java.io.File

internal data class DesktopPackageEnvironment(
    val isFlatpak: Boolean,
    val isAppImage: Boolean,
) {
    val defaultNetworkManagerTun: Boolean get() = isFlatpak || isAppImage
    val canInstallSystemDaemon: Boolean get() = !isFlatpak

    companion object {
        val current = detect()

        fun detect(
            platform: Platform = PlatformInfo.platform,
            environment: Map<String, String> = System.getenv(),
            hasFlatpakInfo: () -> Boolean = { File("/.flatpak-info").isFile },
        ): DesktopPackageEnvironment {
            if (platform != Platform.Linux) {
                return DesktopPackageEnvironment(isFlatpak = false, isAppImage = false)
            }
            fun hasValue(name: String) = !environment[name].isNullOrBlank()
            return DesktopPackageEnvironment(
                isFlatpak = hasValue("FLATPAK_ID") || hasFlatpakInfo(),
                isAppImage = hasValue("APPIMAGE") || hasValue("APPDIR"),
            )
        }
    }
}

internal actual fun defaultNetworkManagerTun(): Boolean =
    DesktopPackageEnvironment.current.defaultNetworkManagerTun
