package fr.husi.net

import android.content.Context
import android.net.http.HttpEngine
import android.net.http.HttpException
import android.net.http.UploadDataProvider
import android.net.http.UploadDataSink
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresExtension
import fr.husi.ktx.Logs
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.Timeout.Companion.timeout
import okio.buffer
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass
import kotlin.time.Duration

// In the future, we may provide an evil favor, using cronet from GMS.

private const val HTTP_ENGINE_MIN_EXTENSION = 7
private const val READ_BUFFER_SIZE = 32 * 1024
private const val DEFAULT_UPLOAD_CONTENT_TYPE = "application/octet-stream"
private const val HEADER_CONTENT_TYPE = "Content-Type"
private const val HEADER_CONTENT_ENCODING = "Content-Encoding"
private const val HEADER_CONTENT_LENGTH = "Content-Length"
private const val CONTENT_ENCODING_IDENTITY = "identity"

private val DIRECT_EXECUTOR = Executor { command ->
    command.run()
}

internal fun isHttpEngineSupported(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        return true
    }

    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= HTTP_ENGINE_MIN_EXTENSION
}

internal fun createHttpEngineCallFactory(
    context: Context,
    timeout: Duration = HTTP_TIMEOUT,
): Call.Factory? {
    if (!isHttpEngineSupported()) return null
    return createHttpEngineCallFactoryInternal(context.applicationContext, timeout)
}

@RequiresExtension(extension = Build.VERSION_CODES.S, version = HTTP_ENGINE_MIN_EXTENSION)
private fun createHttpEngineCallFactoryInternal(
    context: Context,
    timeout: Duration,
): Call.Factory? {
    return try {
        val engine = HttpEngine.Builder(context)
            .setEnableHttp2(true)
            .setEnableQuic(true)
            .setEnableBrotli(true)
            .setEnableHttpCache(HttpEngine.Builder.HTTP_CACHE_DISABLED, 0L)
            .build()
        HttpEngineCallFactory(engine, timeout)
    } catch (error: Throwable) {
        Logs.w("HttpEngine unavailable, falling back to OkHttp", error)
        null
    }
}

@RequiresExtension(extension = Build.VERSION_CODES.S, version = HTTP_ENGINE_MIN_EXTENSION)
private class HttpEngineCallFactory(
    private val engine: HttpEngine,
    private val readTimeout: Duration,
) : Call.Factory {
    override fun newCall(request: Request): Call = HttpEngineCall(engine, request, readTimeout)
}

