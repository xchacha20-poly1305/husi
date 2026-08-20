package fr.husi.bg.proto

import fr.husi.fmt.TAG_DIRECT
import fr.husi.fmt.TrafficNode
import fr.husi.proto.daemon.ConnectionEventType
import fr.husi.proto.daemon.connection
import fr.husi.proto.daemon.connectionEvent
import fr.husi.proto.daemon.connectionEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class OutboundTrafficAggregatorTest {

    private val proxyGraph = mapOf(
        "proxy" to TrafficNode(profileIDs = setOf(PROXY_ID)),
    )

    @Test
    fun `new seeds totals and update adds deltas`() {
        val aggregator = OutboundTrafficAggregator(proxyGraph)
        aggregator.onEvents(newConnection(uplink = 100, downlink = 200))

        assertEquals(TrafficDelta(100, 200), aggregator.drain().byProfile[PROXY_ID])

        aggregator.onEvents(
            connectionEvents {
                events += connectionEvent {
                    type = ConnectionEventType.CONNECTION_EVENT_UPDATE
                    id = "c1"
                    uplinkDelta = 10
                    downlinkDelta = 20
                }
            },
        )

        assertEquals(TrafficDelta(10, 20), aggregator.drain().byProfile[PROXY_ID])
    }

    @Test
    fun `reset without drain does not double-count same totals`() {
        val aggregator = OutboundTrafficAggregator(proxyGraph)
        aggregator.onEvents(newConnection(uplink = 50, downlink = 50))
        // Reconnect replay with reset must not double-count undrained totals.
        aggregator.onEvents(newConnection(uplink = 50, downlink = 50, reset = true))

        assertEquals(TrafficDelta(50, 50), aggregator.drain().byProfile[PROXY_ID])
    }

    @Test
    fun `reset after drain credits only growth past watermark`() {
        val aggregator = OutboundTrafficAggregator(proxyGraph)
        aggregator.onEvents(newConnection(uplink = 100, downlink = 200))
        assertEquals(TrafficDelta(100, 200), aggregator.drain().byProfile[PROXY_ID])

        // Resubscribe replay: NEW with higher lifetime totals must yield only the delta.
        aggregator.onEvents(newConnection(uplink = 150, downlink = 260, reset = true))

        assertEquals(TrafficDelta(50, 60), aggregator.drain().byProfile[PROXY_ID])
    }

    @Test
    fun `chain credits every hop and the chain itself once`() {
        // Chain entered at "entry", dialing through "exit".
        val aggregator = OutboundTrafficAggregator(
            mapOf(
                "entry" to TrafficNode(
                    profileIDs = setOf(ENTRY_ID, CHAIN_ID),
                    detour = "exit",
                ),
                "exit" to TrafficNode(profileIDs = setOf(EXIT_ID, CHAIN_ID)),
            ),
        )

        aggregator.onEvents(newConnection(uplink = 9, downlink = 3, chain = listOf("entry")))

        val snapshot = aggregator.drain()
        assertEquals(
            mapOf(
                ENTRY_ID to TrafficDelta(9, 3),
                EXIT_ID to TrafficDelta(9, 3),
                CHAIN_ID to TrafficDelta(9, 3),
            ),
            snapshot.byProfile,
            "every hop of the chain carried the same bytes",
        )
        assertEquals(
            TrafficDelta(9, 3),
            snapshot.proxied,
            "the session total counts a chain once, not once per hop",
        )
    }

    @Test
    fun `selector credits only the branch the connection resolved`() {
        val aggregator = OutboundTrafficAggregator(selectorGraph())

        aggregator.onEvents(
            newConnection(
                uplink = 5,
                downlink = 0,
                // sing-box reverses the chain it walks: the matched outbound is last.
                chain = listOf("member-entry", "selector"),
            ),
        )

        assertEquals(
            mapOf(
                SELECTOR_ID to TrafficDelta(5, 0),
                ENTRY_ID to TrafficDelta(5, 0),
                EXIT_ID to TrafficDelta(5, 0),
                CHAIN_ID to TrafficDelta(5, 0),
            ),
            aggregator.drain().byProfile,
            "the unselected member must not be credited",
        )
    }

    @Test
    fun `selector hidden behind a detour uses the reported selection`() {
        // landing -> selector -> member, so no connection can report the selection.
        val graph = selectorGraph() + mapOf(
            "landing" to TrafficNode(profileIDs = setOf(LANDING_ID), detour = "selector"),
        )
        val aggregator = OutboundTrafficAggregator(graph)
        aggregator.updateSelection("selector", "other-member")

        aggregator.onEvents(newConnection(uplink = 4, downlink = 0, chain = listOf("landing")))

        assertEquals(
            mapOf(
                LANDING_ID to TrafficDelta(4, 0),
                SELECTOR_ID to TrafficDelta(4, 0),
                OTHER_MEMBER_ID to TrafficDelta(4, 0),
            ),
            aggregator.drain().byProfile,
        )
    }

    @Test
    fun `bypassed traffic is counted apart from proxied traffic`() {
        val aggregator = OutboundTrafficAggregator(proxyGraph)

        aggregator.onEvents(
            newConnection(uplink = 6, downlink = 1, chain = listOf(TAG_DIRECT), id = "direct"),
        )
        aggregator.onEvents(newConnection(uplink = 2, downlink = 8))

        val snapshot = aggregator.drain()
        assertEquals(TrafficDelta(6, 1), snapshot.bypassed)
        assertEquals(TrafficDelta(2, 8), snapshot.proxied)
        assertEquals(mapOf(PROXY_ID to TrafficDelta(2, 8)), snapshot.byProfile)
    }

    /** selector -> ["member-entry" (a chain of entry -> exit), "other-member"]. */
    private fun selectorGraph() = mapOf(
        "selector" to TrafficNode(
            profileIDs = setOf(SELECTOR_ID),
            memberTags = listOf("member-entry", "other-member"),
        ),
        "member-entry" to TrafficNode(
            profileIDs = setOf(ENTRY_ID, CHAIN_ID),
            detour = "member-exit",
        ),
        "member-exit" to TrafficNode(profileIDs = setOf(EXIT_ID, CHAIN_ID)),
        "other-member" to TrafficNode(profileIDs = setOf(OTHER_MEMBER_ID)),
    )

    private fun newConnection(
        uplink: Long,
        downlink: Long,
        chain: List<String> = listOf("proxy"),
        id: String = "c1",
        reset: Boolean = false,
    ) = connectionEvents {
        this.reset = reset
        events += connectionEvent {
            type = ConnectionEventType.CONNECTION_EVENT_NEW
            this.id = id
            connection = connection {
                outbound = chain.last()
                chainList += chain
                uplinkTotal = uplink
                downlinkTotal = downlink
            }
        }
    }

    private companion object {
        const val PROXY_ID = 1L
        const val ENTRY_ID = 2L
        const val EXIT_ID = 3L
        const val CHAIN_ID = 4L
        const val SELECTOR_ID = 5L
        const val OTHER_MEMBER_ID = 6L
        const val LANDING_ID = 7L
    }
}
