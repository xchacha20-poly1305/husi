package fr.husi.bg

import android.content.pm.PackageManager
import fr.husi.repository.resolveAndroidRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.time.Duration.Companion.minutes

internal object ShizukuState {

    val availability: StateFlow<ShizukuAvailability>
        field = MutableStateFlow(ShizukuAvailability.NotInstalled)

    private var pendingRequest: CompletableDeferred<Unit>? = null

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }

    private val binderDead = Shizuku.OnBinderDeadListener {
        refresh()
        finishPendingRequest()
    }

    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ ->
        refresh()
        finishPendingRequest()
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun refresh() {
        availability.value = currentAvailability()
    }

    private const val PERMISSION_REQUEST_CODE = 231230

    private val PERMISSION_REQUEST_TIMEOUT = 3.minutes

    suspend fun requestPermission(): ShizukuAvailability {
        if (availability.value != ShizukuAvailability.NotGranted) return availability.value

        val request = CompletableDeferred<Unit>()
        pendingRequest = request
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            withTimeoutOrNull(PERMISSION_REQUEST_TIMEOUT) { request.await() }
        } finally {
            pendingRequest = null
        }
        refresh()
        return availability.value
    }

    private fun finishPendingRequest() {
        pendingRequest?.complete(Unit)
    }

    private fun currentAvailability(): ShizukuAvailability {
        if (!isShizukuInstalled()) return ShizukuAvailability.NotInstalled
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) return ShizukuAvailability.NotRunning
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(false)) {
            return ShizukuAvailability.Unsupported
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return if (granted) ShizukuAvailability.Granted else ShizukuAvailability.NotGranted
    }

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private fun isShizukuInstalled(): Boolean {
        val packageManager = resolveAndroidRepository().context.packageManager
        return runCatching { packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess
    }
}
