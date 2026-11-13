package fr.husi.remote

import fr.husi.database.RemoteBackend
import fr.husi.ktx.Logs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout

internal data class BackendTestResult(val backend: RemoteBackend, val version: String)

internal class BackendDetector(
    private val singBox: RemoteClient,
    private val openClash: (String, String) -> ClashApi = ::ClashBridge,
) {
    suspend fun test(url: String, secret: String, backend: RemoteBackend): BackendTestResult {
        return when (backend) {
            RemoteBackend.SING_BOX -> attempt { nativeTest(url, secret) }.getOrThrow()
            RemoteBackend.CLASH -> attempt { testClash(url, secret) }.getOrThrow()
            RemoteBackend.AUTO ->
                supervisorScope {
                    val native = async { attempt { nativeTest(url, secret) } }
                    val clash = async { attempt { testClash(url, secret) } }
                    val nativeResult = native.await()
                    if (nativeResult.isSuccess) {
                        clash.cancel()
                        nativeResult.getOrThrow()
                    } else {
                        val clashResult = clash.await()
                        clashResult.getOrNull()
                            ?: error("sing-box: ${nativeResult.exceptionOrNull()?.message}\nClash: ${clashResult.exceptionOrNull()?.message}")
                    }
                }
        }
    }

    private suspend fun nativeTest(url: String, secret: String): BackendTestResult {
        val version = singBox.testConnection(url, secret)
        check(version.isNotBlank()) { "Missing sing-box version" }
        return BackendTestResult(RemoteBackend.SING_BOX, version)
    }

    private suspend fun testClash(url: String, secret: String): BackendTestResult {
        val client = openClash(url, secret)
        try {
            val version = client.request(ClashRequests.version).version
            check(version.isNotBlank()) { "Missing Clash version" }
            return BackendTestResult(RemoteBackend.CLASH, version)
        } finally {
            client.close()
        }
    }

    private suspend fun attempt(
        action: suspend () -> BackendTestResult,
    ): Result<BackendTestResult> =
        try {
            Result.success(withTimeout(10000.milliseconds) { action() })
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            if (failure is TimeoutCancellationException) {
                Result.failure(IllegalStateException("Connection test timed out"))
            } else {
                if (failure is CancellationException) throw failure
                Logs.d("Remote backend probe failed", failure)
                Result.failure(failure)
            }
        }
}
