package fr.husi.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsSystemProxyBackendTest {

    /** The sizes `sizeof` reports for these WinINet structures on 64-bit Windows. */
    @Test
    fun `WinINet structures match the 64-bit native layout`() {
        assertEquals(16, InternetPerConnOption().size())
        assertEquals(32, InternetPerConnOptionList().size())
    }
}
