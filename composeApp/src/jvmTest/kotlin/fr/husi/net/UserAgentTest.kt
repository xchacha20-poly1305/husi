package fr.husi.net

import fr.husi.BuildConfig
import fr.husi.core.CoreVersionCache
import fr.husi.proto.v1.getVersionResponse
import fr.husi.test.FakeCoreClient
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class UserAgentTest {

    private val coreClient = FakeCoreClient().also {
        it.nextVersion = getVersionResponse { singBoxVersion = "1.13.0" }
    }
    private val coreVersionCache = CoreVersionCache(coreClient)

    @Test
    fun `blank custom User-Agent selects the default one`() = runTest {
        assertEquals(
            "husi/${BuildConfig.VERSION_NAME} (sing-box 1.13.0)",
            buildUserAgent("", coreVersionCache),
        )
    }

    @Test
    fun `custom User-Agent expands version escapes`() = runTest {
        assertEquals(
            "clash/${BuildConfig.VERSION_NAME}+1.13.0",
            buildUserAgent($$"clash/$version+$box_version", coreVersionCache),
        )
    }

    @Test
    fun `custom User-Agent without box version escape skips the core`() = runTest {
        assertEquals("clash.meta", buildUserAgent("clash.meta", coreVersionCache))
        assertEquals(0, coreClient.getVersionCalls)
    }

    @Test
    fun `unreachable core leaves box version unknown`() = runTest {
        coreClient.getVersionThrowable = IOException("connection refused")

        assertEquals(
            "husi/${BuildConfig.VERSION_NAME} (sing-box unknown)",
            buildUserAgent("", coreVersionCache),
        )
    }
}
