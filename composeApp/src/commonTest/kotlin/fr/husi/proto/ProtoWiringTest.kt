package fr.husi.proto

import fr.husi.proto.daemon.Connection
import fr.husi.proto.daemon.ConnectionEvent
import fr.husi.proto.daemon.ConnectionEventType
import fr.husi.proto.daemon.ConnectionEvents
import fr.husi.proto.daemon.Log
import fr.husi.proto.daemon.LogLevel
import fr.husi.proto.daemon.ServiceStatus
import fr.husi.proto.daemon.TailscaleSSHClientMessage
import fr.husi.proto.daemon.TailscaleSSHInput
import fr.husi.proto.daemon.TailscaleSSHStart
import fr.husi.proto.v1.ServiceOptions
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Shared schemas and Wire adapters must compile and round-trip on JVM and Wasm. */
class ProtoWiringTest {

    @Test
    fun `vendored sing-box messages round trip through bytes`() {
        val events = ConnectionEvents(
            reset = true,
            events = listOf(
                ConnectionEvent(
                    type = ConnectionEventType.CONNECTION_EVENT_UPDATE,
                    id = "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                    uplinkDelta = 1024,
                    downlinkDelta = 2048,
                    connection = Connection(
                        inbound = "mixed-in",
                        destination = "example.com:443",
                        chainList = listOf("proxy"),
                    ),
                ),
            ),
        )

        val decoded = ConnectionEvents.ADAPTER.decode(events.encode())

        assertEquals(events, decoded)
        assertEquals(1024, decoded.events.single().uplinkDelta)
    }

    @Test
    fun `husi messages carry types imported from the vendored schema`() {
        val options = ServiceOptions(log_level = LogLevel.DEBUG)

        val decoded = ServiceOptions.ADAPTER.decode(options.encode())

        assertEquals(LogLevel.DEBUG, decoded.log_level)
    }

    @Test
    fun `absent and explicitly empty nested messages remain distinct`() {
        assertNull(ConnectionEvent.ADAPTER.decode(ConnectionEvent().encode()).connection)
        val event = ConnectionEvent(connection = Connection())
        assertNotNull(ConnectionEvent.ADAPTER.decode(event.encode()).connection)
    }

    @Test
    fun `oneof cannot carry two request arms`() {
        assertFailsWith<IllegalArgumentException> {
            TailscaleSSHClientMessage(start = TailscaleSSHStart(), input = TailscaleSSHInput())
        }
    }

    @Test
    fun `unknown API enum values remain typed and round trip on every platform`() {
        val bytes = byteArrayOf(8, 99)
        val status = ServiceStatus.ADAPTER.decode(bytes)
        val event = ConnectionEvent.ADAPTER.decode(bytes)
        val message = Log.Message.ADAPTER.decode(bytes)

        assertEquals(99, assertIs<ServiceStatus.Type.Unrecognized>(status.status).value)
        assertEquals(99, assertIs<ConnectionEventType.Unrecognized>(event.type).value)
        assertEquals(99, assertIs<LogLevel.Unrecognized>(message.level).value)
        assertContentEquals(bytes, status.encode())
        assertContentEquals(bytes, event.encode())
        assertContentEquals(bytes, message.encode())
    }

}
