package fr.husi.permission

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppPermission {
    QueryInstalledApps,
    PostNotifications,
    WifiInfo,
    Camera,
}

interface PermissionPlatform {
    fun hasPermission(permission: AppPermission): Boolean

    fun canRequestPermission(permission: AppPermission): Boolean

    fun requestPermission(permission: AppPermission, onResult: (Boolean) -> Unit = {})

    fun openPermissionSettings()
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
