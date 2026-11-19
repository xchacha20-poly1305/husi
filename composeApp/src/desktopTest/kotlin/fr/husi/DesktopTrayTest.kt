package fr.husi

import fr.husi.bg.ServiceState
import fr.husi.bg.ServiceStatus
import fr.husi.bg.SpeedStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopTrayTest {

    @Test
    fun `traySummary ignores speed-only updates`() = runTest {
        val status = MutableStateFlow(
            ServiceStatus(
                state = ServiceState.Connected,
                profileName = "Work",
            ),
        )
        val emissions = mutableListOf<TrayServiceSummary>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            status.traySummary().toList(emissions)
        }

        status.value = status.value.copy(
            speed = SpeedStats(txRateProxy = 1024L, rxRateProxy = 2048L),
        )
        status.value = status.value.copy(
            speed = SpeedStats(
                txRateProxy = 4096L,
                rxRateProxy = 8192L,
                txTotal = 16384L,
                rxTotal = 32768L,
            ),
        )
        status.value = status.value.copy(speed = null)

        assertEquals(
            listOf(TrayServiceSummary(state = ServiceState.Connected, profileName = "Work")),
            emissions,
        )
    }

    @Test
    fun `traySummary emits state transitions`() = runTest {
        val status = MutableStateFlow(
            ServiceStatus(
                state = ServiceState.Stopped,
                profileName = "Default",
            ),
        )
        val emissions = mutableListOf<TrayServiceSummary>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            status.traySummary().take(3).toList(emissions)
        }

        status.value = ServiceStatus(state = ServiceState.Connecting, profileName = "Default")
        status.value = ServiceStatus(state = ServiceState.Connected, profileName = "Default")

        assertEquals(
            listOf(
                TrayServiceSummary(state = ServiceState.Stopped, profileName = "Default"),
                TrayServiceSummary(state = ServiceState.Connecting, profileName = "Default"),
                TrayServiceSummary(state = ServiceState.Connected, profileName = "Default"),
            ),
            emissions,
        )
    }

    @Test
    fun `traySummary emits profileName changes`() = runTest {
        val status = MutableStateFlow(
            ServiceStatus(
                state = ServiceState.Connected,
                profileName = null,
            ),
        )
        val emissions = mutableListOf<TrayServiceSummary>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            status.traySummary().take(3).toList(emissions)
        }

        status.value = ServiceStatus(state = ServiceState.Connected, profileName = "Work")
        status.value = ServiceStatus(
            state = ServiceState.Connected,
            profileName = "Work",
            speed = SpeedStats(txRateProxy = 512L),
        )
        status.value = ServiceStatus(state = ServiceState.Connected, profileName = "Home")

        assertEquals(
            listOf(
                TrayServiceSummary(state = ServiceState.Connected, profileName = null),
                TrayServiceSummary(state = ServiceState.Connected, profileName = "Work"),
                TrayServiceSummary(state = ServiceState.Connected, profileName = "Home"),
            ),
            emissions,
        )
    }
}
