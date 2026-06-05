package fr.husi.ktx

import fr.husi.fmt.shadowquic.ShadowQUICBean
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class FormatsKtTest {

    @Test
    fun `b64Decode should decode regular base64 content`() {
        val result = "SGVsbG8=".b64Decode().decodeToString()

        assertEquals("Hello", result)
    }

    @Test
    fun `b64Decode should decode url safe base64 without padding`() {
        val expected = byteArrayOf(251.toByte(), 239.toByte(), 255.toByte(), 250.toByte())
        val result = "--__-g".b64Decode()

        assertContentEquals(expected, result)
    }

    @Test
    fun `b64Decode should fallback to mime decoder for wrapped content`() {
        val result = "SGVs\nbG8=".b64Decode().decodeToString()

        assertEquals("Hello", result)
    }

    @Test
    fun `b64Decode should correctly decode url safe base64 containing dash`() {
        // 0x03E0 encodes to "A-A" in URL-safe; MIME would silently strip the dash
        val data = byteArrayOf(0x03, 0xE0.toByte())
        val encoded = data.b64EncodeUrlSafe()

        assertContentEquals(data, encoded.b64Decode())
    }

    @Test
    fun `b64Decode should correctly decode url safe base64 containing underscore`() {
        // 0x03FC encodes to "A_w" in URL-safe; MIME would silently strip the underscore
        val data = byteArrayOf(0x03, 0xFC.toByte())
        val encoded = data.b64EncodeUrlSafe()

        assertContentEquals(data, encoded.b64Decode())
    }

    @Test
    fun `b64Decode should correctly decode standard base64 containing plus`() {
        // "A+A=" is standard base64 for 0x03E0; URLSafe decoder must reject it, not corrupt
        val result = "A+A=".b64Decode()

        assertContentEquals(byteArrayOf(0x03, 0xE0.toByte()), result)
    }

    @Test
    fun `b64Decode should correctly decode standard base64 containing slash`() {
        // "A/w=" is standard base64 for 0x03FC; URLSafe decoder must reject it, not corrupt
        val result = "A/w=".b64Decode()

        assertContentEquals(byteArrayOf(0x03, 0xFC.toByte()), result)
    }

    @Test
    fun `b64Decode url safe round trip with mixed dash and underscore`() {
        // Longer data that produces both '-' and '_' in URL-safe encoding
        val data = byteArrayOf(
            0x03, 0xE0.toByte(), 0xFF.toByte(), 0x03, 0xFC.toByte(), 0xAB.toByte(),
        )
        val encoded = data.b64EncodeUrlSafe()

        assertContentEquals(data, encoded.b64Decode())
    }

    @Test
    fun `b64Decode should throw IllegalStateException for invalid base64`() {
        val error = assertFailsWith<IllegalStateException> {
            "世".b64Decode()
        }

        assertEquals(error.message?.startsWith("decode base64: "), true)
    }

    @Test
    fun `parseProxies should parse shadowquic share link`() = runTest {
        val proxies = parseProxies("sq://user:pass@example.com?sni=cdn.com#node-01")
        val bean = assertIs<ShadowQUICBean>(proxies.single())

        assertEquals("example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("user", bean.username)
        assertEquals("pass", bean.password)
        assertEquals("cdn.com", bean.sni)
        assertEquals("node-01", bean.name)
    }
}
