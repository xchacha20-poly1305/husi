package fr.husi.test

import fr.husi.bg.GitHubApi
import fr.husi.bg.GithubRelease
import fr.husi.net.HttpRequestOptions
import retrofit2.Response

internal data class RecordedGitHubCall(
    val kind: Kind,
    val owner: String,
    val repo: String,
    val perPage: Int?,
    val userAgent: String,
    val authorization: String?,
    val options: HttpRequestOptions,
) {
    enum class Kind {
        LATEST_RELEASE,
        RELEASES,
    }
}

internal class FakeGitHubApi : GitHubApi {
    val calls = mutableListOf<RecordedGitHubCall>()
    val lastCall: RecordedGitHubCall? get() = calls.lastOrNull()

    var nextThrowable: Throwable? = null
    var nextLatestRelease: GithubRelease = GithubRelease()
    var nextReleases: List<GithubRelease> = emptyList()

    override suspend fun latestRelease(
        owner: String,
        repo: String,
        userAgent: String,
        authorization: String?,
        options: HttpRequestOptions,
    ): Response<GithubRelease> {
        calls += RecordedGitHubCall(
            kind = RecordedGitHubCall.Kind.LATEST_RELEASE,
            owner = owner,
            repo = repo,
            perPage = null,
            userAgent = userAgent,
            authorization = authorization,
            options = options,
        )
        nextThrowable?.let { throw it }
        return Response.success(nextLatestRelease)
    }

    override suspend fun releases(
        owner: String,
        repo: String,
        perPage: Int,
        userAgent: String,
        authorization: String?,
        options: HttpRequestOptions,
    ): Response<List<GithubRelease>> {
        calls += RecordedGitHubCall(
            kind = RecordedGitHubCall.Kind.RELEASES,
            owner = owner,
            repo = repo,
            perPage = perPage,
            userAgent = userAgent,
            authorization = authorization,
            options = options,
        )
        nextThrowable?.let { throw it }
        return Response.success(nextReleases)
    }
}
