@file:OptIn(ExperimentalCoroutinesApi::class)

package fr.husi.ui.dashboard

import fr.husi.proto.daemon.ClashMode
import fr.husi.proto.daemon.ConnectionEvents
import fr.husi.proto.daemon.Groups
import fr.husi.proto.daemon.OutboundList
import fr.husi.proto.daemon.Status
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
import kotlin.time.Duration

class DashboardViewModelSubscriptionTest : HusiKoinMainDispatcherTest() {

    private class CountingCoreClient : FakeCoreClient() {
        var activeStreams: Int = 0
            private set

        private fun <T> tracked(): Flow<T> = flow {
            activeStreams += 1
            try {
                awaitCancellation()
            } finally {
                activeStreams -= 1
            }
        }

        override fun subscribeStatus(interval: Duration): Flow<Status> = tracked()
        override fun subscribeGroups(): Flow<Groups> = tracked()
        override fun subscribeOutbounds(): Flow<OutboundList> = tracked()
        override fun subscribeConnections(interval: Duration): Flow<ConnectionEvents> = tracked()
        override fun subscribeClashMode(): Flow<ClashMode> = tracked()
    }

    @Test
    fun `setDashboardActive false cancels all gRPC subscriptions`() = runTest(dispatcher.scheduler) {
        val coreClient = CountingCoreClient()
        val viewModel = DashboardViewModel(
            loadPlatformNetworkInfo = { Triple(emptyList(), null, null) },
            coreClient = coreClient,
            computeDispatcher = dispatcher,
        )

        viewModel.setDashboardActive(true)
        viewModel.initialize(isConnected = true)
        advanceUntilIdle()
        assertEquals(5, coreClient.activeStreams)

        viewModel.setDashboardActive(false)
        advanceUntilIdle()
        assertEquals(0, coreClient.activeStreams)

        viewModel.setDashboardActive(true)
        viewModel.initialize(isConnected = true)
        advanceUntilIdle()
        assertEquals(5, coreClient.activeStreams)
    }
}
