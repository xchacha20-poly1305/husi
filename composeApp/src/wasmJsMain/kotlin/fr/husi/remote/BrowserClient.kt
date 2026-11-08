@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.remote

import fr.husi.resources.Res
import fr.husi.resources.remote_stream_overflow
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.getString

/** [RemoteClient] over the `husiRpc` bundle that `index.html` loads. */
internal class BrowserClient : RemoteClient {
    override suspend fun connect(url: String, secret: String): JsonObject =
        parseObject(jsString(connectRpc(url, secret).await()))

    override suspend fun call(method: String, request: JsonObject): JsonObject =
        parseObject(jsString(callRpc(method, request.toString()).await()))

    override fun stream(method: String, request: JsonObject): Flow<JsonObject> = callbackFlow {
        val overflowMessage = getString(Res.string.remote_stream_overflow)
        val id =
            subscribeRpc(
                method,
                request.toString(),
                { value ->
                    if (trySend(parseObject(value)).isFailure)
                        close(IllegalStateException(overflowMessage))
                },
                { error -> close(if (error.isEmpty()) null else IllegalStateException(error)) },
            )
        awaitClose { cancelRpc(id) }
    }

    override fun bidi(method: String, requests: Flow<JsonObject>): Flow<JsonObject> = callbackFlow {
        val overflowMessage = getString(Res.string.remote_stream_overflow)
        var id: Int? = null
        val sender = launch {
            requests.collect { request ->
                val current = id
                if (current == null) {
                    id =
                        subscribeRpc(
                            method,
                            request.toString(),
                            { value ->
                                if (trySend(parseObject(value)).isFailure)
                                    close(IllegalStateException(overflowMessage))
                            },
                            { error ->
                                close(if (error.isEmpty()) null else IllegalStateException(error))
                            },
                        )
                } else sendRpc(current, method, request.toString())
            }
            id?.let(::halfCloseRpc)
        }
        awaitClose {
            sender.cancel()
            id?.let(::cancelRpc)
        }
    }

    override fun close() = disconnectRpc()

    override suspend fun testConnection(url: String, secret: String): String =
        parseObject(jsString(testConnectionRpc(url, secret).await())).text("version")

    override fun normalizeUrl(value: String): String = normalizeUrlRpc(value)
}

@JsFun("(url,secret) => husiRpc.connect(url,secret)")
private external fun connectRpc(url: String, secret: String): Promise<JsString>

@JsFun("(method,json) => husiRpc.unary(method,json)")
private external fun callRpc(method: String, json: String): Promise<JsString>

@JsFun("(method,json,data,end) => husiRpc.subscribe(method,json,data,end)")
private external fun subscribeRpc(
    method: String,
    json: String,
    data: (String) -> Unit,
    end: (String) -> Unit,
): Int

@JsFun("(id,method,json) => husiRpc.send(id,method,json)")
private external fun sendRpc(id: Int, method: String, json: String)

@JsFun("(id) => husiRpc.halfClose(id)") private external fun halfCloseRpc(id: Int)

@JsFun("(id) => husiRpc.cancel(id)") private external fun cancelRpc(id: Int)

@JsFun("() => husiRpc.disconnect()") private external fun disconnectRpc()

@JsFun("(url,secret) => husiRpc.testConnection(url,secret)")
private external fun testConnectionRpc(url: String, secret: String): Promise<JsString>

@JsFun("(value) => husiRpc.normalizeUrl(value)")
private external fun normalizeUrlRpc(value: String): String

@JsFun("(value) => value") private external fun jsString(value: JsString): String
