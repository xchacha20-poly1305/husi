package fr.husi.test

import fr.husi.bg.GitHubApi
import fr.husi.core.CoreClient
import fr.husi.database.DataStore
import fr.husi.database.preference.reset
import fr.husi.group.OpenOnlineConfigApi
import fr.husi.group.SIP008Api
import fr.husi.net.HttpFetcher
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/**
 * Base class for tests that exercise code paths touching HTTP via [HttpFetcher] or Retrofit APIs.
 *
 * Provides shared [fakeHttp], [fakeGitHub], [fakeSIP008], [fakeOOC], and [fakeCore] instances,
 * registers them as Koin overrides so production code gets the fakes, and resets
 * the [DataStore] configuration backing file so each test starts clean.
 *
 * Code under test that takes dependencies via constructor (e.g.
 * `AppUpdateChecker`) should be wired with the corresponding fake explicitly.
 */
abstract class HusiHttpKoinTest : HusiKoinMainDispatcherTest() {

    protected val fakeHttp = FakeHttpFetcher()
    internal val fakeGitHub = FakeGitHubApi()
    internal val fakeSIP008 = FakeSIP008Api()
    internal val fakeOOC = FakeOpenOnlineConfigApi()
    protected val fakeCore = FakeCoreClient()

    override suspend fun postStartKoin() {
        loadKoinModules(
            module {
                single<HttpFetcher> { fakeHttp }
                single<GitHubApi> { fakeGitHub }
                single<SIP008Api> { fakeSIP008 }
                single<OpenOnlineConfigApi> { fakeOOC }
                single<CoreClient> { fakeCore }
            },
        )
        DataStore.configurationStore.reset()
        postStartKoinWithHttp()
    }

    protected open suspend fun postStartKoinWithHttp() {}
}
