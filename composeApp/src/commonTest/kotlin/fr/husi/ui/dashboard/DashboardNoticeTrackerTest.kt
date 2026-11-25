package fr.husi.ui.dashboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardNoticeTrackerTest {

    private val authCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val tailscaleCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    private fun TestScope.newObservedTracker(): DashboardNoticeTracker {
        val tracker = DashboardNoticeTracker(
            noticeCounts = listOf(authCounts, tailscaleCounts),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        backgroundScope.launch { tracker.unreadCount.collect {} }
        return tracker
    }

    @Test
    fun `notice from any source is unread until marked read`() = runTest {
        val tracker = newObservedTracker()
        advanceUntilIdle()
        assertEquals(0, tracker.unreadCount.value)

        tailscaleCounts.value = mapOf("ts-login" to 1)
        advanceUntilIdle()
        assertEquals(1, tracker.unreadCount.value)

        tracker.markAllRead()
        advanceUntilIdle()
        assertEquals(0, tracker.unreadCount.value)
    }

    @Test
    fun `new auth challenge after reading is unread again`() = runTest {
        val tracker = newObservedTracker()
        authCounts.value = setOf("office-c1").asNoticeCounts()
        advanceUntilIdle()
        tracker.markAllRead()
        advanceUntilIdle()

        authCounts.value = setOf("office-c2").asNoticeCounts()
        advanceUntilIdle()
        assertEquals(1, tracker.unreadCount.value)
    }

    @Test
    fun `resolved notice does not leave the badge on`() = runTest {
        val tracker = newObservedTracker()
        authCounts.value = setOf("office-c1").asNoticeCounts()
        advanceUntilIdle()
        assertEquals(1, tracker.unreadCount.value)

        authCounts.value = emptyMap()
        advanceUntilIdle()
        assertEquals(0, tracker.unreadCount.value)
    }

    @Test
    fun `only files received since the last read are counted`() = runTest {
        val tracker = newObservedTracker()
        tailscaleCounts.value = mapOf("ts-files" to 2)
        advanceUntilIdle()
        tracker.markAllRead()
        advanceUntilIdle()

        tailscaleCounts.value = mapOf("ts-files" to 1)
        advanceUntilIdle()
        assertEquals(0, tracker.unreadCount.value)

        tailscaleCounts.value = mapOf("ts-files" to 4)
        advanceUntilIdle()
        assertEquals(3, tracker.unreadCount.value)
    }

    @Test
    fun `file received after the inbox was read is unread`() = runTest {
        val tracker = newObservedTracker()
        tailscaleCounts.value = mapOf("ts-files" to 2)
        advanceUntilIdle()
        tracker.markAllRead()
        advanceUntilIdle()

        tailscaleCounts.value = emptyMap()
        advanceUntilIdle()
        tailscaleCounts.value = mapOf("ts-files" to 1)
        advanceUntilIdle()
        assertEquals(1, tracker.unreadCount.value)
    }

    @Test
    fun `count sums notices across sources`() = runTest {
        val tracker = newObservedTracker()
        authCounts.value = setOf("office-c1", "home-c1").asNoticeCounts()
        tailscaleCounts.value = mapOf("ts-login" to 1, "ts-files" to 5)
        advanceUntilIdle()

        assertEquals(8, tracker.unreadCount.value)
    }

    @Test
    fun `sources are not collected until the badge is observed`() = runTest {
        DashboardNoticeTracker(
            noticeCounts = listOf(tailscaleCounts),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()

        assertEquals(0, tailscaleCounts.subscriptionCount.value)
    }
}
