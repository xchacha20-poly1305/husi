@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.remote

import com.squareup.wire.GrpcCall
import com.squareup.wire.GrpcClient
import com.squareup.wire.GrpcClientStreamingCall
import com.squareup.wire.GrpcException
import com.squareup.wire.GrpcMethod
import com.squareup.wire.GrpcServerStreamingCall
import com.squareup.wire.GrpcStatus
import com.squareup.wire.GrpcStreamingCall
import com.squareup.wire.MessageSink
import com.squareup.wire.MessageSource
import fr.husi.ktx.BrowserBytes
import fr.husi.ktx.Logs
import fr.husi.ktx.toBrowserBytes
import fr.husi.ktx.toByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okio.IOException
import okio.Timeout
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class BrowserGrpcClient(
    private val baseUrl: String,
    private val secret: String,
    private val openExchange: (
        url: String,
        path: String,
        metadata: Map<String, String>,
        request: ByteArray?,
        timeout: Duration?,
    ) -> BrowserRpcExchange = ::openBrowserRpc,
) : GrpcClient(), AutoCloseable {
    private val active = mutableSetOf<BrowserCall<*, *>>()
    private var closed = false

    override fun close() {
        closed = true
        for (call in active.toList()) {
            call.cancel()
        }
    }

    override fun <S : Any, R : Any> newCall(method: GrpcMethod<S, R>): GrpcCall<S, R> = UnaryCall(method)

    override fun <S : Any, R : Any> newServerStreamingCall(
        method: GrpcMethod<S, R>,
    ): GrpcServerStreamingCall<S, R> = ServerStreamingCall(method)

    override fun <S : Any, R : Any> newStreamingCall(method: GrpcMethod<S, R>): GrpcStreamingCall<S, R> {
        return StreamingCall(method)
    }

    override fun <S : Any, R : Any> newClientStreamingCall(
        method: GrpcMethod<S, R>,
    ): GrpcClientStreamingCall<S, R> = error("Client streaming is not used by the web dashboard")

    private abstract inner class BrowserCall<S : Any, R : Any>(
        val method: GrpcMethod<S, R>,
        duration: Duration? = null,
    ) {
        val timeout = BrowserCallTimeout(duration)
        var requestMetadata: Map<String, String> = emptyMap()
        var responseMetadata: Map<String, String>? = null
            private set
        private var executed = false
        private var canceled = false
        private var exchange: BrowserRpcExchange? = null

        fun cancel() {
            canceled = true
            exchange?.close()
        }

        fun isCanceled(): Boolean = canceled

        fun isExecuted(): Boolean = executed

        fun <T : BrowserCall<S, R>> copySettings(target: T): T = target.apply {
            timeout.duration = this@BrowserCall.timeout.duration
            requestMetadata = this@BrowserCall.requestMetadata
        }

        suspend fun <T> executeRpc(request: S?, block: suspend (BrowserRpcExchange) -> T): T {
            check(!executed) { "RPC already executed" }
            executed = true
            if (closed) {
                throw closedSession()
            }
            if (canceled) {
                throw GrpcException(GrpcStatus.CANCELLED, "RPC canceled")
            }
            val headers = if (secret.isEmpty()) {
                requestMetadata
            } else {
                requestMetadata + ("authorization" to "Bearer $secret")
            }
            val rpc = openExchange(
                baseUrl,
                method.path,
                headers,
                request?.let(method.requestAdapter::encode),
                timeout.duration,
            )
            exchange = rpc
            active.add(this)
            try {
                return block(rpc)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                if (closed) throw closedSession()
                val status = rpc.status
                if (status != 0) {
                    throw GrpcException(GrpcStatus.get(status), rpc.statusMessage)
                }
                throw failure
            } finally {
                responseMetadata = rpc.responseMetadata
                active.remove(this)
                exchange = null
                rpc.close()
            }
        }

        fun stream(
            scope: CoroutineScope,
            request: S?,
            onDone: () -> Unit = {},
            send: suspend (BrowserRpcExchange) -> Unit,
        ): ReceiveChannel<R> {
            val responses = Channel<R>(1)
            val job = scope.launch {
                try {
                    executeRpc(request) { rpc ->
                        coroutineScope {
                            val writer = launch {
                                send(rpc)
                            }
                            try {
                                while (true) {
                                    val message = rpc.receive() ?: break
                                    responses.send(method.responseAdapter.decode(message))
                                }
                            } finally {
                                writer.cancel()
                            }
                        }
                    }
                    responses.close()
                } catch (failure: Exception) {
                    responses.close(failure)
                    if (failure is CancellationException) throw failure
                }
            }
            responses.invokeOnClose { job.cancel() }
            job.invokeOnCompletion { failure ->
                onDone()
                responses.close(failure)
            }
            return responses
        }
    }

    private inner class UnaryCall<S : Any, R : Any>(method: GrpcMethod<S, R>) :
        BrowserCall<S, R>(method, DEFAULT_UNARY_TIMEOUT), GrpcCall<S, R> {
        override suspend fun execute(request: S): R {
            val started = TimeSource.Monotonic.markNow()
            Logs.d("RPC ${method.path} started")
            try {
                val result = executeRpc(request) { rpc ->
                    val message = rpc.receive()
                        ?: throw GrpcException(GrpcStatus.INTERNAL, "Missing unary response")
                    if (rpc.receive() != null) {
                        throw GrpcException(GrpcStatus.INTERNAL, "Multiple unary responses")
                    }
                    method.responseAdapter.decode(message)
                }
                Logs.d("RPC ${method.path} completed in ${started.elapsedNow()}")
                return result
            } catch (failure: Exception) {
                if (failure !is CancellationException) {
                    Logs.w("RPC ${method.path} failed after ${started.elapsedNow()}", failure)
                }
                throw failure
            }
        }

        override fun enqueue(request: S, callback: GrpcCall.Callback<S, R>) {
            CoroutineScope(Dispatchers.Default).launch {
                val response = try {
                    execute(request)
                } catch (failure: IOException) {
                    callback.onFailure(this@UnaryCall, failure)
                    return@launch
                }
                callback.onSuccess(this@UnaryCall, response)
            }
        }

        override fun executeBlocking(request: S): R = blockingUnsupported()

        override fun clone(): GrpcCall<S, R> = copySettings(UnaryCall(method))
    }

    private inner class ServerStreamingCall<S : Any, R : Any>(method: GrpcMethod<S, R>) :
        BrowserCall<S, R>(method), GrpcServerStreamingCall<S, R> {
        override suspend fun executeIn(scope: CoroutineScope, request: S): ReceiveChannel<R> {
            // Download chunks use Fetch backpressure. Other streams leave the HTTP/1.1 pool free.
            if (method.path == "/daemon.StartedService/DownloadTaildropFile") {
                return stream(scope, request) {}
            }
            return stream(scope, null) { rpc ->
                rpc.send(method.requestAdapter.encode(request))
                rpc.finish()
            }
        }

        override fun executeBlocking(request: S): MessageSource<R> = blockingUnsupported()

        override fun clone(): GrpcServerStreamingCall<S, R> = copySettings(ServerStreamingCall(method))
    }

    private inner class StreamingCall<S : Any, R : Any>(method: GrpcMethod<S, R>) :
        BrowserCall<S, R>(method), GrpcStreamingCall<S, R> {
        override fun executeIn(scope: CoroutineScope): Pair<SendChannel<S>, ReceiveChannel<R>> {
            val requests = Channel<S>(1)
            val responses = stream(scope, null, onDone = { requests.cancel() }) { rpc ->
                for (request in requests) {
                    rpc.send(method.requestAdapter.encode(request))
                }
                rpc.finish()
            }
            return requests to responses
        }

        @Deprecated("Provide a coroutine scope")
        override fun execute(): Pair<SendChannel<S>, ReceiveChannel<R>> =
            executeIn(CoroutineScope(Dispatchers.Default))

        override fun executeBlocking(): Pair<MessageSink<S>, MessageSource<R>> = blockingUnsupported()

        override fun clone(): GrpcStreamingCall<S, R> = copySettings(StreamingCall(method))
    }
}

