@file:OptIn(ExperimentalCoroutinesApi::class)

package fr.husi.ui.dashboard

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import fr.husi.TrafficSortMode
import fr.husi.database.DataStore
import fr.husi.proto.daemon.ConnectionEventType
import fr.husi.proto.daemon.connection
import fr.husi.proto.daemon.connectionEvent
import fr.husi.proto.daemon.connectionEvents
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinMainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.experimental.or
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val CLOSED_AT_MILLIS = 1_700_000_000_000L

class DashboardViewModelConnectionListTest : HusiKoinMainDispatcherTest() {

    private val coreClient = FakeCoreClient()

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
        DataStore.trafficSortMode.set(TrafficSortMode.START)
        DataStore.trafficDescending.set(false)
        DataStore.trafficConnectionQuery.set(
            (DashboardState.SHOW_TRACKER_ACTIVELY or DashboardState.SHOW_TRACKER_CLOSED).toInt(),
        )
    }

    private fun newViewModel() = DashboardViewModel(
        loadPlatformNetworkInfo = { Triple(emptyList(), null, null) },
        coreClient = coreClient,
        computeDispatcher = dispatcher,
    )

    private suspend fun emitNew(ids: List<String>, destination: (String) -> String = { "" }) {
        coreClient.connectionsFlow.emit(
            connectionEvents {
                for (id in ids) {
                    events += connectionEvent {
                        type = ConnectionEventType.CONNECTION_EVENT_NEW
                        this.id = id
                        connection = connection {
                            this.id = id
                            network = "tcp"
                            this.destination = destination(id)
                        }
                    }
                }
            },
        )
    }

    private suspend fun emitClosed(ids: List<String>) {
        coreClient.connectionsFlow.emit(
            connectionEvents {
                for (id in ids) {
                    events += connectionEvent {
                        type = ConnectionEventType.CONNECTION_EVENT_CLOSED
                        this.id = id
                        closedAt = CLOSED_AT_MILLIS
                    }
                }
            },
        )
    }

    @Test
    fun `closed connections beyond the cap drop the oldest`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        viewModel.initialize(isConnected = true)
        advanceUntilIdle()

        val overflow = 100
        val ids = List(DashboardViewModel.MAX_CLOSED_CONNECTIONS + overflow) { "c$it" }
        emitNew(ids)
        emitClosed(ids)
        advanceUntilIdle()

        val kept = viewModel.uiState.value.connections.map { it.uuid }
        assertEquals(DashboardViewModel.MAX_CLOSED_CONNECTIONS, kept.size)
        assertFalse(kept.contains(ids.first()))
        assertContains(kept, ids.last())
    }

    @Test
    fun `eviction never drops active connections`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        viewModel.initialize(isConnected = true)
        advanceUntilIdle()

        emitNew(listOf("alive"))
        val closedIds = List(DashboardViewModel.MAX_CLOSED_CONNECTIONS + 10) { "c$it" }
        emitNew(closedIds)
        emitClosed(closedIds)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertContains(state.connections.map { it.uuid }, "alive")
        assertEquals(1, state.activeConnectionCount)
    }

    @Test
    fun `search query filters the published list`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        viewModel.initialize(isConnected = true)
        advanceUntilIdle()

        emitNew(listOf("a", "b")) { id -> if (id == "a") "example.com:443" else "other.net:80" }
        advanceUntilIdle()

        viewModel.searchTextFieldState.setTextAndPlaceCursorAtEnd("example")
        // The write lands in the global snapshot immediately, but the observers behind
        // snapshotFlow only wake on an apply notification. In the app the recomposer sends one
        // every frame; a unit test has no Compose runtime, so it has to send it itself.
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertEquals(listOf("a"), viewModel.uiState.value.connections.map { it.uuid })
        assertEquals(2, viewModel.uiState.value.activeConnectionCount)
    }

    @Test
    fun `status filter hides closed connections`() = runTest(dispatcher.scheduler) {
        DataStore.trafficConnectionQuery.set(DashboardState.SHOW_TRACKER_ACTIVELY.toInt())
        val viewModel = newViewModel()
        viewModel.initialize(isConnected = true)
        advanceUntilIdle()

        emitNew(listOf("a", "b"))
        emitClosed(listOf("b"))
        advanceUntilIdle()

        assertEquals(listOf("a"), viewModel.uiState.value.connections.map { it.uuid })
    }

    @Test
    fun `pause freezes the published list until resumed`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        viewModel.initialize(isConnected = true)
        advanceUntilIdle()

        emitNew(listOf("a"))
        advanceUntilIdle()

        viewModel.togglePause()
        emitNew(listOf("b"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPause)
        assertEquals(listOf("a"), viewModel.uiState.value.connections.map { it.uuid })

        viewModel.togglePause()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), viewModel.uiState.value.connections.map { it.uuid })
    }
}
