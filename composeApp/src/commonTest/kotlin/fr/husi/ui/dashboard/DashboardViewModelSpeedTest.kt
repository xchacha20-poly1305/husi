package fr.husi.ui.dashboard

import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.bg.SpeedStats
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinMainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelSpeedTest : HusiKoinMainDispatcherTest() {

    @AfterTest
    fun resetBackendState() {
        BackendState.reset()
    }

    private fun newViewModel() = DashboardViewModel(
        loadPlatformNetworkInfo = { Triple(emptyList(), null, null) },
        coreClient = FakeCoreClient(),
    )

    @Test
    fun `speed sample appends combined proxy and direct history`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        BackendState.updateState(ServiceState.Connected, "profile")
        BackendState.updateSpeed(
            SpeedStats(
                txRateProxy = 100,
                rxRateProxy = 200,
                txRateDirect = 10,
                rxRateDirect = 20,
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(100, state.txRateProxy)
        assertEquals(200, state.rxRateProxy)
        assertEquals(10, state.txRateDirect)
        assertEquals(20, state.rxRateDirect)
        assertEquals(SPEED_HISTORY_SIZE, state.proxySpeedHistory.size)
        assertEquals(SPEED_HISTORY_SIZE, state.directSpeedHistory.size)
        assertEquals(300f, state.proxySpeedHistory.last())
        assertEquals(30f, state.directSpeedHistory.last())
        assertTrue(state.proxySpeedHistory.dropLast(1).all { it == 0f })
        assertTrue(state.directSpeedHistory.dropLast(1).all { it == 0f })
    }

    @Test
    fun `state-only update does not append a duplicate sample`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val speed = SpeedStats(txRateProxy = 1, rxRateProxy = 1)
        BackendState.updateState(ServiceState.Connected, "profile")
        BackendState.updateSpeed(speed)
        advanceUntilIdle()

        BackendState.updateState(ServiceState.Connected, "renamed")
        advanceUntilIdle()

        val history = viewModel.uiState.value.proxySpeedHistory
        assertEquals(1, history.count { it != 0f })
        assertEquals(2f, history.last())
    }

    @Test
    fun `disconnect clears speed rates and history`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        BackendState.updateState(ServiceState.Connected, "profile")
        BackendState.updateSpeed(SpeedStats(txRateProxy = 50, rxRateProxy = 50))
        advanceUntilIdle()

        BackendState.updateState(ServiceState.Stopped)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.txRateProxy)
        assertEquals(0, state.rxRateProxy)
        assertEquals(0, state.txRateDirect)
        assertEquals(0, state.rxRateDirect)
        assertTrue(state.proxySpeedHistory.all { it == 0f })
        assertTrue(state.directSpeedHistory.all { it == 0f })
    }

    @Test
    fun `equal rates from a new sample still slide the window`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        BackendState.updateState(ServiceState.Connected, "profile")
        BackendState.updateSpeed(SpeedStats(rxRateProxy = 8))
        advanceUntilIdle()
        BackendState.updateSpeed(SpeedStats(rxRateProxy = 8))
        advanceUntilIdle()

        val history = viewModel.uiState.value.proxySpeedHistory
        assertEquals(listOf(8f, 8f), history.takeLast(2))
        assertEquals(SPEED_HISTORY_SIZE - 2, history.count { it == 0f })
    }
}
