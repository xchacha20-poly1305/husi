package fr.husi.remote

import fr.husi.proto.daemon.Connection
import fr.husi.proto.daemon.ConnectionEvent
import fr.husi.proto.daemon.ConnectionEventType
import fr.husi.proto.daemon.ConnectionEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionEventsTest {
    @Test
    fun `reset snapshot delta and close preserve connection state`() {
        val connections = mutableMapOf("stale" to Connection(id = "stale"))
        fun apply(vararg events: ConnectionEvent, reset: Boolean = false) = applyConnectionEvents(
            connections,
            ConnectionEvents.ADAPTER.decode(ConnectionEvents(events.toList(), reset).encode()),
        )
        apply(ConnectionEvent(id = "a", connection = Connection(id = "a", uplinkTotal = 9007199254740993L, downlinkTotal = 2)), reset = true)
        assertEquals(setOf("a"), connections.keys)
        apply(ConnectionEvent(id = "a", type = ConnectionEventType.CONNECTION_EVENT_UPDATE, uplinkDelta = 7, downlinkDelta = 3))
        assertEquals(9007199254741000L, connections.getValue("a").uplinkTotal)
        assertEquals(5L, connections.getValue("a").downlinkTotal)
        apply(ConnectionEvent(id = "a", type = ConnectionEventType.CONNECTION_EVENT_CLOSED))
        assertEquals(0, connections.size)
        apply(ConnectionEvent(id = "missing", type = ConnectionEventType.CONNECTION_EVENT_UPDATE))
        assertEquals(0, connections.size)
    }

    @Test
    fun `browser retains final closed snapshot and clears instantaneous speed`() {
        val connections = mutableMapOf("a" to Connection(id = "a", uplinkTotal = 12, uplink = 5))
        applyConnectionEvents(connections, ConnectionEvents(listOf(
            ConnectionEvent(id = "a", type = ConnectionEventType.CONNECTION_EVENT_CLOSED, closedAt = 1234),
        )), keepClosed = true)
        assertEquals(1234L, connections.getValue("a").closedAt)
        assertEquals(12L, connections.getValue("a").uplinkTotal)
        assertEquals(0L, connections.getValue("a").uplink)
    }
}
