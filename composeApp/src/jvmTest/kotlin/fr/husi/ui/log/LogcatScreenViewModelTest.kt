@file:OptIn(ExperimentalCoroutinesApi::class)

package fr.husi.ui.log

import fr.husi.proto.daemon.Log
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinMainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LogcatScreenViewModelTest : HusiKoinMainDispatcherTest() {

    private class CountingLogClient : FakeCoreClient() {
        var activeStreams: Int = 0
            private set

        override fun subscribeLog(): Flow<Log> = flow {
            activeStreams += 1
            try {
                awaitCancellation()
            } finally {
                activeStreams -= 1
            }
        }
    }

    @Test
    fun `stop cancels the log stream and initialize resumes it`() = runTest(dispatcher.scheduler) {
        val coreClient = CountingLogClient()
        val viewModel = LogcatScreenViewModel(coreClient = coreClient)

        viewModel.initialize(isConnected = true)
        advanceUntilIdle()
        assertEquals(1, coreClient.activeStreams)

        viewModel.stop()
        advanceUntilIdle()
        assertEquals(0, coreClient.activeStreams)

        viewModel.initialize(isConnected = true)
        advanceUntilIdle()
        assertEquals(1, coreClient.activeStreams)
    }
}
