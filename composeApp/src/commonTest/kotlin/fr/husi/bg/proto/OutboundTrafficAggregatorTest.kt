package fr.husi.bg.proto

import fr.husi.proto.daemon.ConnectionEventType
import fr.husi.proto.daemon.connection
import fr.husi.proto.daemon.connectionEvent
import fr.husi.proto.daemon.connectionEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class OutboundTrafficAggregatorTest {

    @Test
    fun `new seeds totals and update adds deltas`() {
        val aggregator = OutboundTrafficAggregator()
        aggregator.onEvents(
            connectionEvents {
                events += connectionEvent {
                    type = ConnectionEventType.CONNECTION_EVENT_NEW
                    id = "c1"
                    connection = connection {
                        outbound = "proxy"
                        chainList += "proxy"
                        uplinkTotal = 100
                        downlinkTotal = 200
                    }
                }
            },
        )
        assertEquals(100, aggregator.drain("proxy", isUpload = true))
        assertEquals(200, aggregator.drain("proxy", isUpload = false))

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
        assertEquals(10, aggregator.drain("proxy", isUpload = true))
        assertEquals(20, aggregator.drain("proxy", isUpload = false))
    }

    @Test
    fun `reset without drain does not double-count same totals`() {
        val aggregator = OutboundTrafficAggregator()
        aggregator.onEvents(
            connectionEvents {
                events += connectionEvent {
                    type = ConnectionEventType.CONNECTION_EVENT_NEW
                    id = "c1"
                    connection = connection {
                        outbound = "proxy"
                        chainList += "proxy"
                        uplinkTotal = 50
                        downlinkTotal = 50
                    }
                }
            },
        )
        // Reconnect replay with reset must not double-count undrained totals.
        aggregator.onEvents(
            connectionEvents {
                reset = true
                events += connectionEvent {
                    type = ConnectionEventType.CONNECTION_EVENT_NEW
                    id = "c1"
                    connection = connection {
                        outbound = "proxy"
                        chainList += "proxy"
                        uplinkTotal = 50
                        downlinkTotal = 50
                    }
                }
            },
        )
        assertEquals(50, aggregator.drain("proxy", isUpload = true))
        assertEquals(50, aggregator.drain("proxy", isUpload = false))
    }

    @Test
    fun `reset after drain credits only growth past watermark`() {
        val aggregator = OutboundTrafficAggregator()
        aggregator.onEvents(
            connectionEvents {
                events += connectionEvent {
                    type = ConnectionEventType.CONNECTION_EVENT_NEW
                    id = "c1"
                    connection = connection {
                        outbound = "proxy"
                        chainList += "proxy"
                        uplinkTotal = 100
                        downlinkTotal = 200
                    }
                }
            },
        )
        assertEquals(100, aggregator.drain("proxy", isUpload = true))
        assertEquals(200, aggregator.drain("proxy", isUpload = false))

        // Resubscribe replay: NEW with higher lifetime totals must yield only the delta.
        aggregator.onEvents(
            connectionEvents {
                reset = true
                events += connectionEvent {
                    type = ConnectionEventType.CONNECTION_EVENT_NEW
                    id = "c1"
                    connection = connection {
                        outbound = "proxy"
                        chainList += "proxy"
                        uplinkTotal = 150
                        downlinkTotal = 260
                    }
                }
            },
        )
        assertEquals(50, aggregator.drain("proxy", isUpload = true))
        assertEquals(60, aggregator.drain("proxy", isUpload = false))
    }

    @Test
    fun `matched outbound uses last chain hop`() {
        val aggregator = OutboundTrafficAggregator()
        aggregator.onEvents(
            connectionEvents {
                events += connectionEvent {
                    type = ConnectionEventType.CONNECTION_EVENT_NEW
                    id = "c1"
                    connection = connection {
                        outbound = "selector"
                        chainList += "selector"
                        chainList += "node-a"
                        uplinkTotal = 7
                        downlinkTotal = 0
                    }
                }
            },
        )
        assertEquals(7, aggregator.drain("node-a", isUpload = true))
        assertEquals(0, aggregator.drain("selector", isUpload = true))
    }
}
