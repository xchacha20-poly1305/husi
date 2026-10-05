package fr.husi.ktx

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteFormatsKtTest {

    @Test
    fun `formatBytes should show zero in kB`() {
        assertEquals("0 kB", 0L.formatBytes())
    }

    @Test
    fun `formatBytes should show values below one kilobyte as fractions of kB`() {
        assertEquals("0.0 kB", 1L.formatBytes())
        assertEquals("0.5 kB", 500L.formatBytes())
    }

    @Test
    fun `formatBytes should use one decimal below ten`() {
        assertEquals("1.0 kB", 1_000L.formatBytes())
        assertEquals("1.5 MB", 1_500_000L.formatBytes())
        assertEquals("10 kB", 9_999L.formatBytes())
    }

    @Test
    fun `formatBytes should drop decimals from ten`() {
        assertEquals("123 MB", 123_400_000L.formatBytes())
        assertEquals("9.2 EB", Long.MAX_VALUE.formatBytes())
    }

    @Test
    fun `formatMemoryBytes should use 1024 based units`() {
        assertEquals("0 kB", 0L.formatMemoryBytes())
        assertEquals("1.0 kB", 1_024L.formatMemoryBytes())
        assertEquals("1.0 kB", 1_000L.formatMemoryBytes())
        assertEquals("1.5 MB", (1_536L * 1_024).formatMemoryBytes())
        assertEquals("512 MB", (512L * 1_024 * 1_024).formatMemoryBytes())
    }
}
