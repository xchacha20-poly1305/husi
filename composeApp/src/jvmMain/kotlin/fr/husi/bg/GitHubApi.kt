package fr.husi.bg

import fr.husi.net.HttpRequestOptions
import org.koin.core.context.GlobalContext
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

internal interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("User-Agent") userAgent: String,
        @Header("Authorization") authorization: String? = null,
        @Tag options: HttpRequestOptions = HttpRequestOptions.DEFAULT,
    ): Response<GithubRelease>

    @GET("repos/{owner}/{repo}/releases")
    suspend fun releases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = RELEASE_LIST_PAGE_SIZE,
        @Header("User-Agent") userAgent: String,
        @Header("Authorization") authorization: String? = null,
        @Tag options: HttpRequestOptions = HttpRequestOptions.DEFAULT,
    ): Response<List<GithubRelease>>
}

internal fun resolveGitHubApi(): GitHubApi = GlobalContext.get().get()
