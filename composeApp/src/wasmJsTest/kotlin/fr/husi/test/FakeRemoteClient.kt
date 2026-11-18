package fr.husi.test

import fr.husi.proto.daemon.GrpcStartedServiceClient
import fr.husi.proto.daemon.Version
import fr.husi.remote.BrowserGrpcClient
import fr.husi.remote.BrowserRpcExchange
import fr.husi.remote.RemoteClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.produceIn

internal open class FakeRemoteClient : RemoteClient {
    override val service = GrpcStartedServiceClient(
        BrowserGrpcClient("http://example.test", "") { _, path, _, request, _ ->
            val method = path.substringAfterLast('/')
            object : BrowserRpcExchange {
                override val status = 0
                override val statusMessage = ""
                override val responseMetadata = null
                private val input = CompletableDeferred<ByteArray>()
                private var responses: ReceiveChannel<ByteArray>? = null
                private var unaryReceived = false

                override suspend fun send(bytes: ByteArray) {
                    input.complete(bytes)
                }

                override suspend fun finish() = Unit

                override suspend fun receive(): ByteArray? {
                    if (request != null) {
                        if (unaryReceived) {
                            return null
                        }
                        unaryReceived = true
                        return call(method, request)
                    }
                    val channel = responses ?: stream(method, input.await())
                        .produceIn(CoroutineScope(currentCoroutineContext()))
                        .also { responses = it }
                    val result = channel.receiveCatching()
                    result.exceptionOrNull()?.let { throw it }
                    return result.getOrNull()
                }

                override fun close() {
                    input.cancel()
                    responses?.cancel()
                }
            }
        },
    )
    override suspend fun connect(url: String, secret: String) = Version()
    override fun close() = Unit
    override suspend fun testConnection(url: String, secret: String) = "test"
    override fun normalizeUrl(value: String) = value

    open suspend fun call(method: String, request: ByteArray) = byteArrayOf()
    open fun stream(method: String, request: ByteArray): Flow<ByteArray> = emptyFlow()
}
