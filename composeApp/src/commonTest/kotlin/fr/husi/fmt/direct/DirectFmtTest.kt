package fr.husi.fmt.direct

import kotlin.test.Test
import kotlin.test.assertEquals

class DirectFmtTest {

    @Test
    fun `buildSingBoxOutboundDirectBean should set direct type and reuse_addr`() {
        val outbound = buildSingBoxOutboundDirectBean(DirectBean())

        assertEquals("direct", outbound.type)
        assertEquals(outbound.reuse_addr, true)
    }
}
