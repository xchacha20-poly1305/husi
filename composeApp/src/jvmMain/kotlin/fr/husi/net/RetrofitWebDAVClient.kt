package fr.husi.net

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.time.Duration.Companion.milliseconds

internal class RetrofitWebDAVClient(private val api: WebDAVApi) : WebDAVClient {
    override suspend fun list(server: WebDAVServer): List<WebDAVEntry> {
        return withHttpDeadline(WEBDAV_TIMEOUT) {
            val session = Session(server)
            session.propfind(depth = 1).awaitAndUse { response ->
                if (response.code() != HttpStatus.MULTI_STATUS) {
                    throw response.statusError()
                }
                val directory = response.raw().request.url.asDirectory()
                val body = response.body() ?: throw IOException("Empty WebDAV listing response")
                readDavListing(body.byteStream(), directory)
            }
        }
    }

    override suspend fun upload(server: WebDAVServer, name: String, content: ByteArray) {
        return withHttpDeadline(WEBDAV_TIMEOUT) {
            val session = Session(server)
            val file = session.file(name)
            val missing = session.propfind(depth = 0).awaitAndUse { response ->
                when (response.code()) {
                    HttpStatus.MULTI_STATUS -> false
                    HttpStatus.NOT_FOUND -> true
                    else -> throw IOException("find directory: ${response.statusError().message}")
                }
            }
            if (missing) {
                session.mkcol().awaitAndUse { response ->
                    if (response.code() != HttpStatus.CREATED &&
                        response.code() != HttpStatus.METHOD_NOT_ALLOWED
                    ) {
                        throw IOException("create directory: ${response.statusError().message}")
                    }
                }
            }
            session.put(file, content.toRequestBody()).awaitAndUse { response ->
                if (response.code() !in PUT_STATUSES) {
                    throw response.statusError()
                }
            }
        }
    }

    override suspend fun download(server: WebDAVServer, name: String): ByteArray {
        return withHttpDeadline(WEBDAV_TIMEOUT) {
            val session = Session(server)
            session.download(session.file(name)).awaitAndUse { response ->
                if (response.code() != HttpStatus.OK) {
                    throw response.statusError()
                }
                response.body()?.bytes() ?: ByteArray(0)
            }
        }
    }

    override suspend fun delete(server: WebDAVServer, name: String) {
        withHttpDeadline(WEBDAV_TIMEOUT) {
            val session = Session(server)
            session.delete(session.file(name)).awaitAndUse { response ->
                if (response.code() !in DELETE_STATUSES) {
                    throw response.statusError()
                }
            }
        }
    }

    private inner class Session(server: WebDAVServer) {
        val directory = server.url.toHttpUrl().withoutCredentials().asDirectory()
        private val authorization = if (server.username.isNotEmpty() || server.password.isNotEmpty()) {
            Credentials.basic(server.username, server.password, UTF_8)
        } else {
            null
        }
        private val options = HttpRequestOptions(
            proxy = server.proxy,
            sameOriginRedirects = true,
        )

        fun file(name: String): HttpUrl {
            require(isDavFileName(name)) { "invalid WebDAV file name" }
            return directory.newBuilder().addPathSegment(name).build()
        }

        fun propfind(depth: Int): Call<ResponseBody> {
            val callOptions = if (depth == 0) {
                options.copy(emptyBodyStatusCodes = setOf(HttpStatus.NOT_FOUND))
            } else {
                options
            }
            return api.propfind(directory, depth, authorization, PROPFIND_BODY, callOptions)
        }

        fun mkcol(): Call<ResponseBody> {
            return api.mkcol(
                directory,
                authorization,
                options.copy(emptyBodyStatusCodes = setOf(HttpStatus.METHOD_NOT_ALLOWED)),
            )
        }

        fun put(url: HttpUrl, body: RequestBody): Call<ResponseBody> {
            return api.put(url, authorization, body, options)
        }

        fun download(url: HttpUrl): Call<ResponseBody> {
            return api.download(url, authorization, options)
        }

        fun delete(url: HttpUrl): Call<ResponseBody> {
            return api.delete(url, authorization, options)
        }
    }

    private companion object {
        val WEBDAV_TIMEOUT = 120_000L.milliseconds
        val PUT_STATUSES = setOf(HttpStatus.OK, HttpStatus.CREATED, HttpStatus.NO_CONTENT)
        val DELETE_STATUSES = setOf(HttpStatus.OK, HttpStatus.ACCEPTED, HttpStatus.NO_CONTENT)
        val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:resourcetype/>
                <d:getcontentlength/>
                <d:getlastmodified/>
              </d:prop>
            </d:propfind>
        """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())
    }
}

private fun HttpUrl.asDirectory(): HttpUrl = if (encodedPath.endsWith('/')) {
    this
} else {
    newBuilder().addPathSegment("").build()
}

internal fun isDavFileName(name: String): Boolean {
    return name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name
}
