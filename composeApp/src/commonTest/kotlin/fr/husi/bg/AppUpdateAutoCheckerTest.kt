package fr.husi.bg

import fr.husi.database.DataStore
import fr.husi.test.FakeAppUpdateFetcher
import fr.husi.test.HusiKoinMainDispatcherTest
import fr.husi.test.appUpdateInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AppUpdateAutoCheckerTest : HusiKoinMainDispatcherTest() {

    private companion object {
        const val TODAY = 100L
    }

    private val fetcher = FakeAppUpdateFetcher()

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    private fun checker(isSupported: Boolean = true) = AppUpdateAutoChecker(
        fetcher = fetcher,
        isSupported = isSupported,
        today = { TODAY },
    )

    private suspend fun enableAutoCheck(onlyWhenConnected: Boolean) {
        DataStore.appUpdateAutoCheck.set(true)
        DataStore.appUpdateOnlyWhenConnected.set(onlyWhenConnected)
        DataStore.appUpdateLastCheckEpochDay.set(0L)
        DataStore.appUpdateSkippedVersion.set("")
    }

    @Test
    fun `checkIfDue does not fetch while disconnected when the proxy is required`() =
        runTest(dispatcher.scheduler) {
            enableAutoCheck(onlyWhenConnected = true)

            assertNull(checker().checkIfDue(connected = false))

            assertEquals(0, fetcher.checkCount)
            assertEquals(0L, DataStore.appUpdateLastCheckEpochDay.get())
        }

    @Test
    fun `checkIfDue fetches once the service is connected`() = runTest(dispatcher.scheduler) {
        enableAutoCheck(onlyWhenConnected = true)
        val info = appUpdateInfo()
        fetcher.nextResult = info
        val checker = checker()

        assertNull(checker.checkIfDue(connected = false))
        assertSame(info, checker.checkIfDue(connected = true))

        assertEquals(1, fetcher.checkCount)
        assertEquals(TODAY, DataStore.appUpdateLastCheckEpochDay.get())
    }

    @Test
    fun `checkIfDue does not fetch again on the same day`() = runTest(dispatcher.scheduler) {
        enableAutoCheck(onlyWhenConnected = false)
        fetcher.nextResult = appUpdateInfo()
        val checker = checker()

        checker.checkIfDue(connected = true)
        assertNull(checker.checkIfDue(connected = true))

        assertEquals(1, fetcher.checkCount)
    }

    @Test
    fun `checkIfDue retries on the next call when the fetch failed`() =
        runTest(dispatcher.scheduler) {
            enableAutoCheck(onlyWhenConnected = true)
            fetcher.nextThrowable = Exception("boom")
            val checker = checker()

            assertNull(checker.checkIfDue(connected = true))
            assertEquals(0L, DataStore.appUpdateLastCheckEpochDay.get())

            fetcher.nextThrowable = null
            val info = appUpdateInfo()
            fetcher.nextResult = info

            assertSame(info, checker.checkIfDue(connected = true))
            assertEquals(2, fetcher.checkCount)
        }

    @Test
    fun `checkIfDue hides the skipped version but still marks the day`() =
        runTest(dispatcher.scheduler) {
            enableAutoCheck(onlyWhenConnected = false)
            val info = appUpdateInfo()
            fetcher.nextResult = info
            DataStore.appUpdateSkippedVersion.set(info.version)

            assertNull(checker().checkIfDue(connected = true))

            assertEquals(1, fetcher.checkCount)
            assertEquals(TODAY, DataStore.appUpdateLastCheckEpochDay.get())
        }

    @Test
    fun `checkIfDue does nothing where installing updates is unsupported`() =
        runTest(dispatcher.scheduler) {
            enableAutoCheck(onlyWhenConnected = false)
            fetcher.nextResult = appUpdateInfo()

            assertNull(checker(isSupported = false).checkIfDue(connected = true))

            assertEquals(0, fetcher.checkCount)
        }
}
