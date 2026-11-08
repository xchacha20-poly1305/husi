package fr.husi.remote

import kotlinx.serialization.json.JsonObject

internal data class RemoteTraffic(
    val upload: Long = 0,
    val download: Long = 0,
    val uploadTotal: Long = 0,
    val downloadTotal: Long = 0,
    val memory: Long = 0,
    val connections: Long = 0,
    val goroutines: Int = 0,
) {
    companion object {
        fun fromJson(value: JsonObject) =
            RemoteTraffic(
                upload = value.number("uplink"),
                download = value.number("downlink"),
                uploadTotal = value.number("uplinkTotal"),
                downloadTotal = value.number("downlinkTotal"),
                memory = value.number("memory"),
                goroutines = value.number("goroutines").toInt(),
                connections = value.number("connectionsIn") + value.number("connectionsOut"),
            )
    }
}