@RequiresExtension(extension = Build.VERSION_CODES.S, version = HTTP_ENGINE_MIN_EXTENSION)
private class HttpEngineCall(
    private val engine: HttpEngine,
    private val request: Request,
    private val readTimeout: Duration,
) : Call {
    private val executed = AtomicBoolean(false)
    private val canceled = AtomicBoolean(false)
    private val urlRequestRef = AtomicReference<UrlRequest?>(null)
    private val events = LinkedBlockingQueue<Event>()
    private val callTimeout = Timeout().apply {
        timeout(readTimeout)
    }

    override fun request(): Request = request

    override fun execute(): Response {
        check(executed.compareAndSet(false, true)) { "Already Executed" }
        if (canceled.get()) {
            throw IOException("Canceled")
        }

        val callback = BridgeCallback(events)
        val builder = engine.newUrlRequestBuilder(request.url.toString(), DIRECT_EXECUTOR, callback)
            .setHttpMethod(request.method)
            .setDirectExecutorAllowed(true)

        for (index in 0 until request.headers.size) {
            builder.addHeader(request.headers.name(index), request.headers.value(index))
        }

        val body = request.body
        if (body != null) {
            val payloadBuffer = Buffer()
            body.writeTo(payloadBuffer)
            val payload = payloadBuffer.readByteArray()
            if (request.header(HEADER_CONTENT_TYPE).isNullOrEmpty()) {
                val mediaType = body.contentType()?.toString() ?: DEFAULT_UPLOAD_CONTENT_TYPE
                builder.addHeader(HEADER_CONTENT_TYPE, mediaType)
            }
            builder.setUploadDataProvider(ByteArrayUploadDataProvider(payload), DIRECT_EXECUTOR)
        }

        val urlRequest = builder.build()
        urlRequestRef.set(urlRequest)
        if (canceled.get()) {
            urlRequest.cancel()
            throw IOException("Canceled")
        }
        urlRequest.start()

        return when (val event = awaitEvent()) {
            is Event.Redirect -> {
                urlRequest.cancel()
                buildResponse(
                    info = event.info,
                    body = ByteArray(0).toResponseBody(),
                    stripEncoding = false,
                )
            }

            is Event.ResponseStarted -> {
                val stripEncoding = hasTransparentContentEncoding(event.info)
                val responseBody = HttpEngineResponseBody(
                    urlRequest = urlRequest,
                    events = events,
                    canceled = canceled,
                    readTimeout = readTimeout,
                    contentType = event.info.firstHeader(HEADER_CONTENT_TYPE)?.toMediaTypeOrNull(),
                    contentLength = if (stripEncoding) {
                        -1L
                    } else {
                        event.info.firstHeader(HEADER_CONTENT_LENGTH)?.toLongOrNull() ?: -1L
                    },
                )
                buildResponse(
                    info = event.info,
                    body = responseBody,
                    stripEncoding = stripEncoding,
                )
            }

            is Event.Failed -> throw event.error
            is Event.Canceled -> throw IOException("Canceled")
            is Event.ReadCompleted, Event.Succeeded -> {
                urlRequest.cancel()
                throw IOException("Unexpected HttpEngine callback state before response start")
            }
        }
    }

    override fun enqueue(responseCallback: Callback) {
        throw UnsupportedOperationException("Async enqueue is not supported by HttpEngineCall")
    }

    override fun cancel() {
        if (canceled.compareAndSet(false, true)) {
            urlRequestRef.get()?.cancel()
            events.offer(Event.Canceled)
        }
    }

    override fun isExecuted(): Boolean = executed.get()

    override fun isCanceled(): Boolean = canceled.get()

    override fun timeout(): Timeout = callTimeout

    override fun clone(): Call = HttpEngineCall(engine, request, readTimeout)

    override fun <T : Any> tag(type: KClass<T>): T? = null

    override fun <T> tag(type: Class<out T>): T? = null

    override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T {
        throw UnsupportedOperationException("Tags are not supported by HttpEngineCall")
    }

    override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T {
        throw UnsupportedOperationException("Tags are not supported by HttpEngineCall")
    }

    override fun addEventListener(eventListener: EventListener) {
        // No-op.
    }

    private fun awaitEvent(): Event {
        val event = try {
            events.poll(readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            cancel()
            throw IOException("Interrupted", interrupted)
        }
        if (event == null) {
            cancel()
            throw SocketTimeoutException("HTTP request timed out")
        }
        return event
    }

    private fun buildResponse(
        info: UrlResponseInfo,
        body: ResponseBody,
        stripEncoding: Boolean,
    ): Response {
        val headersBuilder = Headers.Builder()
        for ((name, value) in info.headers.asList) {
            if (stripEncoding
                && (
                    name.equals(HEADER_CONTENT_ENCODING, ignoreCase = true)
                        || name.equals(HEADER_CONTENT_LENGTH, ignoreCase = true)
                    )
            ) {
                continue
            }
            headersBuilder.addUnsafeNonAscii(name, value)
        }
        return Response.Builder()
            .request(request)
            .protocol(parseHttpEngineProtocol(info.negotiatedProtocol))
            .code(info.httpStatusCode)
            .message(info.httpStatusText)
            .headers(headersBuilder.build())
            .body(body)
            .build()
    }

    private sealed interface Event {
        data class Redirect(val info: UrlResponseInfo) : Event
        data class ResponseStarted(val info: UrlResponseInfo) : Event
        data class ReadCompleted(val buffer: ByteBuffer) : Event
        data object Succeeded : Event
        data class Failed(val error: IOException) : Event
        data object Canceled : Event
    }

    private class BridgeCallback(
        private val events: LinkedBlockingQueue<Event>,
    ) : UrlRequest.Callback {
        override fun onRedirectReceived(
            request: UrlRequest,
            info: UrlResponseInfo,
            newLocationUrl: String,
        ) {
            events.offer(Event.Redirect(info))
        }

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            events.offer(Event.ResponseStarted(info))
        }

        override fun onReadCompleted(
            request: UrlRequest,
            info: UrlResponseInfo,
            byteBuffer: ByteBuffer,
        ) {
            events.offer(Event.ReadCompleted(byteBuffer))
        }

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
            events.offer(Event.Succeeded)
        }

        override fun onFailed(
            request: UrlRequest,
            info: UrlResponseInfo?,
            error: HttpException,
        ) {
            events.offer(Event.Failed(error))
        }

        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
            events.offer(Event.Canceled)
        }
    }

    private class HttpEngineResponseBody(
        private val urlRequest: UrlRequest,
        private val events: LinkedBlockingQueue<Event>,
        private val canceled: AtomicBoolean,
        private val readTimeout: Duration,
        private val contentType: MediaType?,
        private val contentLength: Long,
    ) : ResponseBody() {
        private val closed = AtomicBoolean(false)
        private val finished = AtomicBoolean(false)
        private val bufferedSource: BufferedSource = HttpEngineSource().buffer()

        override fun contentType(): MediaType? = contentType

        override fun contentLength(): Long = contentLength

        override fun source(): BufferedSource = bufferedSource

        override fun close() {
            if (closed.compareAndSet(false, true) && !finished.get()) {
                urlRequest.cancel()
                events.offer(Event.Canceled)
            }
        }

        private inner class HttpEngineSource : Source {
            private val readBuffer: ByteBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE)
            private val sourceTimeout = Timeout().apply {
                timeout(readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            }

            override fun read(sink: Buffer, byteCount: Long): Long {
                require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
                if (byteCount == 0L) return 0L
                if (closed.get() || canceled.get()) {
                    throw IOException("Canceled")
                }
                if (finished.get()) return -1L

                readBuffer.clear()
                if (byteCount < readBuffer.capacity()) {
                    readBuffer.limit(byteCount.toInt())
                }
                urlRequest.read(readBuffer)

                val event = try {
                    events.poll(readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    close()
                    throw IOException("Interrupted", interrupted)
                }
                if (event == null) {
                    close()
                    throw SocketTimeoutException("HTTP read timed out")
                }

                return when (event) {
                    is Event.ReadCompleted -> {
                        val buffer = event.buffer
                        buffer.flip()
                        val bytesRead = buffer.remaining()
                        if (bytesRead == 0) {
                            return read(sink, byteCount)
                        }
                        sink.write(buffer)
                        bytesRead.toLong()
                    }

                    is Event.Succeeded -> {
                        finished.set(true)
                        -1L
                    }

                    is Event.Failed -> {
                        finished.set(true)
                        throw event.error
                    }

                    is Event.Canceled -> {
                        finished.set(true)
                        throw IOException("Canceled")
                    }

                    is Event.Redirect, is Event.ResponseStarted -> {
                        finished.set(true)
                        urlRequest.cancel()
                        throw IOException("Unexpected HttpEngine callback state during body read")
                    }
                }
            }

            override fun timeout(): Timeout = sourceTimeout

            override fun close() {
                this@HttpEngineResponseBody.close()
            }
        }
    }

    private class ByteArrayUploadDataProvider(
        private val payload: ByteArray,
    ) : UploadDataProvider() {
        private var offset = 0

        override fun getLength(): Long = payload.size.toLong()

        override fun read(uploadDataSink: UploadDataSink, byteBuffer: ByteBuffer) {
            val remaining = payload.size - offset
            val length = minOf(remaining, byteBuffer.remaining())
            if (length > 0) {
                byteBuffer.put(payload, offset, length)
                offset += length
            }
            uploadDataSink.onReadSucceeded(false)
        }

        override fun rewind(uploadDataSink: UploadDataSink) {
            offset = 0
            uploadDataSink.onRewindSucceeded()
        }
    }
}

