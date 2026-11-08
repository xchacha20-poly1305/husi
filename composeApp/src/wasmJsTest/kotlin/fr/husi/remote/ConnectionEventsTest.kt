package fr.husi.remote

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionEventsTest {
    @Test
    fun `reset snapshot delta and close preserve connection state`() {
        val connections = mutableMapOf<String, JsonObject>()
        fun apply(value: String) = applyConnectionEvents(connections, Json.parseToJsonElement(value).jsonObject)
        apply("""{"reset":true,"events":[{"id":"a","connection":{"id":"a","uplinkTotal":"9007199254740993","downlinkTotal":"2"}}]}""")
        apply("""{"events":[{"id":"a","type":"CONNECTION_EVENT_UPDATE","uplinkDelta":"7","downlinkDelta":"3"}]}""")
        assertEquals("\"9007199254741000\"", connections.getValue("a")["uplinkTotal"].toString())
        assertEquals("\"5\"", connections.getValue("a")["downlinkTotal"].toString())
        apply("""{"events":[{"id":"a","type":"CONNECTION_EVENT_CLOSED"}]}""")
        assertEquals(0, connections.size)
        apply("""{"events":[{"id":"missing","type":"CONNECTION_EVENT_UPDATE"}]}""")
        assertEquals(0, connections.size)
    }
}

class ClosedConnectionEventsTest {
    @Test
    fun `browser can retain the final closed snapshot`() {
        val connections = mutableMapOf<String, JsonObject>()
        applyConnectionEvents(connections, Json.parseToJsonElement("""{"events":[{"id":"a","connection":{"id":"a","uplinkTotal":"12"}}]}""").jsonObject)
        applyConnectionEvents(connections, Json.parseToJsonElement("""{"events":[{"id":"a","type":"CONNECTION_EVENT_CLOSED","closedAt":"1234"}]}""").jsonObject, keepClosed = true)
        assertEquals("\"1234\"", connections.getValue("a")["closedAt"].toString())
        assertEquals("\"12\"", connections.getValue("a")["uplinkTotal"].toString())
    }
}
