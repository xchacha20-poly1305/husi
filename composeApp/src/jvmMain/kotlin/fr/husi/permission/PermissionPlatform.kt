package fr.husi.permission

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

enum class AppPermission {
    QueryInstalledApps,
    PostNotifications,
    WifiInfo,
    Camera,
    LocalNetwork,
    InstallPackages,
}

interface PermissionPlatform {
    fun hasPermission(permission: AppPermission): Boolean

    fun canRequestPermission(permission: AppPermission): Boolean

    fun requestPermission(permission: AppPermission, onResult: (Boolean) -> Unit = {})

    fun openPermissionSettings()
}

internal suspend fun PermissionPlatform.awaitPermission(permission: AppPermission): Boolean =
    suspendCancellableCoroutine { continuation ->
        requestPermission(permission) { granted -> continuation.resume(granted) }
    }

object NoPermissionPlatform : PermissionPlatform {

    override fun hasPermission(permission: AppPermission): Boolean = true

    override fun canRequestPermission(permission: AppPermission): Boolean = true

    override fun requestPermission(permission: AppPermission, onResult: (Boolean) -> Unit) {
        onResult(true)
    }

    override fun openPermissionSettings() {}
}

val LocalPermissionPlatform = staticCompositionLocalOf<PermissionPlatform> {
    NoPermissionPlatform
}
