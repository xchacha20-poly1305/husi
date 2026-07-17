package fr.husi.utils

import fr.husi.ktx.Logs
import fr.husi.libcore.Client
import fr.husi.libcore.ConnectionEvent
import fr.husi.libcore.Libcore
import fr.husi.libcore.LogItem
import fr.husi.libcore.OpenConnectEndpointStatusIterator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class LibcoreClientManager(
    private val retryDelay: Duration = 200.milliseconds,
    private val maxRetryDelay: Duration = 5000.milliseconds,
    private val stableReset: Duration = 5000.milliseconds,
) {
    private val access = Mutex()
    private var client: Client? = null

    suspend fun <T> withClient(block: suspend (Client) -> T): T {
        return access.withLock {
            val client = offer()
            try {
                block(client)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                resetLocked()
                throw e
            }
        }
    }

    private fun offer(): Client {
        return client ?: Libcore.newClient(null).also { client = it }
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

    fun subscribeOpenConnectStatus(
        scope: CoroutineScope,
        callback: (OpenConnectEndpointStatusIterator) -> Unit,
    ): Job {
        return subscribe(scope, "subscribe openconnect status") { client ->
            client.subscribeOpenConnectStatus(callback)
        }
    }

    private fun subscribe(
        scope: CoroutineScope,
        label: String,
        callback: (Client) -> Unit,
    ): Job = scope.launch(Dispatchers.IO) {
        var delayDuration = retryDelay
        while (isActive) {
            val subClient = try {
                Libcore.newClient(null)
            } catch (e: Exception) {
                Logs.w("$label create client", e)
                delay(delayDuration)
                delayDuration = (delayDuration * 2).coerceAtMost(maxRetryDelay)
                continue
            }
            val elapsed = measureTime {
                try {
                    callback(subClient)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logs.w("$label error", e)
                } finally {
                    subClient.closeQuietly()
                }
            }
            delayDuration = if (elapsed >= stableReset) {
                retryDelay
            } else {
                (delayDuration * 2).coerceAtMost(maxRetryDelay)
            }
            delay(delayDuration)
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
