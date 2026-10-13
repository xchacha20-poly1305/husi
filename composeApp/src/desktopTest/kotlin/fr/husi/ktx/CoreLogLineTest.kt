package fr.husi.ktx

import fr.husi.proto.daemon.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoreLogLineTest {
    @Test
    fun `reads level from relative timestamp prefix`() {
        assertEquals(LogLevel.TRACE, coreLogLineLevel("TRACE[0012] router: match"))
    }

    @Test
    fun `reads level without timestamp`() {
        assertEquals(LogLevel.INFO, coreLogLineLevel("INFO session host listening on /tmp/api.sock"))
    }

    @Test
    fun `reads level after full timestamp`() {
        assertEquals(LogLevel.ERROR, coreLogLineLevel("+0800 2026-10-13 12:00:00 ERROR dns: timeout"))
    }

    @Test
    fun `reads level from colored prefix`() {
        assertEquals(LogLevel.WARN, coreLogLineLevel("\u001B[33mWARN\u001B[0m[0003] outbound: retry"))
    }

    @Test
    fun `returns null for line without level`() {
        assertNull(coreLogLineLevel("panic: runtime error: index out of range"))
        assertNull(coreLogLineLevel("goroutine 1 [running]:"))
    }

    @Test
    fun `ignores dash line that is not a timestamp`() {
        assertNull(coreLogLineLevel("--- a b TRACE message"))
    }

    @Test
    fun `ignores level word later in message`() {
        assertNull(coreLogLineLevel("the TRACE level is noisy"))
    }
}
