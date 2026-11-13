package fr.husi.core

import fr.husi.proto.v1.GetVersionResponse
import fr.husi.test.FakeCoreClient
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoreVersionCacheTest {

    private val coreClient = FakeCoreClient().also {
        it.nextVersion = GetVersionResponse(sing_box_version = "1.13.0")
    }
    private val cache = CoreVersionCache(coreClient)

    @Test
    fun `get keeps the first successful version`() = runTest {
        assertEquals("1.13.0", cache.get()?.sing_box_version)

        coreClient.nextVersion = GetVersionResponse(sing_box_version = "1.14.0")

        assertEquals("1.13.0", cache.get()?.sing_box_version)
        assertEquals(1, coreClient.getVersionCalls)
    }

    @Test
    fun `get retries after the core host was unreachable`() = runTest {
        coreClient.getVersionThrowable = IOException("connection refused")

        assertNull(cache.get())

        coreClient.getVersionThrowable = null

        assertEquals("1.13.0", cache.get()?.sing_box_version)
        assertEquals(2, coreClient.getVersionCalls)
    }
}
