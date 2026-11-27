package fr.husi.group

import fr.husi.net.HttpRequestOptions
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import org.koin.core.context.GlobalContext
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Tag
import retrofit2.http.Url

@Serializable
internal data class OOCResponse(
    val protocols: List<String> = emptyList(),
    val username: String? = null,
    val bytesUsed: Long? = null,
    val bytesRemaining: Long? = null,
    val expiryDate: Long? = null,
    val shadowsocks: List<OOCShadowsocksProfile> = emptyList(),
)

@Serializable
internal data class OOCShadowsocksProfile(
    val name: String? = null,
    val address: String? = null,
    val port: Int? = null,
    val method: String? = null,
    val password: String? = null,
    val pluginName: String? = null,
    val pluginOptions: String? = null,
)

internal interface OpenOnlineConfigApi {
    @GET
    suspend fun fetch(
        @Url url: HttpUrl,
        @Header("User-Agent") userAgent: String,
        @Header("Authorization") authorization: String? = null,
        @Tag options: HttpRequestOptions = HttpRequestOptions.DEFAULT,
    ): Response<OOCResponse>
}

internal fun resolveOpenOnlineConfigApi(): OpenOnlineConfigApi = GlobalContext.get().get()
