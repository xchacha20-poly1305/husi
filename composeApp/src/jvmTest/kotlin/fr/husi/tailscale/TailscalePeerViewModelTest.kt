package fr.husi.tailscale

import fr.husi.core.remote.CoreTarget
import fr.husi.proto.daemon.TailscalePingResponse
import fr.husi.proto.daemon.tailscaleEndpointStatus
import fr.husi.proto.daemon.tailscalePingResponse
import fr.husi.proto.daemon.tailscaleStatusUpdate
import fr.husi.test.FakeCoreClient
import fr.husi.test.MainDispatcherTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class TailscalePeerViewModelTest : MainDispatcherTest() {

    @Test
    fun `ping history keeps the latest thirty samples`() = runTest(dispatcher.scheduler) {
        val client = object : FakeCoreClient() {
            override fun tailscalePing(tag: String, ip: String): Flow<TailscalePingResponse> = flow {
                repeat(TAILSCALE_PING_HISTORY_SIZE + 1) { index ->
                    emit(tailscalePingResponse { latencyMs = (index + 1).toDouble() })
                }
            }
        }
        client.tailscaleStatusFlow.tryEmit(
            tailscaleStatusUpdate {
                endpoints.add(tailscaleEndpointStatus { endpointTag = "ts" })
            },
        )
        val viewModel = TailscalePeerViewModel(
            endpointTag = "ts",
            peerId = "laptop",
            // Not backgroundScope: advanceUntilIdle stops once only background work is left.
            repository = TailscaleStatusRepository(
                MutableStateFlow(CoreTarget(client)),
                CoroutineScope(dispatcher),
            ),
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()

        viewModel.startPing("100.64.0.2")
        advanceUntilIdle()

        val history = viewModel.uiState.value.ping.latencyHistory
        assertEquals(TAILSCALE_PING_HISTORY_SIZE, history.size)
        assertEquals(2f, history.first())
        assertEquals((TAILSCALE_PING_HISTORY_SIZE + 1).toFloat(), history.last())
        assertFalse(viewModel.uiState.value.ping.running)
    }
}
