package fr.husi.bg

import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.repository.resolveAndroidRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.time.Duration.Companion.seconds

private val SERVICE_STOP_TIMEOUT = 10.seconds

actual object AppUpdateInstaller {

    actual val isSupported: Boolean = true

    actual val shizukuAvailability: StateFlow<ShizukuAvailability> get() = ShizukuState.availability

    actual fun refreshShizukuAvailability() = ShizukuState.refresh()

    actual suspend fun requestShizukuPermission(): ShizukuAvailability =
        ShizukuState.requestPermission()

    actual suspend fun install(apk: File): ApkInstallResult {
        stopRunningService()

        val context = resolveAndroidRepository().context
        if (DataStore.appUpdateUseShizuku.get() &&
            ShizukuState.availability.value == ShizukuAvailability.Granted
        ) {
            try {
                val host = ShizukuApkInstallSessionHost(context.packageName)
                when (val result = installApk(context, host, apk)) {
                    ApkInstallResult.Success -> return result
                    is ApkInstallResult.Failed -> Logs.w(
                        "shizuku install failed, falling back to the system installer: " +
                            result.message,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.e("shizuku install failed, falling back to the system installer", e)
            }
        }

        return installApk(context, SystemApkInstallSessionHost(context), apk)
    }

    private suspend fun stopRunningService() {
        if (!BackendState.status.value.state.started) return
        try {
            resolveAndroidRepository().stopService()
        } catch (e: Exception) {
            Logs.e("stop the service before installing", e)
            return
        }
        withTimeoutOrNull(SERVICE_STOP_TIMEOUT) {
            BackendState.status.first { !it.state.started }
        } ?: Logs.w("the service is still running after $SERVICE_STOP_TIMEOUT, installing anyway")
    }
}
