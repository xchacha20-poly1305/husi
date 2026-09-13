package fr.husi.bg

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

private const val SESSION_FILE_NAME = "base.apk"

private val INSTALL_TIMEOUT = 10.minutes

internal interface ApkInstallSessionHost {

    fun createSession(params: PackageInstaller.SessionParams): Int

    fun openSession(sessionId: Int): PackageInstaller.Session

    fun abandonSession(sessionId: Int)
}

internal object PendingApkInstalls {

    private val pending = ConcurrentHashMap<Int, CompletableDeferred<ApkInstallResult>>()

    fun expect(sessionId: Int): CompletableDeferred<ApkInstallResult> =
        CompletableDeferred<ApkInstallResult>().also { pending[sessionId] = it }

    fun forget(sessionId: Int) {
        pending.remove(sessionId)
    }

    fun complete(sessionId: Int, result: ApkInstallResult) {
        pending.remove(sessionId)?.complete(result)
    }
}

internal suspend fun installApk(
    context: Context,
    host: ApkInstallSessionHost,
    apk: File,
): ApkInstallResult = withContext(Dispatchers.IO) {
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
    params.setAppPackageName(context.packageName)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
    }

    val sessionId = host.createSession(params)
    val result = PendingApkInstalls.expect(sessionId)
    try {
        host.openSession(sessionId).use { session ->
            session.openWrite(SESSION_FILE_NAME, 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            @SuppressLint("RequestInstallPackagesPolicy")
            session.commit(installResultSender(context, sessionId))
        }
    } catch (e: Throwable) {
        PendingApkInstalls.forget(sessionId)
        runCatching { host.abandonSession(sessionId) }
        throw e
    }

    withTimeoutOrNull(INSTALL_TIMEOUT) { result.await() } ?: run {
        PendingApkInstalls.forget(sessionId)
        runCatching { host.abandonSession(sessionId) }
        ApkInstallResult.Failed("the installer did not report a result in $INSTALL_TIMEOUT")
    }
}

private fun installResultSender(context: Context, sessionId: Int) = PendingIntent.getBroadcast(
    context,
    sessionId,
    Intent(context, ApkInstallResultReceiver::class.java),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
).intentSender
