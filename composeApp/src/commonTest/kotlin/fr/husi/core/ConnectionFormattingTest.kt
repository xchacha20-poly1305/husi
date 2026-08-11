package fr.husi.core

import fr.husi.proto.daemon.connection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionFormattingTest {

    @Test
    fun `bound label joins name and type`() {
        assertEquals("mixed-in/mixed", formatBound("mixed-in", "mixed"))
        assertEquals("mixed", formatBound("", "mixed"))
        assertEquals("mixed-in", formatBound("mixed-in", ""))
    }

    @Test
    fun `matched outbound prefers last chain hop`() {
        val connection = connection {
            outbound = "selector"
            chainList += "selector"
            chainList += "node"
        }
        assertEquals("node", connection.matchedOutbound())
    }

    @Test
    fun `matched rule falls back to final`() {
        val unmatched = connection { }
        assertEquals("final", unmatched.matchedRuleOrFinal())
        val matched = connection { rule = "geosite:cn" }
        assertEquals("geosite:cn", matched.matchedRuleOrFinal())
    }

    @Test
    fun `formatConnectionTime zero is empty`() {
        assertEquals("", formatConnectionTime(0L))
        assertEquals("", formatConnectionTime(-1L))
    }

    @Test
    fun `formatConnectionTime renders known millis in local zone`() {
        val millis = 1_700_000_000_000L // 2023-11-14T22:13:20Z
        val expected = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
        assertEquals(expected, formatConnectionTime(millis))
    }
}
