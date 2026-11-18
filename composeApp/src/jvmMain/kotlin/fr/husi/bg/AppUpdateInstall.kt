package fr.husi.bg

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.StateFlow

enum class ShizukuAvailability {
    NotInstalled,
    NotRunning,
    Unsupported,
    NotGranted,
    Granted,
}

sealed interface ApkInstallResult {
    data object Success : ApkInstallResult

    data class Failed(val message: String) : ApkInstallResult
}

expect object AppUpdateInstaller {

    val isSupported: Boolean

    val shizukuAvailability: StateFlow<ShizukuAvailability>

    fun refreshShizukuAvailability()

    suspend fun requestShizukuPermission(): ShizukuAvailability

    suspend fun installsWithShizuku(): Boolean

    suspend fun install(apk: PlatformFile): ApkInstallResult
}
