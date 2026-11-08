package fr.husi.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Applies the daemon's initial snapshots and subsequent traffic deltas. */
internal fun applyConnectionEvents(
    connections: MutableMap<String, JsonObject>,
    update: JsonObject,
    keepClosed: Boolean = false,
) {
    if (update["reset"] == JsonPrimitive(true)) connections.clear()
    val events = update["events"] as? JsonArray ?: return
    for (element in events) {
        val event = element as? JsonObject ?: continue
        val id = (event["id"] as? JsonPrimitive)?.contentOrNull ?: continue
        val kind = (event["type"] as? JsonPrimitive)?.contentOrNull
        if (kind == "CONNECTION_EVENT_CLOSED" || kind == "2") {
            if (keepClosed) {
                val previous = event["connection"] as? JsonObject ?: connections[id]
                if (previous != null)
                    connections[id] =
                        JsonObject(
                            previous +
                                mapOf(
                                    "closedAt" to (event["closedAt"] ?: JsonPrimitive("0")),
                                    "uplink" to JsonPrimitive("0"),
                                    "downlink" to JsonPrimitive("0"),
                                ),
                        )
            } else connections.remove(id)
            continue
        }
        val snapshot = event["connection"] as? JsonObject
        if (snapshot != null) {
            connections[id] = snapshot
            continue
        }
        val previous = connections[id] ?: continue
        val upload = (event["uplinkDelta"] as? JsonPrimitive)?.longOrNull ?: 0
        val download = (event["downlinkDelta"] as? JsonPrimitive)?.longOrNull ?: 0
        val uploadTotal = (previous["uplinkTotal"] as? JsonPrimitive)?.longOrNull ?: 0
        val downloadTotal = (previous["downlinkTotal"] as? JsonPrimitive)?.longOrNull ?: 0
        connections[id] =
            JsonObject(
                previous +
                    mapOf(
                        "uplinkTotal" to JsonPrimitive((uploadTotal + upload).toString()),
                        "downlinkTotal" to JsonPrimitive((downloadTotal + download).toString()),
                        "uplink" to JsonPrimitive(upload.toString()),
                        "downlink" to JsonPrimitive(download.toString()),
                    ),
            )
    }
}
