package fr.husi.net

import fr.husi.ktx.kxs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call as OkHttpCall
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okio.Buffer
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.awaitResponse
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.Closeable
import java.io.IOException

internal const val GITHUB_API_BASE_URL = "https://api.github.com/"

internal fun createAppRetrofit(callFactory: OkHttpCall.Factory): Retrofit {
    return Retrofit.Builder()
        .baseUrl(GITHUB_API_BASE_URL)
        .callFactory(callFactory)
        .addConverterFactory(kxs.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()
}

internal suspend fun <T, R> Call<T>.awaitAndUse(
    consume: suspend (Response<T>) -> R,
): R = coroutineScope {
    // Keep cancellation wired through the last body read so timeouts and job cancels abort blocked socket reads.
    val cancelOnExit = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            this@awaitAndUse.cancel()
        }
    }
    try {
        val response = awaitResponse()
        try {
            withContext(Dispatchers.IO) {
                consume(response)
            }
        } finally {
            (response.body() as? Closeable)?.close()
            response.errorBody()?.close()
        }
    } catch (error: IOException) {
        ensureActive()
        throw error
    } finally {
        cancelOnExit.cancel()
    }
}

internal fun Response<*>.statusError(): IOException {
    val status = "HTTP ${code()} ${message()}".trimEnd()
    val errorBody = errorBody() ?: (body() as? ResponseBody)
    if (errorBody == null) {
        return IOException(status)
    }
    return try {
        val preview = errorBody.use { body ->
            val source = body.source()
            val buffer = Buffer()
            val limit = STATUS_ERROR_PREVIEW_BYTES.toLong() + 1L
            while (buffer.size < limit) {
                val read = source.read(buffer, limit - buffer.size)
                if (read == -1L) break
            }
            buffer.readByteArray()
        }
        val detail = preview.decodeToString(
            endIndex = minOf(STATUS_ERROR_PREVIEW_BYTES, preview.size),
        )
        val suffix = if (preview.size > STATUS_ERROR_PREVIEW_BYTES) {
            " ..."
        } else {
            ""
        }
        IOException(
            if (preview.isEmpty()) {
                status
            } else {
                "$status: $detail$suffix"
            },
        )
    } catch (error: IOException) {
        IOException(status, error)
    }
}

internal fun <T : Any> Response<T>.requireOkBody(): T {
    if (code() != HttpStatus.OK) {
        (body() as? Closeable)?.close()
        throw statusError()
    }
    return body() ?: throw IOException("HTTP ${code()} ${message()}".trimEnd() + ": empty body")
}
