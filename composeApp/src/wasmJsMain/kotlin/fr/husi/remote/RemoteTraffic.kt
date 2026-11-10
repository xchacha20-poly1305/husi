package fr.husi.remote

import fr.husi.proto.daemon.Status

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
        fun fromStatus(value: Status) =
            RemoteTraffic(
                upload = value.uplink,
                download = value.downlink,
                uploadTotal = value.uplinkTotal,
                downloadTotal = value.downlinkTotal,
                memory = value.memory,
                goroutines = value.goroutines,
                connections = value.connectionsIn.toLong() + value.connectionsOut,
            )
    }
}
