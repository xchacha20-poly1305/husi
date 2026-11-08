package fr.husi.fmt.trusttunnel

import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Ported from TrustTunnel's `deeplink/src/cert.rs` tests. Each certificate is a bare ASN.1 SEQUENCE. */
class TrustTunnelCertificateTest {

    private val first = bytes(0x30, 0x03, 0x01, 0x02, 0x03)
    private val second = bytes(0x30, 0x04, 0x04, 0x05, 0x06, 0x07)

    @Test
    fun `pem to chain decodes a PEM block`() {
        val pem = """
            -----BEGIN CERTIFICATE-----
            MIIBkTCB+wIJAKHHCgVZU7PWMA0GCSqGSIb3DQEBCwUAMBExDzANBgNVBAMMBnRl
            c3RjYTAeFw0yMzAxMDEwMDAwMDBaFw0yNDAxMDEwMDAwMDBaMBExDzANBgNVBAMM
            BnRlc3RjYTCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAw9nQx8KLBs9LKVqK
            6WZ7aYvMQXAA1tP9VbFqFBDzDYJoFZxKZPbZKGOZOmKMJMxLCqN6qLlPWnZrYWXL
            +3A8PqYqLqvMVxQ8QZQZQZQZQZQZQZQZQZQZQZQZQZQZQZQCAQIDAQABMA0GCSqG
            SIb3DQEBCwUAA4GBAJKCfpqLG3PkKE4L7VVzLqH4E7FkLqZxMQZQZQZQZQZQZQZQ
            -----END CERTIFICATE-----
        """.trimIndent()

        assertTrue(pemToCertificateChain(pem).size > 0)
    }

    @Test
    fun `pem to chain rejects text without a PEM block`() {
        assertFailsWith<IllegalArgumentException> { pemToCertificateChain("") }
        assertFailsWith<IllegalArgumentException> { pemToCertificateChain("not a certificate") }
    }

    @Test
    fun `chain to pem writes one block per certificate`() {
        val single = certificateChainToPem(first)
        assertTrue(single.startsWith("-----BEGIN CERTIFICATE-----"))
        assertTrue(single.endsWith("-----END CERTIFICATE-----\n"))
        assertEquals(1, single.occurrences("-----BEGIN CERTIFICATE-----"))

        val chain = certificateChainToPem(first + second)
        assertEquals(2, chain.occurrences("-----BEGIN CERTIFICATE-----"))
        assertEquals(2, chain.occurrences("-----END CERTIFICATE-----"))
    }

    @Test
    fun `split reads long form lengths`() {
        // 0x81 0x05: the length, 5, is in the one byte that follows.
        val longForm = bytes(0x30, 0x81, 0x05, 0x01, 0x02, 0x03, 0x04, 0x05)
        assertEquals(listOf(longForm), splitDerCertificates(longForm))

        // 0x82 0x01 0x00: the length, 256, is in the two bytes that follow.
        val twoByteLength = bytes(0x30, 0x82, 0x01, 0x00) + ByteArray(256).toByteString()
        assertEquals(listOf(twoByteLength), splitDerCertificates(twoByteLength))
    }

    @Test
    fun `split rejects malformed DER`() {
        // Not an ASN.1 SEQUENCE.
        assertFailsWith<IllegalArgumentException> {
            splitDerCertificates(bytes(0x31, 0x05, 0x01, 0x02, 0x03, 0x04, 0x05))
        }
        // Claims 10 bytes but has 5.
        assertFailsWith<IllegalArgumentException> {
            splitDerCertificates(bytes(0x30, 0x0A, 0x01, 0x02, 0x03, 0x04, 0x05))
        }
        // Claims 0x0123 bytes but has none.
        assertFailsWith<IllegalArgumentException> { splitDerCertificates(bytes(0x30, 0x82, 0x01, 0x23)) }
    }

    @Test
    fun `split of nothing is no certificate`() {
        assertEquals(emptyList(), splitDerCertificates(ByteString.EMPTY))
    }

    @Test
    fun `chain round trips through pem`() {
        assertEquals(first, pemToCertificateChain(certificateChainToPem(first)))
        assertEquals(first + second, pemToCertificateChain(certificateChainToPem(first + second)))
    }

    private fun bytes(vararg values: Int): ByteString = ByteArray(values.size) { values[it].toByte() }.toByteString()

    private operator fun ByteString.plus(other: ByteString): ByteString = (toByteArray() + other.toByteArray()).toByteString()

    private fun String.occurrences(text: String): Int = windowed(text.length).count { it == text }
}
