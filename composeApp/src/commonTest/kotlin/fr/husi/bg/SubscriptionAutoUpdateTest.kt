package fr.husi.bg

import fr.husi.database.ProxyGroup
import fr.husi.database.SubscriptionBean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SubscriptionAutoUpdateTest {

    @Test
    fun `planner uses shortest interval and earliest due time`() {
        val nowSeconds = 10_000L
        val plan = SubscriptionAutoUpdatePlanner.plan(
            subscriptions = listOf(
                autoUpdateGroup(name = "slow", delayMinutes = 60, lastUpdated = (nowSeconds - 30 * 60).toInt()),
                autoUpdateGroup(name = "fast", delayMinutes = 15, lastUpdated = (nowSeconds - 5 * 60).toInt()),
            ),
            nowSeconds = nowSeconds,
        )

        assertEquals(15, plan?.repeatIntervalMinutes)
        assertEquals(10 * 60L, plan?.initialDelaySeconds)
    }

    @Test
    fun `planner clamps non-positive interval to one minute`() {
        val plan = SubscriptionAutoUpdatePlanner.plan(
            subscriptions = listOf(
                autoUpdateGroup(
                    name = "clamped",
                    delayMinutes = 0,
                    lastUpdated = 100,
                ),
            ),
            nowSeconds = 100,
        )

        assertEquals(1, plan?.repeatIntervalMinutes)
        assertEquals(60L, plan?.initialDelaySeconds)
    }

    @Test
    fun `due subscriptions treat overdue profiles as due immediately`() {
        val dueSubscriptions = SubscriptionAutoUpdateRunner.dueSubscriptions(
            subscriptions = listOf(
                autoUpdateGroup(
                    name = "overdue",
                    delayMinutes = 30,
                    lastUpdated = 100,
                ),
                autoUpdateGroup(
                    name = "not-due",
                    delayMinutes = 30,
                    lastUpdated = 100 + 25 * 60,
                ),
            ),
            nowSeconds = 100 + 31L * 60L,
            connected = true,
        )

        assertEquals(listOf("overdue"), dueSubscriptions.map { it.name })
    }

    @Test
    fun `planner returns null when there are no subscriptions`() {
        assertNull(
            SubscriptionAutoUpdatePlanner.plan(
                subscriptions = emptyList(),
                nowSeconds = 10_000L,
            ),
        )
    }

    @Test
    fun `due subscriptions skip connected-only profiles while disconnected`() {
        val dueSubscriptions = SubscriptionAutoUpdateRunner.dueSubscriptions(
            subscriptions = listOf(
                autoUpdateGroup(
                    name = "plain-due",
                    delayMinutes = 15,
                    lastUpdated = 0,
                ),
                autoUpdateGroup(
                    name = "connected-only-due",
                    delayMinutes = 15,
                    lastUpdated = 0,
                    updateWhenConnectedOnly = true,
                ),
                autoUpdateGroup(
                    name = "plain-not-due",
                    delayMinutes = 15,
                    lastUpdated = 15 * 60,
                ),
            ),
            nowSeconds = 20 * 60L,
            connected = false,
        )

        assertEquals(listOf("plain-due"), dueSubscriptions.map { it.name })
    }

    @Test
    fun `due subscriptions keep connected-only profiles while connected`() {
        val dueSubscriptions = SubscriptionAutoUpdateRunner.dueSubscriptions(
            subscriptions = listOf(
                autoUpdateGroup(
                    name = "connected-only-due",
                    delayMinutes = 15,
                    lastUpdated = 0,
                    updateWhenConnectedOnly = true,
                ),
                autoUpdateGroup(
                    name = "plain-due",
                    delayMinutes = 15,
                    lastUpdated = 0,
                ),
                autoUpdateGroup(
                    name = "connected-only-not-due",
                    delayMinutes = 15,
                    lastUpdated = 10 * 60,
                    updateWhenConnectedOnly = true,
                ),
            ),
            nowSeconds = 20 * 60L,
            connected = true,
        )

        assertEquals(
            listOf("connected-only-due", "plain-due"),
            dueSubscriptions.map { it.name },
        )
        assertFalse(dueSubscriptions.any { it.name == "connected-only-not-due" })
    }

    private fun autoUpdateGroup(
        name: String,
        delayMinutes: Int,
        lastUpdated: Int,
        updateWhenConnectedOnly: Boolean = false,
    ): ProxyGroup {
        return ProxyGroup(
            name = name,
            subscription = SubscriptionBean().apply {
                autoUpdate = true
                autoUpdateDelay = delayMinutes
                this.lastUpdated = lastUpdated
                this.updateWhenConnectedOnly = updateWhenConnectedOnly
            },
        )
    }
}
