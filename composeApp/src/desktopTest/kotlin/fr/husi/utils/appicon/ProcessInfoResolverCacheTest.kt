package fr.husi.utils.appicon

import fr.husi.ui.dashboard.ProcessInfo
import fr.husi.ui.dashboard.ProcessInfoResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ProcessInfoResolverCacheTest {
    @Test
    fun `resolve looks up a path only once including negative results`() = runTest {
        var lookups = 0
        val missing = "/opt/missing/binary"
        val present = "/usr/bin/firefox"
        val info = ProcessInfo(packageName = present, label = "Firefox")
        val resolver = ProcessInfoResolver { path ->
            lookups += 1
            if (path == present) info else null
        }

        assertNull(resolver.resolve(missing, 0))
        assertNull(resolver.resolve(missing, 0))
        assertSame(info, resolver.resolve(present, 1))
        assertSame(info, resolver.resolve(present, 99))
        assertEquals(2, lookups)
    }

    @Test
    fun `clear drops cached hits and misses`() = runTest {
        var lookups = 0
        val path = "/usr/bin/firefox"
        val resolver = ProcessInfoResolver {
            lookups += 1
            null
        }

        assertNull(resolver.resolve(path, 0))
        resolver.clear()
        assertNull(resolver.resolve(path, 0))
        assertEquals(2, lookups)
    }

    @Test
    fun `blank process is not looked up`() = runTest {
        var lookups = 0
        val resolver = ProcessInfoResolver {
            lookups += 1
            null
        }

        assertNull(resolver.resolve(null, 12))
        assertNull(resolver.resolve("   ", 12))
        assertEquals(0, lookups)
    }
}
