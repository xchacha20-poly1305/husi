package fr.husi.bg

import kotlinx.coroutines.flow.StateFlow
import java.io.File

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

    suspend fun install(apk: File): ApkInstallResult
}
