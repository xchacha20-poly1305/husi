package fr.husi.test

import fr.husi.group.OOCResponse
import fr.husi.group.OpenOnlineConfigApi
import fr.husi.net.HttpRequestOptions
import okhttp3.HttpUrl
import retrofit2.Response

internal data class RecordedOOCCall(
    val url: HttpUrl,
    val userAgent: String,
    val authorization: String?,
    val options: HttpRequestOptions,
)

internal class FakeOpenOnlineConfigApi : OpenOnlineConfigApi {
    val calls = mutableListOf<RecordedOOCCall>()

    var nextThrowable: Throwable? = null
    var nextResponse: OOCResponse = OOCResponse()

    override suspend fun fetch(
        url: HttpUrl,
        userAgent: String,
        authorization: String?,
        options: HttpRequestOptions,
    ): Response<OOCResponse> {
        calls += RecordedOOCCall(
            url = url,
            userAgent = userAgent,
            authorization = authorization,
            options = options,
        )
        nextThrowable?.let { throw it }
        return Response.success(nextResponse)
    }
}
