@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.remote

import fr.husi.proto.daemon.StartedServiceClient
import fr.husi.proto.daemon.Version
import io.github.xchacha20_poly1305.kurpc.CallInvoker
import io.github.xchacha20_poly1305.kurpc.CallOptions
import io.github.xchacha20_poly1305.kurpc.Channel
import io.github.xchacha20_poly1305.kurpc.ChannelClosedException
import io.github.xchacha20_poly1305.kurpc.Metadata
import io.github.xchacha20_poly1305.kurpc.WebCallOptions
import io.github.xchacha20_poly1305.kurpc.WebChannelConfig
import io.github.xchacha20_poly1305.kurpc.WebStreamTransport
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class BrowserClient(
    private val openChannel: (WebChannelConfig) -> Channel = { Channel(it) },
) : RemoteClient {
    private var channel: Channel? = null

    init {
        onPageHide {
            close()
        }
    }
    override val service get() = StartedServiceClient(SessionCalls(currentChannel()))

    override suspend fun connect(url: String, secret: String): Version {
        close()
        val opened = openChannel(configuration(url, secret))
        channel = opened
        try {
            val version = StartedServiceClient(opened).getVersion(options = CallOptions(timeout = 10.seconds))
            currentCoroutineContext().ensureActive()
            if (channel !== opened) {
                throw ChannelClosedException()
            }
            return version
        } catch (failure: Throwable) {
            if (channel === opened) {
                channel = null
            }
            opened.close()
            throw failure
        }
    }

    override fun close() {
        val previous = channel
        channel = null
        previous?.close()
    }

    override suspend fun testConnection(url: String, secret: String): String =
        openChannel(configuration(url, secret)).use {
            StartedServiceClient(it).getVersion(options = CallOptions(timeout = 10.seconds)).version
        }

    private fun configuration(url: String, secret: String) = WebChannelConfig(
        baseUrl = normalizeUrl(url),
        metadata = if (secret.isEmpty()) {
            Metadata.Empty
        } else {
            Metadata.of("authorization" to "Bearer $secret")
        },
        // Persistent subscriptions must not occupy the HTTP/1.1 fetch connection pool.
        serverStreamingTransport = WebStreamTransport.WebSocket,
        maxMessageBytes = 16 * 1024 * 1024,
    )

    private fun currentChannel() = channel ?: throw ChannelClosedException("Connect to a server first")

    override fun normalizeUrl(value: String): String = normalizeApiUrl(value)
}

private class SessionCalls(private val channel: Channel) : CallInvoker by channel {
    override suspend fun unary(method: String, request: ByteArray, options: CallOptions): ByteArray =
        unaryCall(method, request, options).message

    override suspend fun unaryCall(method: String, request: ByteArray, options: CallOptions) =
        channel.unaryCall(method, request, options.withUnaryTimeout())
}

private fun CallOptions.withUnaryTimeout(): CallOptions = when {
    timeout != null -> this
    this is WebCallOptions -> WebCallOptions(30.seconds, metadata, serverStreamingTransport)
    else -> CallOptions(30.seconds, metadata)
}

@JsFun("""(value) => {
    const url = new URL(value);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
        throw new Error('Use an HTTP or HTTPS API URL');
    }
    if (url.username || url.password || url.search || url.hash) {
        throw new Error('API URL must not contain credentials, a query or a fragment');
    }
    return url.href.endsWith('/') ? url.href.slice(0, -1) : url.href;
}""")
private external fun normalizeApiUrl(value: String): String

@JsFun("(close) => window.addEventListener('pagehide', close)")
private external fun onPageHide(close: () -> Unit)
