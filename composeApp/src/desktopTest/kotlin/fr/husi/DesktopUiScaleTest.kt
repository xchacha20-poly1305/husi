package fr.husi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopUiScaleTest {

    private val gnomeResourceDatabase = """
        Xft.dpi:	192
        Xft.antialias:	1
        Xft.hintstyle:	hintslight
        Xcursor.size:	48
    """.trimIndent()

    @Test
    fun `parses the scale published by GNOME`() {
        assertEquals(2f, parseXftDpiScale(gnomeResourceDatabase))
    }

    @Test
    fun `parses a fractional scale`() {
        assertEquals(1.5f, parseXftDpiScale("Xft.dpi:\t144"))
    }

    @Test
    fun `ignores resources whose name merely ends with the dpi key`() {
        assertNull(parseXftDpiScale("Xft.dpi.override:\t192"))
    }

    @Test
    fun `reports no scale when the dpi resource is absent`() {
        assertNull(parseXftDpiScale("Xft.antialias:\t1"))
    }

    @Test
    fun `reports no scale for a malformed or meaningless dpi`() {
        assertNull(parseXftDpiScale("Xft.dpi:\tinvalid"))
        assertNull(parseXftDpiScale("Xft.dpi:\t0"))
        assertNull(parseXftDpiScale("Xft.dpi:\t-192"))
    }

    @Test
    fun `an empty database means the settings daemon has not published yet`() {
        assertFalse(isXResourceDatabasePublished(""))
        assertFalse(isXResourceDatabasePublished("\n \n"))
    }

    @Test
    fun `a database without the dpi resource still counts as published`() {
        assertTrue(isXResourceDatabasePublished("Xft.antialias:\t1"))
    }
}
