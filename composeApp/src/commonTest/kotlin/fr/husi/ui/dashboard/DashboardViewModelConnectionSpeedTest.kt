@file:OptIn(ExperimentalCoroutinesApi::class)

package fr.husi.ui.dashboard

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
import kotlin.test.assertEquals

class DashboardViewModelConnectionSpeedTest : HusiKoinMainDispatcherTest() {

    private val coreClient = FakeCoreClient()

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
        DataStore.trafficConnectionQuery.set(
            (DashboardState.SHOW_TRACKER_ACTIVELY or DashboardState.SHOW_TRACKER_CLOSED).toInt(),
        )
    }

    private fun newViewModel() = DashboardViewModel(
        loadPlatformNetworkInfo = { Triple(emptyList(), null, null) },
        coreClient = coreClient,
    )

    private suspend fun emitNew(vararg ids: String) {
        coreClient.connectionsFlow.emit(
            connectionEvents {
                for (id in ids) {
                    events += connectionEvent {
                        type = ConnectionEventType.CONNECTION_EVENT_NEW
                        this.id = id
                        connection = connection {
                            this.id = id
                            network = "tcp"
                        }
                    }
                }
            },
        )
    }

    private suspend fun emitUpdate(id: String, uplink: Long, downlink: Long) {
        coreClient.connectionsFlow.emit(
            connectionEvents {
                events += connectionEvent {
                    type = ConnectionEventType.CONNECTION_EVENT_UPDATE
                    this.id = id
                    uplinkDelta = uplink
                    downlinkDelta = downlink
                }
            },
        )
    }

    @Test
    fun `update reports the interval delta as speed`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        viewModel.initialize(isConnected = true)
        advanceUntilIdle()

        emitNew("c1")
        emitUpdate("c1", uplink = 100, downlink = 200)
        advanceUntilIdle()

        val connection = viewModel.uiState.value.connections.single()
        assertEquals(100, connection.uploadSpeed)
        assertEquals(200, connection.downloadSpeed)
        assertEquals(100, connection.uploadTotal)
        assertEquals(200, connection.downloadTotal)
    }

    @Test
    fun `going idle drops the speed back to zero without touching totals`() =
        runTest(dispatcher.scheduler) {
            val viewModel = newViewModel()
            viewModel.initialize(isConnected = true)
            advanceUntilIdle()

            emitNew("c1")
            emitUpdate("c1", uplink = 100, downlink = 200)
            emitUpdate("c1", uplink = 0, downlink = 0)
            advanceUntilIdle()

            val connection = viewModel.uiState.value.connections.single()
            assertEquals(0, connection.uploadSpeed)
            assertEquals(0, connection.downloadSpeed)
            assertEquals(100, connection.uploadTotal)
            assertEquals(200, connection.downloadTotal)
        }

    @Test
    fun `close clears the speed`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        viewModel.initialize(isConnected = true)
        advanceUntilIdle()

        emitNew("c1")
        emitUpdate("c1", uplink = 100, downlink = 200)
        coreClient.connectionsFlow.emit(
            connectionEvents {
                events += connectionEvent {
                    type = ConnectionEventType.CONNECTION_EVENT_CLOSED
                    id = "c1"
                    closedAt = 1_000_000
                }
            },
        )
        advanceUntilIdle()

        val connection = viewModel.uiState.value.connections.single()
        assertEquals(0, connection.uploadSpeed)
        assertEquals(0, connection.downloadSpeed)
    }

    @Test
    fun `sorting by download speed ranks the fastest connection first`() =
        runTest(dispatcher.scheduler) {
            DataStore.trafficSortMode.set(TrafficSortMode.DOWNLOAD_SPEED)
            DataStore.trafficDescending.set(true)
            val viewModel = newViewModel()
            viewModel.initialize(isConnected = true)
            advanceUntilIdle()

            emitNew("slow", "fast")
            emitUpdate("slow", uplink = 0, downlink = 10)
            emitUpdate("fast", uplink = 0, downlink = 5000)
            advanceUntilIdle()

            assertEquals(
                listOf("fast", "slow"),
                viewModel.uiState.value.connections.map { it.uuid },
            )
        }
}
