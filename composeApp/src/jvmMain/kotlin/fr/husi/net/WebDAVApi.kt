package fr.husi.net

import okhttp3.HttpUrl
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Streaming
import retrofit2.http.Tag
import retrofit2.http.Url

internal interface WebDAVApi {
    @HTTP(method = HttpMethod.PROPFIND, hasBody = true)
    @Streaming
    fun propfind(
        @Url url: HttpUrl,
        @Header("Depth") depth: Int,
        @Header("Authorization") authorization: String?,
        @Body body: RequestBody,
        @Tag options: HttpRequestOptions,
    ): Call<ResponseBody>

    @HTTP(method = HttpMethod.MKCOL, hasBody = false)
    @Streaming
    fun mkcol(
        @Url url: HttpUrl,
        @Header("Authorization") authorization: String?,
        @Tag options: HttpRequestOptions,
    ): Call<ResponseBody>

    @PUT
    @Streaming
    fun put(
        @Url url: HttpUrl,
        @Header("Authorization") authorization: String?,
        @Body body: RequestBody,
        @Tag options: HttpRequestOptions,
    ): Call<ResponseBody>

    @GET
    @Streaming
    fun download(
        @Url url: HttpUrl,
        @Header("Authorization") authorization: String?,
        @Tag options: HttpRequestOptions,
    ): Call<ResponseBody>

    @DELETE
    @Streaming
    fun delete(
        @Url url: HttpUrl,
        @Header("Authorization") authorization: String?,
        @Tag options: HttpRequestOptions,
    ): Call<ResponseBody>
}
