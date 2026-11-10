package fr.husi.test

import fr.husi.proto.daemon.StartedServiceClient
import fr.husi.proto.daemon.Version
import fr.husi.remote.RemoteClient
import io.github.xchacha20_poly1305.kurpc.CallInvoker
import io.github.xchacha20_poly1305.kurpc.CallOptions
import io.github.xchacha20_poly1305.kurpc.Metadata
import io.github.xchacha20_poly1305.kurpc.UnaryResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal open class FakeRemoteClient : RemoteClient, CallInvoker {
    override val service = StartedServiceClient(this)
    override suspend fun connect(url: String, secret: String) = Version()
    override fun close() = Unit
    override suspend fun testConnection(url: String, secret: String) = "test"
    override fun normalizeUrl(value: String) = value

    open suspend fun call(method: String, request: ByteArray) = byteArrayOf()
    open fun stream(method: String, request: ByteArray): Flow<ByteArray> = emptyFlow()

    override suspend fun unary(method: String, request: ByteArray, options: CallOptions) =
        call(method.substringAfterLast('/'), request)
    override suspend fun unaryCall(method: String, request: ByteArray, options: CallOptions) =
        UnaryResponse(unary(method, request, options), Metadata.Empty, Metadata.Empty)
    override fun serverStreaming(method: String, request: ByteArray, options: CallOptions) =
        stream(method.substringAfterLast('/'), request)
    override suspend fun clientStreaming(method: String, requests: Flow<ByteArray>, options: CallOptions) =
        error("Unexpected client stream: $method")
    override fun bidiStreaming(method: String, requests: Flow<ByteArray>, options: CallOptions): Flow<ByteArray> =
        error("Unexpected bidirectional stream: $method")
}
