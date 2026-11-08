package fr.husi.proto

import fr.husi.proto.daemon.ConnectionEvents
import fr.husi.proto.daemon.ConnectionEventType
import fr.husi.proto.daemon.LogLevel
import fr.husi.proto.daemon.connection
import fr.husi.proto.daemon.connectionEvent
import fr.husi.proto.daemon.connectionEvents
import fr.husi.proto.v1.ServiceOptions
import fr.husi.proto.v1.serviceOptions
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the wiring of the shared `proto/` tree into the shared source set: the
 * generated messages, their Kotlin DSL and the protobuf runtime all have to be
 * reachable from common code, not only from one platform.
 *
 * Both trees are covered, because they are generated differently. The vendored
 * sing-box schema is the contract husi shares with the original on the wire,
 * while husi's own schema imports from it.
 */
class ProtoWiringTest {

    @Test
    fun `vendored sing-box messages round trip through bytes`() {
        val events = connectionEvents {
            reset = true
            events += connectionEvent {
                type = ConnectionEventType.CONNECTION_EVENT_UPDATE
                id = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                uplinkDelta = 1024
                downlinkDelta = 2048
                connection = connection {
                    inbound = "mixed-in"
                    destination = "example.com:443"
                    chainList += "proxy"
                }
            }
        }

        val decoded = ConnectionEvents.parseFrom(events.toByteArray())

        assertEquals(events, decoded)
        assertEquals(1024, decoded.eventsList.single().uplinkDelta)
    }

    @Test
    fun `husi messages carry types imported from the vendored schema`() {
        val options = serviceOptions {
            logLevel = LogLevel.DEBUG
        }

        val decoded = ServiceOptions.parseFrom(options.toByteArray())

        assertEquals(LogLevel.DEBUG, decoded.logLevel)
    }
}
