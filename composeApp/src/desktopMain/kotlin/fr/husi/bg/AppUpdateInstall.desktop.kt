package fr.husi.bg

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual object AppUpdateInstaller {

    actual val isSupported: Boolean = false

    actual val shizukuAvailability: StateFlow<ShizukuAvailability> =
        MutableStateFlow(ShizukuAvailability.NotInstalled)

    actual fun refreshShizukuAvailability() = Unit

    actual suspend fun requestShizukuPermission(): ShizukuAvailability =
        ShizukuAvailability.NotInstalled

    actual suspend fun installsWithShizuku(): Boolean = false

    actual suspend fun install(apk: PlatformFile): ApkInstallResult =
        throw UnsupportedOperationException("in-app update is Android only")
}
