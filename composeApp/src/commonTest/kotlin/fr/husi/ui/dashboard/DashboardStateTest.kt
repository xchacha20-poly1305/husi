package fr.husi.ui.dashboard

import fr.husi.ui.dashboard.DashboardState.Companion.SHOW_TRACKER_ACTIVELY
import fr.husi.ui.dashboard.DashboardState.Companion.SHOW_TRACKER_CLOSED
import kotlin.experimental.or
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardStateTest {

    private val showBoth = SHOW_TRACKER_ACTIVELY or SHOW_TRACKER_CLOSED
    private val showNone: Byte = 0

    @Test
    fun `empty reason follows the status filter`() {
        assertEquals(
            EmptyConnectionsReason.NO_CONNECTIONS,
            DashboardState(queryOptions = showBoth).emptyConnectionsReason,
        )
        assertEquals(
            EmptyConnectionsReason.NO_ACTIVE,
            DashboardState(queryOptions = SHOW_TRACKER_ACTIVELY).emptyConnectionsReason,
        )
        assertEquals(
            EmptyConnectionsReason.NO_CLOSED,
            DashboardState(queryOptions = SHOW_TRACKER_CLOSED).emptyConnectionsReason,
        )
    }

    @Test
    fun `search takes precedence over the status filter`() {
        val state = DashboardState(queryOptions = SHOW_TRACKER_ACTIVELY, isSearchingConnections = true)

        assertEquals(EmptyConnectionsReason.NO_MATCH, state.emptyConnectionsReason)
    }

    @Test
    fun `no selected status takes precedence over search`() {
        val state = DashboardState(queryOptions = showNone, isSearchingConnections = true)

        assertEquals(EmptyConnectionsReason.NO_STATUS_SELECTED, state.emptyConnectionsReason)
    }
}
