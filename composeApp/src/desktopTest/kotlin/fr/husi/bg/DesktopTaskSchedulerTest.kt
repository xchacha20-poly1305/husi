package fr.husi.bg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopTaskSchedulerTest {

    @Test
    fun `mac launch agent uses run at load for immediate task`() {
        val schedule = macLaunchAgentSchedule(
            DesktopTaskSchedule(
                repeatIntervalMinutes = 60,
                initialDelaySeconds = 0L,
            ),
        )

        assertTrue(schedule.runAtLoad)
        assertNull(schedule.startIntervalSeconds)
    }

    @Test
    fun `mac launch agent uses one-shot delay for deferred task`() {
        val schedule = macLaunchAgentSchedule(
            DesktopTaskSchedule(
                repeatIntervalMinutes = 60,
                initialDelaySeconds = 600L,
            ),
        )

        assertFalse(schedule.runAtLoad)
        assertEquals(600L, schedule.startIntervalSeconds)
    }
}
