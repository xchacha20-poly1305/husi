package fr.husi.net

import okhttp3.HttpUrl
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Streaming
import retrofit2.http.Tag
import retrofit2.http.Url

internal interface RawHttpApi {
    @GET
    @Streaming
    fun fetch(
        @Url url: HttpUrl,
        @HeaderMap headers: Map<String, String>,
        @Tag options: HttpRequestOptions = HttpRequestOptions.DEFAULT,
    ): Call<ResponseBody>
}