private val DEFAULT_UNARY_TIMEOUT = 30.seconds

internal class BrowserCallTimeout(var duration: Duration?) : Timeout()

internal fun closedSession(): GrpcException =
    GrpcException(GrpcStatus.UNAVAILABLE, "Browser RPC session closed")

private fun blockingUnsupported(): Nothing = error("Blocking gRPC calls are unavailable in the browser")

internal interface BrowserRpcExchange : AutoCloseable {
    val status: Int
    val statusMessage: String
    val responseMetadata: Map<String, String>?
    suspend fun send(bytes: ByteArray)
    suspend fun finish()
    suspend fun receive(): ByteArray?
}

private fun openBrowserRpc(
    url: String,
    path: String,
    metadata: Map<String, String>,
    request: ByteArray?,
    timeout: Duration?,
): BrowserRpcExchange {
    val timeoutMs = timeout?.inWholeMilliseconds
        ?.coerceIn(1, Int.MAX_VALUE.toLong())
        ?.toInt()
        ?: 0
    val exchange = openGrpcWeb(
        url,
        path,
        Json.encodeToString(metadata),
        request?.toBrowserBytes(),
        timeoutMs,
    )
    return object : BrowserRpcExchange {
        override val status get() = exchange.status
        override val statusMessage get() = exchange.statusMessage
        override val responseMetadata
            get() = exchange.responseMetadata?.let {
                Json.decodeFromString<Map<String, String>>(it)
            }

        override suspend fun send(bytes: ByteArray) {
            exchange.send(bytes.toBrowserBytes()).await()
        }

        override suspend fun finish() {
            exchange.finish().await()
        }

        override suspend fun receive(): ByteArray? =
            exchange.receive().await()?.toByteArray()

        override fun close() = exchange.close()
    }
}

private external interface JsGrpcWebExchange : JsAny {
    val status: Int
    val statusMessage: String
    val responseMetadata: String?
    fun send(bytes: BrowserBytes): Promise<JsAny?>
    fun finish(): Promise<JsAny?>
    fun receive(): Promise<BrowserBytes?>
    fun close()
}

@JsFun("(url, path, metadata, body, timeout) => husiBrowser.openGrpcWeb(url, path, metadata, body, timeout)")
private external fun openGrpcWeb(
    url: String,
    path: String,
    metadata: String,
    body: BrowserBytes?,
    timeout: Int,
): JsGrpcWebExchange
