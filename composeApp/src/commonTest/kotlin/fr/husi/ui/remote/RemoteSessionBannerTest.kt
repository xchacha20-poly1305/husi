package fr.husi.ui.remote

import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteSessionBannerTest {

    @Test
    fun `formatUptime uses mm ss under an hour`() {
        assertEquals("1:05", formatUptime(0L, 65_000L))
    }

    @Test
    fun `formatUptime includes hours when needed`() {
        assertEquals("1:02:03", formatUptime(0L, (3600 + 120 + 3) * 1000L))
    }
}
