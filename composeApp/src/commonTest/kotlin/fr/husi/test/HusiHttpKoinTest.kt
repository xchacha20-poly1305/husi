package fr.husi.test

import fr.husi.core.CoreClient
import fr.husi.database.DataStore
import fr.husi.net.HttpFetcher
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/**
 * Base class for tests that exercise code paths touching HTTP via [HttpFetcher].
 *
 * Provides shared [fakeHttp] and [fakeCore] instances, registers them as Koin
 * overrides so production code resolving [HttpFetcher] or [CoreClient] (the
 * sing-box version in the User-Agent) gets the fakes, and resets
 * the [DataStore] configuration backing file so each test starts clean.
 *
 * Code under test that takes [HttpFetcher] via constructor (e.g.
 * `AppUpdateChecker`) should be wired with [fakeHttp] explicitly.
 */
abstract class HusiHttpKoinTest : HusiKoinMainDispatcherTest() {

    protected val fakeHttp = FakeHttpFetcher()
    protected val fakeCore = FakeCoreClient()

    override suspend fun postStartKoin() {
        loadKoinModules(
            module {
                single<HttpFetcher> { fakeHttp }
                single<CoreClient> { fakeCore }
            },
        )
        DataStore.configurationStore.reset()
        postStartKoinWithHttp()
    }

    protected open suspend fun postStartKoinWithHttp() {}
}
