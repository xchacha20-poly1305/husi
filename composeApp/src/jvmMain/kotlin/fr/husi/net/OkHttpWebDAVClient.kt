package fr.husi.net

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.time.Duration.Companion.milliseconds

internal class OkHttpWebDAVClient(private val http: AppHttpClient) : WebDAVClient {
    override suspend fun list(server: WebDAVServer): List<WebDAVEntry> {
        return withHttpDeadline(WEBDAV_TIMEOUT) {
            val session = Session(server)
            session.propfind(depth = 1) { response ->
                if (response.code != HttpStatus.MULTI_STATUS) {
                    throw response.statusError()
                }
                val directory = response.request.url.asDirectory()
                readDavListing(response.body.byteStream(), directory)
            }
        }
    }

    override suspend fun upload(server: WebDAVServer, name: String, content: ByteArray) {
        return withHttpDeadline(WEBDAV_TIMEOUT) {
            val session = Session(server)
            val file = session.file(name)
            val missing = session.propfind(depth = 0) { response ->
                when (response.code) {
                    HttpStatus.MULTI_STATUS -> false
                    HttpStatus.NOT_FOUND -> true
                    else -> throw IOException("find directory: ${response.statusError().message}")
                }
            }
            if (missing) {
                session.request(HttpMethod.MKCOL, session.directory) { response ->
                    if (response.code != HttpStatus.CREATED &&
                        response.code != HttpStatus.METHOD_NOT_ALLOWED
                    ) {
                        throw IOException("create directory: ${response.statusError().message}")
                    }
                }
            }
            session.request(HttpMethod.PUT, file, content.toRequestBody()) { response ->
                if (response.code !in PUT_STATUSES) {
                    throw response.statusError()
                }
            }
        }
    }

    override suspend fun download(server: WebDAVServer, name: String): ByteArray {
        return withHttpDeadline(WEBDAV_TIMEOUT) {
            val session = Session(server)
            session.request(HttpMethod.GET, session.file(name)) { response ->
                if (response.code != HttpStatus.OK) {
                    throw response.statusError()
                }
                response.body.bytes()
            }
        }
    }

    override suspend fun delete(server: WebDAVServer, name: String) {
        withHttpDeadline(WEBDAV_TIMEOUT) {
            val session = Session(server)
            session.request(HttpMethod.DELETE, session.file(name)) { response ->
                if (response.code !in DELETE_STATUSES) {
                    throw response.statusError()
                }
            }
        }
    }

    private inner class Session(private val server: WebDAVServer) {
        val directory = server.url.toHttpUrl().withoutCredentials().asDirectory()

        fun file(name: String): HttpUrl {
            require(isDavFileName(name)) { "invalid WebDAV file name" }
            return directory.newBuilder().addPathSegment(name).build()
        }

        suspend fun <T> propfind(depth: Int, consume: (Response) -> T): T {
            return request(HttpMethod.PROPFIND, directory, PROPFIND_BODY, depth, consume)
        }

        suspend fun <T> request(
            method: String,
            url: HttpUrl,
            body: RequestBody? = null,
            depth: Int? = null,
            consume: (Response) -> T,
        ): T {
            val request = Request.Builder().url(url).method(method, body)
            if (depth != null) {
                request.header("Depth", depth.toString())
            }
            if (server.username.isNotEmpty() || server.password.isNotEmpty()) {
                request.header(
                    "Authorization",
                    Credentials.basic(server.username, server.password, UTF_8),
                )
            }
            return http.exchange(
                request = request.build(),
                proxy = server.proxy,
                sameOriginRedirects = true,
            ) { response, _ ->
                consume(response)
            }
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
