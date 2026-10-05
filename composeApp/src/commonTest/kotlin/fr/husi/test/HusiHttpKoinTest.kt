package fr.husi.test

import fr.husi.database.DataStore
import fr.husi.net.HttpFetcher
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/**
 * Base class for tests that exercise code paths touching HTTP via [HttpFetcher].
 *
 * Provides a shared [fakeHttp] instance, registers it as a Koin override so any
 * production code resolving [HttpFetcher] from Koin gets the fake, and resets
 * the [DataStore] configuration backing file so each test starts clean.
 *
 * Code under test that takes [HttpFetcher] via constructor (e.g.
 * `AppUpdateChecker`) should be wired with [fakeHttp] explicitly.
 */
abstract class HusiHttpKoinTest : HusiKoinMainDispatcherTest() {

    protected val fakeHttp = FakeHttpFetcher()

    override suspend fun postStartKoin() {
        loadKoinModules(
            module {
                single<HttpFetcher> { fakeHttp }
            },
        )
        DataStore.configurationStore.reset()
        postStartKoinWithHttp()
    }

    protected open suspend fun postStartKoinWithHttp() {}
}
