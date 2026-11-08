package fr.husi.ktx

import kotlin.test.Test
import kotlin.test.assertEquals

class CertificateHashesKtTest {

    private val firstCertificate = """
        -----BEGIN CERTIFICATE-----
        MIIBfzCCASWgAwIBAgIUAxrt8fLY4tBdw2ds/X2wFs+umsswCgYIKoZIzj0EAwIw
        FDESMBAGA1UEAwwJYS5leGFtcGxlMCAXDTI2MTAwNTEyMzY1MloYDzIxMjYwOTEx
        MTIzNjUyWjAUMRIwEAYDVQQDDAlhLmV4YW1wbGUwWTATBgcqhkjOPQIBBggqhkjO
        PQMBBwNCAAQY8vygcemkqTw1xvfRTRJhNxZ87NTWazhcnFjyzO6JuxAIK6G7+j9j
        Fd6Z16Qg0d5yFPkUxdaLUhwwfRNuKbhIo1MwUTAdBgNVHQ4EFgQUNytkRoIORj2D
        TwEqf2jd4pkQbR4wHwYDVR0jBBgwFoAUNytkRoIORj2DTwEqf2jd4pkQbR4wDwYD
        VR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiBJIRtoysLbp8Xa2cJgYOP7
        X42p92cbUiiHMKh7Zne/EAIhANjZW2iDuyjnq0l93vz9tL7Sr237VYAYnhAbjEDH
        qNEG
        -----END CERTIFICATE-----
    """.trimIndent()

    private val secondCertificate = """
        -----BEGIN CERTIFICATE-----
        MIIBfzCCASWgAwIBAgIUfhMPga/V9Z5WRNJr/kRfCUyrDm8wCgYIKoZIzj0EAwIw
        FDESMBAGA1UEAwwJYi5leGFtcGxlMCAXDTI2MTAwNTEyMzY1MloYDzIxMjYwOTEx
        MTIzNjUyWjAUMRIwEAYDVQQDDAliLmV4YW1wbGUwWTATBgcqhkjOPQIBBggqhkjO
        PQMBBwNCAAQD3ceUnj7+6ntpNqMsWTH4yG835mCy5GsJQ/tWphM4gkrCjxKe2lVo
        YAZ2ANChPwcd5RND8JKTeDdKQcv2LqDHo1MwUTAdBgNVHQ4EFgQUB2JlUdCOZqwG
        lwa/LynBe2xU9IwwHwYDVR0jBBgwFoAUB2JlUdCOZqwGlwa/LynBe2xU9IwwDwYD
        VR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiEA9qwiOlCMDWICOdKMnnku
        0mKVloZnYH7JwW7OWVXNjgUCIF8eA9HInCY8/NCT0YWN+jhgn3n/39RcFG7I1w1D
        OOPB
        -----END CERTIFICATE-----
    """.trimIndent()

    private val chain = firstCertificate + "\n" + secondCertificate

    @Test
    fun `pemToV2RayCertChainHash should hash a single certificate`() {
        assertEquals(
            "WzXSNZ+gZHXuvS730E/wy3GUaZ5Z4dwFcCxfggVwqH8=",
            pemToV2RayCertChainHash(firstCertificate),
        )
    }

    @Test
    fun `pemToV2RayCertChainHash should chain every certificate`() {
        assertEquals(
            "RAbC7zjtbgpq/PGMp/0X+OKCnqO79in71IcN98iZ4vA=",
            pemToV2RayCertChainHash(chain),
        )
    }

    @Test
    fun `pemToHysteriaCertSha256Hex should hash only the first certificate`() {
        assertEquals(
            "5b35d2359fa06475eebd2ef7d04ff0cb7194699e59e1dc05702c5f820570a87f",
            pemToHysteriaCertSha256Hex(chain),
        )
    }

    @Test
    fun `pemToSingPublicKeySha256 should hash the first certificate public key`() {
        assertEquals(
            "qwT9Bo6eIZbZJey+ORvwdmb/Cr6wHEWYPLcYBlrvnVE=",
            pemToSingPublicKeySha256(chain),
        )
    }

    @Test
    fun `hashes should be empty without PEM blocks`() {
        assertEquals("", pemToV2RayCertChainHash("not a certificate"))
        assertEquals("", pemToHysteriaCertSha256Hex("not a certificate"))
        assertEquals("", pemToSingPublicKeySha256("not a certificate"))
    }
}
