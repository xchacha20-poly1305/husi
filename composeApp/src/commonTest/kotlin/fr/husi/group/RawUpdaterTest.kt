package fr.husi.group

import fr.husi.fmt.FmtTestConstant
import fr.husi.fmt.v2ray.VMessBean
import fr.husi.fmt.v2ray.VLESSBean
import fr.husi.ktx.b64EncodeOneLine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RawUpdaterTest {

    @Test
    fun `parseRaw should parse base64 encoded vless links`() = runBlocking {
        val proxies = assertNotNull(RawUpdater.parseRaw(FmtTestConstant.VLESS_GRPC_URL.b64EncodeOneLine()))
        val bean = assertIs<VLESSBean>(proxies.single())

        assertEquals("uuid", bean.uuid)
        assertEquals("test-vless", bean.name)
    }

    @Test
    fun `parseRaw should parse base64 encoded multi line links`() = runBlocking {
        val rawText = listOf(
            FmtTestConstant.VLESS_GRPC_URL,
            FmtTestConstant.VMESS_DUCKSOFT_URL,
        ).joinToString("\n")

        val proxies = assertNotNull(RawUpdater.parseRaw(rawText.b64EncodeOneLine()))

        assertEquals(2, proxies.size)
        assertTrue(proxies.any { it is VLESSBean })
        assertTrue(proxies.any { it is VMessBean })
    }

    @Test
    fun `parseRaw should parse plain multi line links`() = runBlocking {
        val rawText = listOf(
            FmtTestConstant.VLESS_GRPC_URL,
            FmtTestConstant.VMESS_DUCKSOFT_URL,
        ).joinToString("\n")

        val proxies = assertNotNull(RawUpdater.parseRaw(rawText))

        assertEquals(2, proxies.size)
        assertTrue(proxies.any { it is VLESSBean })
        assertTrue(proxies.any { it is VMessBean })
    }
}