@RequiresExtension(extension = Build.VERSION_CODES.S, version = HTTP_ENGINE_MIN_EXTENSION)
private fun UrlResponseInfo.firstHeader(name: String): String? {
    for ((headerName, value) in headers.asList) {
        if (headerName.equals(name, ignoreCase = true)) {
            return value
        }
    }
    return null
}

@RequiresExtension(extension = Build.VERSION_CODES.S, version = HTTP_ENGINE_MIN_EXTENSION)
private fun hasTransparentContentEncoding(info: UrlResponseInfo): Boolean {
    for ((name, value) in info.headers.asList) {
        if (name.equals(HEADER_CONTENT_ENCODING, ignoreCase = true) &&
            value.isNotBlank() &&
            !value.equals(CONTENT_ENCODING_IDENTITY, ignoreCase = true)
        ) {
            return true
        }
    }
    return false
}

private fun parseHttpEngineProtocol(negotiatedProtocol: String): Protocol {
    val normalized = negotiatedProtocol.lowercase()
    return when {
        normalized == "h2" -> Protocol.HTTP_2
        normalized == "h3" || normalized.startsWith("h3-") -> Protocol.HTTP_3
        normalized.startsWith("quic") -> Protocol.QUIC
        normalized == "http/1.0" -> Protocol.HTTP_1_0
        else -> Protocol.HTTP_1_1
    }
}
