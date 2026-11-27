package fr.husi.test

import fr.husi.group.SIP008Api
import fr.husi.group.SIP008Response
import fr.husi.net.HttpRequestOptions
import okhttp3.HttpUrl
import retrofit2.Response

internal data class RecordedSIP008Call(
    val url: HttpUrl,
    val userAgent: String,
    val authorization: String?,
    val options: HttpRequestOptions,
)

internal class FakeSIP008Api : SIP008Api {
    val calls = mutableListOf<RecordedSIP008Call>()

    var nextThrowable: Throwable? = null
    var nextResponse: SIP008Response = SIP008Response()

    override suspend fun fetch(
        url: HttpUrl,
        userAgent: String,
        authorization: String?,
        options: HttpRequestOptions,
    ): Response<SIP008Response> {
        calls += RecordedSIP008Call(
            url = url,
            userAgent = userAgent,
            authorization = authorization,
            options = options,
        )
        nextThrowable?.let { throw it }
        return Response.success(nextResponse)
    }
}
