package fr.husi.permission

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import fr.husi.ktx.MIUIUtils

@Composable
fun rememberAndroidPermissionPlatform(): PermissionPlatform {
    val context = LocalContext.current

    var onQueryInstalledAppsResult by remember { mutableStateOf<(Boolean) -> Unit>({}) }
    val queryInstalledAppsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onQueryInstalledAppsResult(granted)
        onQueryInstalledAppsResult = {}
    }

    var onPostNotificationsResult by remember { mutableStateOf<(Boolean) -> Unit>({}) }
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onPostNotificationsResult(granted)
        onPostNotificationsResult = {}
    }

    var onFineLocationResult by remember { mutableStateOf<(Boolean) -> Unit>({}) }
    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onFineLocationResult(granted)
        onFineLocationResult = {}
    }

    var onBackgroundLocationResult by remember { mutableStateOf<(Boolean) -> Unit>({}) }
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onBackgroundLocationResult(granted)
        onBackgroundLocationResult = {}
    }

    var onCameraResult by remember { mutableStateOf<(Boolean) -> Unit>({}) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onCameraResult(granted)
        onCameraResult = {}
    }

    return remember(
        context,
        queryInstalledAppsLauncher,
        postNotificationsLauncher,
        fineLocationLauncher,
        backgroundLocationLauncher,
        cameraLauncher,
    ) {
        object : PermissionPlatform {
            private val supportsPostNotificationPermission: Boolean
                get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            private val needsBackgroundLocationPermission: Boolean
                get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

            override fun hasPermission(permission: AppPermission): Boolean {
                return when (permission) {
                    AppPermission.QueryInstalledApps ->
                        context.hasGrantedPermission(Permission.QueryInstalledApps)

                    AppPermission.PostNotifications -> {
                        if (!supportsPostNotificationPermission) {
                            true
                        } else {
                            context.hasGrantedPermission(Permission.PostNotifications)
                        }
                    }

                    AppPermission.WifiInfo ->
                        context.hasGrantedPermission(Permission.FineLocation) &&
                                (!needsBackgroundLocationPermission ||
                                        context.hasGrantedPermission(Permission.BackgroundLocation))

                    AppPermission.Camera -> context.hasGrantedPermission(Permission.Camera)
                }
            }

            override fun canRequestPermission(permission: AppPermission): Boolean {
                return when (permission) {
                    AppPermission.QueryInstalledApps ->
                        context.hasPlatformPermission(Permission.QueryInstalledApps)

                    AppPermission.PostNotifications -> {
                        if (!supportsPostNotificationPermission) {
                            true
                        } else {
                            context.hasPlatformPermission(Permission.PostNotifications)
                        }
                    }

                    AppPermission.WifiInfo ->
                        context.hasPlatformPermission(Permission.FineLocation) &&
                                (!needsBackgroundLocationPermission ||
                                        context.hasPlatformPermission(Permission.BackgroundLocation))

                    AppPermission.Camera -> context.hasPlatformPermission(Permission.Camera)
                }
            }

            override fun requestPermission(
                permission: AppPermission,
                onResult: (Boolean) -> Unit,
            ) {
                if (!canRequestPermission(permission)) {
                    onResult(false)
                    return
                }
                when (permission) {
                    AppPermission.QueryInstalledApps -> {
                        onQueryInstalledAppsResult = onResult
                        queryInstalledAppsLauncher.launch(Permission.QueryInstalledApps)
                    }

                    AppPermission.PostNotifications -> {
                        if (!supportsPostNotificationPermission) {
                            onResult(true)
                            return
                        }
                        onPostNotificationsResult = onResult
                        postNotificationsLauncher.launch(Permission.PostNotifications)
                    }

                    AppPermission.WifiInfo -> requestWifiInfoPermission(onResult)

                    AppPermission.Camera -> {
                        onCameraResult = onResult
                        cameraLauncher.launch(Permission.Camera)
                    }
                }
            }

            override fun openPermissionSettings() {
                if (MIUIUtils.isMIUI) runCatching {
                    MIUIUtils.openPermissionSettings(context)
                }.onSuccess {
                    return
                }
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = "package:${context.packageName}".toUri()
                context.startActivity(intent)
            }

            private fun requestWifiInfoPermission(onResult: (Boolean) -> Unit) {
                if (hasPermission(AppPermission.WifiInfo)) {
                    onResult(true)
                    return
                }
                if (!context.hasGrantedPermission(Permission.FineLocation)) {
                    onFineLocationResult = { granted ->
                        if (!granted) {
                            onResult(false)
                        } else if (needsBackgroundLocationPermission &&
                            !context.hasGrantedPermission(Permission.BackgroundLocation)
                        ) {
                            onBackgroundLocationResult = onResult
                            backgroundLocationLauncher.launch(Permission.BackgroundLocation)
                        } else {
                            onResult(true)
                        }
                    }
                    fineLocationLauncher.launch(Permission.FineLocation)
                    return
                }
                if (needsBackgroundLocationPermission &&
                    !context.hasGrantedPermission(Permission.BackgroundLocation)
                ) {
                    onBackgroundLocationResult = onResult
                    backgroundLocationLauncher.launch(Permission.BackgroundLocation)
                    return
                }
                onResult(true)
            }
        }
    }
}

private object Permission {
    const val QueryInstalledApps = "com.android.permission.GET_INSTALLED_APPS"

    @SuppressLint("InlinedApi")
    const val PostNotifications = Manifest.permission.POST_NOTIFICATIONS
    const val FineLocation = Manifest.permission.ACCESS_FINE_LOCATION
    const val BackgroundLocation = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    const val Camera = Manifest.permission.CAMERA
}

private fun Context.hasGrantedPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.hasPlatformPermission(permission: String): Boolean = try {
    packageManager.getPermissionInfo(permission, 0) != null
} catch (_: PackageManager.NameNotFoundException) {
    false
}
