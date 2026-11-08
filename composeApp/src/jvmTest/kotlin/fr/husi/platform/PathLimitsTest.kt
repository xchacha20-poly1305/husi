package fr.husi.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathLimitsTest {

    private val chineseName = "中".repeat(100) + ".srs"

    @Test
    fun `byte counting platforms measure UTF-8 length`() {
        assertEquals(304, PathLimits.Linux.lengthOf(chineseName))
        assertEquals(304, PathLimits.MacOs.lengthOf(chineseName))
    }

    @Test
    fun `Windows counts UTF-16 code units`() {
        assertEquals(104, PathLimits.Windows.lengthOf(chineseName))
    }

    @Test
    fun `a name fitting on Windows may not fit on Linux`() {
        assertTrue(PathLimits.Windows.acceptsName(chineseName))
        assertFalse(PathLimits.Linux.acceptsName(chineseName))
    }

    @Test
    fun `name limit is the component limit, not the path limit`() {
        val longestName = "a".repeat(255)

        assertTrue(PathLimits.Linux.acceptsName(longestName))
        assertFalse(PathLimits.Linux.acceptsName(longestName + "a"))
    }

    @Test
    fun `Windows rejects a path no longer than MAX_PATH allows`() {
        val path = "C:/Users/husi/assets/geo/" + "a".repeat(234)

        assertEquals(259, path.length)
        assertTrue(PathLimits.Windows.acceptsPath(path))
        assertFalse(PathLimits.Windows.acceptsPath(path + "a"))
        assertTrue(PathLimits.Linux.acceptsPath(path + "a"))
    }
}
