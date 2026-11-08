package fr.husi.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteTrafficTest {
    @Test
    fun `protobuf JSON counters keep full integer precision`() {
        val traffic = RemoteTraffic.fromJson(Json.parseToJsonElement("""{"uplinkTotal":"9007199254740993","downlink":42,"connectionsIn":2,"connectionsOut":3}""").jsonObject)
        assertEquals(9007199254740993L, traffic.uploadTotal)
        assertEquals(42L, traffic.download)
        assertEquals(5L, traffic.connections)
        assertEquals(0L, traffic.memory)
    }
}
