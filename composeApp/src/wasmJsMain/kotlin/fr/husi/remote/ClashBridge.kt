@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.remote

import fr.husi.ktx.Logs
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

internal class ClashApiException(val kind: String, val status: Int?, message: String) :
    Exception(message) {
    val unsupported
        get() = status in listOf(404, 405, 501)
}

internal interface ClashApi {
    suspend fun <T> request(request: ClashRequest<T>): T

    fun <T> watch(stream: ClashStream<T>, interval: Int = 1000): Flow<T>

    fun close()
}

internal class ClashBridge(url: String, secret: String) : ClashApi {
    private val session = createClashSession(url, secret, Logs::d)
    private var nextRequestId = 0

    override suspend fun <T> request(request: ClashRequest<T>): T {
        val id = ++nextRequestId
        try {
            val result = session.request(request.operation, clashJson.encodeToString(request.input), id).await()
            return request.decode(clashResult(toKotlinString(result)))
        } finally {
            session.cancelRequest(id)
        }
    }

    override fun <T> watch(stream: ClashStream<T>, interval: Int): Flow<T> = callbackFlow {
        val subscription = session.watch(stream.kind, interval) { value ->
            try {
                trySend(stream.decode(clashResult(value)))
            } catch (failure: Exception) {
                close(failure)
            }
        }
        awaitClose { subscription.close() }
    }

    override fun close() = session.close()
}

internal fun clashResult(value: String): JsonElement {
    val envelope = clashJson.decodeFromString<ClashEnvelope>(value)

    envelope.error?.let { error ->
        throw ClashApiException(error.kind, error.status, error.message)
    }

    return envelope.value
}

@Serializable
private data class ClashEnvelope(
    val value: JsonElement = JsonNull,
    val error: ClashError? = null,
)

@Serializable
private data class ClashError(
    val kind: String,
    val status: Int? = null,
    val message: String,
)

private external interface ClashBrowserSession : JsAny {
    fun request(operation: String, input: String, id: Int): Promise<JsString>

    fun cancelRequest(id: Int)

    fun watch(kind: String, interval: Int, receive: (String) -> Unit): ClashSubscription

    fun close()
}

private external interface ClashSubscription : JsAny {
    fun close()
}

@JsFun("(url, secret, logDebug) => husiBrowser.createClashSession(url, secret, logDebug)")
private external fun createClashSession(
    url: String,
    secret: String,
    logDebug: (String) -> Unit,
): ClashBrowserSession

@JsFun("value => value")
private external fun toKotlinString(value: JsString): String
