package fr.husi.remote

import fr.husi.proto.daemon.Connection
import fr.husi.proto.daemon.ConnectionEventType
import fr.husi.proto.daemon.ConnectionEvents

/** Applies the daemon's initial snapshots and subsequent traffic deltas. */
internal fun applyConnectionEvents(
    connections: MutableMap<String, Connection>,
    update: ConnectionEvents,
    keepClosed: Boolean = false,
) {
    if (update.reset) connections.clear()
    for (event in update.events) {
        if (event.type is ConnectionEventType.Unrecognized) continue
        val id = event.id
        if (event.type == ConnectionEventType.CONNECTION_EVENT_CLOSED) {
            if (keepClosed) {
                val previous = event.connection ?: connections[id]
                if (previous != null) {
                    connections[id] = previous.copy(closedAt = event.closedAt, uplink = 0, downlink = 0)
                }
            } else {
                connections.remove(id)
            }
            continue
        }
        val snapshot = event.connection
        if (snapshot != null) {
            connections[id] = snapshot
            continue
        }
        val previous = connections[id] ?: continue
        connections[id] = previous.copy(
            uplinkTotal = previous.uplinkTotal + event.uplinkDelta,
            downlinkTotal = previous.downlinkTotal + event.downlinkDelta,
            uplink = event.uplinkDelta,
            downlink = event.downlinkDelta,
        )
    }
}
