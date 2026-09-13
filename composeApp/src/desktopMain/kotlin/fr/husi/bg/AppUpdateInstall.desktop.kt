package fr.husi.bg

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

actual object AppUpdateInstaller {

    actual val isSupported: Boolean = false

    actual val shizukuAvailability: StateFlow<ShizukuAvailability> =
        MutableStateFlow(ShizukuAvailability.NotInstalled)

    actual fun refreshShizukuAvailability() = Unit

    actual suspend fun requestShizukuPermission(): ShizukuAvailability =
        ShizukuAvailability.NotInstalled

    actual suspend fun install(apk: File): ApkInstallResult =
        throw UnsupportedOperationException("in-app update is Android only")
}
