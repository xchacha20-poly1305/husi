package fr.husi.remote

import fr.husi.proto.daemon.Status
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteTrafficTest {
    @Test
    fun `Wire counters keep full integer precision in the browser`() {
        val encoded = Status(uplinkTotal = 9007199254740993L, downlink = 42, connectionsIn = 2, connectionsOut = 3).encode()
        val traffic = RemoteTraffic.fromStatus(Status.ADAPTER.decode(encoded))
        assertEquals(9007199254740993L, traffic.uploadTotal)
        assertEquals(42L, traffic.download)
        assertEquals(5L, traffic.connections)
        assertEquals(0L, traffic.memory)
    }
}
