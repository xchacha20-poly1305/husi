package fr.husi.ui.dashboard

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class RemoteUrlTestResult(val time: Long, val delay: Int)

class RemoteUrlTestRunner(
    private val result: (String) -> RemoteUrlTestResult?,
) {

    private companion object {
        val RESULT_TIMEOUT = 10.seconds
        val RESULT_POLL_INTERVAL = 100.milliseconds
    }

    private val permits = Semaphore(GROUP_URL_TEST_CONCURRENCY)

    suspend fun test(tag: String, start: suspend () -> Unit) = permits.withPermit {
        val previous = result(tag)
        start()
        // Failed tests without history have no observable change. Bound the wait so the
        // queue can continue when no result update arrives.
        withTimeoutOrNull(RESULT_TIMEOUT) {
            while (result(tag) == previous) {
                delay(RESULT_POLL_INTERVAL)
            }
        }
        Unit
    }

}
