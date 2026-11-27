package fr.husi.group

import fr.husi.net.HttpRequestOptions
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import org.koin.core.context.GlobalContext
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Tag
import retrofit2.http.Url

@Serializable
internal data class SIP008Response(
    @SerialName("bytes_used")
    val bytesUsed: Long? = null,
    @SerialName("bytes_remaining")
    val bytesRemaining: Long? = null,
    val servers: List<SIP008Server> = emptyList(),
)

@Serializable
internal data class SIP008Server(
    val server: String? = null,
    @SerialName("server_port")
    val serverPort: Int? = null,
    val method: String? = null,
    val password: String? = null,
    val remarks: String? = null,
    val plugin: String? = null,
    @SerialName("plugin_opts")
    val pluginOpts: String? = null,
)

internal interface SIP008Api {
    @GET
    suspend fun fetch(
        @Url url: HttpUrl,
        @Header("User-Agent") userAgent: String,
        @Header("Authorization") authorization: String? = null,
        @Tag options: HttpRequestOptions = HttpRequestOptions.DEFAULT,
    ): Response<SIP008Response>
}

internal fun resolveSIP008Api(): SIP008Api = GlobalContext.get().get()
