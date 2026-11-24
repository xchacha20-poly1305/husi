package fr.husi.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartFormatTest {
    @Test
    fun `formatSmartWeight formats finite values cleanly and rejects non-finite values`() {
        assertEquals("0", formatSmartWeight(0.0))
        assertEquals("0", formatSmartWeight(-0.0))
        assertEquals("0", formatSmartWeight(-0.001))
        assertEquals("92", formatSmartWeight(92.0))
        assertEquals("92.5", formatSmartWeight(92.5))
        assertEquals("92.56", formatSmartWeight(92.56))
        assertEquals("92.57", formatSmartWeight(92.567))
        assertEquals("-12.35", formatSmartWeight(-12.345))
        assertEquals("0.5", formatSmartWeight(0.5))
        assertEquals("", formatSmartWeight(Double.NaN))
        assertEquals("", formatSmartWeight(Double.POSITIVE_INFINITY))
        assertEquals("", formatSmartWeight(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `SmartConnectionInfo canBlock only when state is normal`() {
        assertTrue(SmartConnectionInfo(blockState = SmartConnectionInfo.SMART_BLOCK_NORMAL).canBlock)
        assertFalse(SmartConnectionInfo(blockState = SmartConnectionInfo.SMART_BLOCK_DEGRADED).canBlock)
        assertFalse(SmartConnectionInfo(blockState = SmartConnectionInfo.SMART_BLOCK_BLOCKED).canBlock)
        assertFalse(SmartConnectionInfo(blockState = "").canBlock)
    }
}
