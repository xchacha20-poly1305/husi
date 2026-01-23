package io.nekohasekai.sagernet.utils

import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libcore.Client
import libcore.ConnectionEvent
import libcore.Libcore
import libcore.LogItem

class LibcoreClientManager(
    private val retryDelayMs: Long = 200L,
    private val maxRetryDelayMs: Long = 5000L,
    private val stableResetMs: Long = 5000L,
) {
    private val access = Mutex()
    private var client: Client? = null

    suspend fun <T> withClient(block: suspend (Client) -> T): T {
        return access.withLock {
            val current = client ?: Libcore.newClient().also { client = it }
            try {
                block(current)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                resetLocked()
                throw e
            }
        }
    }

    suspend fun close() {
        access.withLock {
            resetLocked()
        }
    }

    fun subscribeLogs(scope: CoroutineScope, callback: (LogItem) -> Unit): Job {
        return subscribe(scope, "subscribe logs") { client ->
            client.subscribeLogs(callback)
        }
    }

    fun subscribeConnectionEvents(scope: CoroutineScope, callback: (ConnectionEvent) -> Unit): Job {
        return subscribe(scope, "subscribe connection event") { client ->
            client.subscribeConnectionEvent(callback)
        }
    }

    fun subscribeClashMode(scope: CoroutineScope, callback: (String) -> Unit): Job {
        return subscribe(scope, "subscribe clash mode") { client ->
            client.subscribeClashMode(callback)
        }
    }

    private fun subscribe(
        scope: CoroutineScope,
        label: String,
        callback: (Client) -> Unit,
    ): Job = scope.launch(Dispatchers.IO) {
        var delayMs = retryDelayMs
        while (isActive) {
            val subClient = try {
                Libcore.newClient()
            } catch (e: Exception) {
                Logs.w("$label create client", e)
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(maxRetryDelayMs)
                continue
            }
            val startNanos = System.nanoTime()
            try {
                callback(subClient)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w("$label error", e)
            } finally {
                subClient.closeQuietly()
            }
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            delayMs = if (elapsedMs >= stableResetMs) {
                retryDelayMs
            } else {
                (delayMs * 2).coerceAtMost(maxRetryDelayMs)
            }
            delay(delayMs)
        }
    }

    private fun resetLocked() {
        val current = client
        client = null
        current?.closeQuietly()
    }

}

fun Client.closeQuietly(): Result<Unit> {
    return runCatching { close() }
}
