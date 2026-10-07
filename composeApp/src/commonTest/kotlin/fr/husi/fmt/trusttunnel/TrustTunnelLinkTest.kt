package fr.husi.fmt.trusttunnel

import fr.husi.io.writeQuicVarint
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Links from two sources: sing-trusttunnel's tturl, which the Go side used before, and TrustTunnel's
 * own `deeplink` crate tests, encoded by its reference `scripts/config_to_deeplink.py`.
 */
class TrustTunnelLinkTest {

    // tturl

    private val minimalLink = "tt://?AAEBAQcxLjIuMy40AgsxLjIuMy40OjQ0MwUFYWxpY2UGCXNlY3JldDEyMw"
    private val minimal = TrustTunnelLink(
        hostname = "1.2.3.4",
        addresses = listOf("1.2.3.4:443"),
        username = "alice",
        password = "secret123",
    )

    private val fullLink =
        "tt://?AAEBAQ9zbmkuZXhhbXBsZS5vcmcCFHZwbi5leGFtcGxlLmNvbTo4NDQzAw9zbmkuZXhhbXBsZS5vcmcFA2JvYgYCcHcHAQEIBDCCASMJAQIMBk15IFZQTg"
    private val full = TrustTunnelLink(
        hostname = "sni.example.org",
        addresses = listOf("vpn.example.com:8443"),
        customSni = "sni.example.org",
        username = "bob",
        password = "pw",
        skipVerification = true,
        certificate = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x23).toByteString(),
        http3 = true,
        name = "My VPN",
    )

    @Test
    fun `build matches tturl byte for byte`() {
        assertEquals(minimalLink, minimal.build())
        assertEquals(fullLink, full.build())
    }

    @Test
    fun `parse reads what tturl built`() {
        assertEquals(minimal, TrustTunnelLink.parse(minimalLink))
        assertEquals(full, TrustTunnelLink.parse(fullLink))
    }

    @Test
    fun `parse skips records with no sing-box counterpart`() {
        // Anti-DPI, client random prefix and DNS upstreams, around a bracketed IPv6 address.
        val link = "tt://?AAEBAQsyMDAxOmRiODo6MQIRWzIwMDE6ZGI4OjoxXTo0NDMFAXUGAXALAmFhCgEBDQgHMS4xLjEuMQ"

        val parsed = TrustTunnelLink.parse(link)

        assertEquals(listOf("[2001:db8::1]:443"), parsed.addresses)
    }

    @Test
    fun `parse accepts the pre draft 2 form without question mark`() {
        assertEquals(minimal, TrustTunnelLink.parse(minimalLink.replace("tt://?", "tt://")))
    }

    @Test
    fun `parse rejects a truncated record`() {
        assertFailsWith<IllegalArgumentException> {
            TrustTunnelLink.parse(minimalLink.dropLast(4))
        }
    }

    // TrustTunnel python_compat.rs and roundtrip.rs: links its reference encoder writes, at version 2.

    @Test
    fun `parse reads a minimal upstream link`() {
        assertEquals(
            TrustTunnelLink(
                hostname = "vpn.example.com",
                addresses = listOf("1.2.3.4:443"),
                username = "alice",
                password = "secret123",
            ),
            TrustTunnelLink.parse("tt://?AAECAQ92cG4uZXhhbXBsZS5jb20FBWFsaWNlBglzZWNyZXQxMjMCCzEuMi4zLjQ6NDQz"),
        )
    }

    @Test
    fun `parse reads an upstream link with every non-default record`() {
        // Also carries has_ipv6 = false and anti_dpi = true, which have no sing-box counterpart.
        val link = "tt://?AAECARZzZWN1cmUudnBuLmV4YW1wbGUuY29tBQxwcmVtaXVtX3VzZXIGGHZlcnlfc2VjcmV0X3Bhc3N3b3JkXzEyMwIQ" +
            "MTkyLjE2OC4xLjE6ODQ0MwIMMTAuMC4wLjE6NDQzAw9jZG4uZXhhbXBsZS5vcmcEAQAHAQEKAQEIBDCCASMJAQI"

        assertEquals(
            TrustTunnelLink(
                hostname = "secure.vpn.example.com",
                addresses = listOf("192.168.1.1:8443", "10.0.0.1:443"),
                customSni = "cdn.example.org",
                username = "premium_user",
                password = "very_secret_password_123",
                skipVerification = true,
                certificate = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x23).toByteString(),
                http3 = true,
            ),
            TrustTunnelLink.parse(link),
        )
    }

    @Test
    fun `parse reads an upstream link with bracketed IPv6 addresses`() {
        val link = "tt://?AAECARB2cG42LmV4YW1wbGUuY29tBQhpcHY2dXNlcgYIaXB2NnBhc3MCEVsyMDAxOmRiODo6MV06NDQzAgpbOjoxXTo4NDQz"

        assertEquals(listOf("[2001:db8::1]:443", "[::1]:8443"), TrustTunnelLink.parse(link).addresses)
    }

    @Test
    fun `parse keeps the whole certificate chain of an upstream link`() {
        val link = "tt://?AAECAQ52cG4uc2VjdXJlLmNvbQUEdXNlcgYEcGFzcwIPMjAzLjAuMTEzLjE6NDQzCAswAwECAzAEBAUGBw"

        assertEquals(
            bytes(0x30, 0x03, 0x01, 0x02, 0x03, 0x30, 0x04, 0x04, 0x05, 0x06, 0x07),
            TrustTunnelLink.parse(link).certificate,
        )
    }

    @Test
    fun `parse reads name and skips the DNS upstreams of an upstream link`() {
        val link = "tt://?AAECAQ92cG4uZXhhbXBsZS5jb20FBWFsaWNlBglzZWNyZXQxMjMCCzEuMi4zLjQ6NDQzDAlNeSBTZXJ2ZXINEAcxLjEuMS4xBzguOC44Ljg"

        assertEquals("My Server", TrustTunnelLink.parse(link).name)
    }

    @Test
    fun `parse skips the client random prefix of an upstream link`() {
        val link = "tt://?AAECAQ9jcnAuZXhhbXBsZS5jb20FCHRlc3R1c2VyBgh0ZXN0cGFzcwIQMTAuMjAuMzAuNDA6ODQ0MwsKYWFiYmNjZGRlZQ"

        assertEquals(listOf("10.20.30.40:8443"), TrustTunnelLink.parse(link).addresses)
    }

    @Test
    fun `parse keeps the server of an upstream link that also has a subscription URL`() {
        val link = "tt://?AAECAQ92cG4uZXhhbXBsZS5jb20FBWFsaWNlBgZzM2NyZXQCCzEuMi4zLjQ6NDQzDjFodHRwczovL2FsaWNlOnMzY3JldEB2cG4u" +
            "ZXhhbXBsZS5jb20vc3Vic2NyaXB0aW9u"

        assertEquals(
            TrustTunnelLink(
                hostname = "vpn.example.com",
                addresses = listOf("1.2.3.4:443"),
                username = "alice",
                password = "s3cret",
            ),
            TrustTunnelLink.parse(link),
        )
    }

    @Test
    fun `parse rejects an upstream subscription-only link`() {
        val link = "tt://?AAECDjFodHRwczovL2FsaWNlOnMzY3JldEB2cG4uZXhhbXBsZS5jb20vc3Vic2NyaXB0aW9u"

        assertFailsWith<IllegalArgumentException> { TrustTunnelLink.parse(link) }
    }

    @Test
    fun `build output parses back`() {
        val links = listOf(
            // test_roundtrip_multiple_addresses
            TrustTunnelLink(
                hostname = "multi.vpn.com",
                addresses = listOf("1.1.1.1:443", "8.8.8.8:8443", "9.9.9.9:9443"),
                username = "multiaddr",
                password = "test123",
            ),
            // test_roundtrip_long_values: lengths past the one-byte varint
            TrustTunnelLink(
                hostname = "sub".repeat(50) + ".vpn.example.com",
                addresses = listOf("1.2.3.4:443"),
                username = "user",
                password = "a".repeat(200),
            ),
            // test_roundtrip_special_characters
            TrustTunnelLink(
                hostname = "vpn.example.com",
                addresses = listOf("1.2.3.4:443"),
                customSni = "cdn-123.example.org",
                username = "user@example.com",
                password = "p@ss!w0rd#123",
            ),
            // test_roundtrip_ipv6_addresses
            TrustTunnelLink(
                hostname = "vpn6.example.com",
                addresses = listOf("[2001:db8::1]:443", "[::1]:8443"),
                username = "ipv6user",
                password = "ipv6pass",
            ),
            // test_roundtrip_with_certificate
            TrustTunnelLink(
                hostname = "vpn.secure.com",
                addresses = listOf("203.0.113.1:443"),
                username = "user",
                password = "pass",
                certificate = bytes(
                    0x30, 0x82, 0x03, 0x52, 0x30, 0x82, 0x02, 0x3A, 0xA0, 0x03, 0x02, 0x01, 0x02, 0x02, 0x09, 0x00,
                ),
            ),
            // test_roundtrip_non_default_values, test_roundtrip_with_name
            TrustTunnelLink(
                hostname = "vpn.example.com",
                addresses = listOf("1.2.3.4:443"),
                username = "user",
                password = "pass",
                skipVerification = true,
                http3 = true,
                name = "My VPN Server",
            ),
        )
        for (link in links) {
            assertEquals(link, TrustTunnelLink.parse(link.build()))
        }
    }

    // TrustTunnel decode.rs and roundtrip.rs: hand-built records.

    @Test
    fun `parse accepts every version up to 2`() {
        for (version in 0L..2L) {
            assertEquals(minimalServer, TrustTunnelLink.parse(link(versionRecord(version), *minimalServerRecords)))
        }
    }

    @Test
    fun `parse assumes version 0 without a version record`() {
        assertEquals(minimalServer, TrustTunnelLink.parse(link(*minimalServerRecords)))
    }

    @Test
    fun `parse rejects a version above 2`() {
        for (version in listOf(3L, 99L)) {
            assertFailsWith<IllegalArgumentException> {
                TrustTunnelLink.parse(link(versionRecord(version), *minimalServerRecords))
            }
        }
    }

    @Test
    fun `parse ignores an unknown tag`() {
        val unknown = 0x0FL to bytes(0x01, 0x02, 0x03)

        assertEquals(minimalServer, TrustTunnelLink.parse(link(*minimalServerRecords, unknown)))
    }

    @Test
    fun `parse lets the last duplicate record win`() {
        val parsed = TrustTunnelLink.parse(link(*minimalServerRecords, TAG_USERNAME to "bob".encodeUtf8()))

        assertEquals("bob", parsed.username)
    }

    @Test
    fun `parse rejects a record longer than the payload`() {
        // Tag 0x01, length 10, but only 3 bytes of value.
        assertFailsWith<IllegalArgumentException> {
            TrustTunnelLink.parse("tt://?" + bytes(0x01, 0x0A, 0x01, 0x02, 0x03).base64Url().trimEnd('='))
        }
    }

    @Test
    fun `parse rejects a missing required record`() {
        for (missing in minimalServerRecords) {
            val records = minimalServerRecords.filter { it !== missing }.toTypedArray()
            assertFailsWith<IllegalArgumentException> { TrustTunnelLink.parse(link(versionRecord(1), *records)) }
        }
    }

    @Test
    fun `parse reads booleans as 0 or 1 only`() {
        val skip = { value: Int -> TAG_SKIP_VERIFICATION to bytes(value) }

        assertEquals(false, TrustTunnelLink.parse(link(*minimalServerRecords, skip(0))).skipVerification)
        assertEquals(true, TrustTunnelLink.parse(link(*minimalServerRecords, skip(1))).skipVerification)
        assertFailsWith<IllegalArgumentException> { TrustTunnelLink.parse(link(*minimalServerRecords, skip(2))) }
        assertFailsWith<IllegalArgumentException> {
            TrustTunnelLink.parse(link(*minimalServerRecords, TAG_SKIP_VERIFICATION to bytes(0x00, 0x01)))
        }
    }

    @Test
    fun `parse rejects an unknown upstream protocol`() {
        assertFailsWith<IllegalArgumentException> {
            TrustTunnelLink.parse(link(*minimalServerRecords, TAG_UPSTREAM_PROTOCOL to bytes(0x03)))
        }
    }

    @Test
    fun `parse rejects invalid UTF-8`() {
        assertFailsWith<IllegalArgumentException> {
            TrustTunnelLink.parse(link(*minimalServerRecords, TAG_HOSTNAME to bytes(0xFF, 0xFE)))
        }
    }

    @Test
    fun `parse rejects a subscription URL that is not https`() {
        val subscription = TAG_SUBSCRIPTION_URL to "http://vpn.example.com/subscription".encodeUtf8()

        assertFailsWith<IllegalArgumentException> {
            TrustTunnelLink.parse(link(versionRecord(2), *minimalServerRecords, subscription))
        }
    }

    @Test
    fun `parse rejects another scheme`() {
        assertFailsWith<IllegalArgumentException> { TrustTunnelLink.parse("http://example.com") }
    }

    @Test
    fun `bean round trips through a link`() {
        val bean = TrustTunnelBean().apply {
            serverAddress = "2001:db8::1"
            serverPort = 443
            serverName = "sni.example.org"
            username = "user"
            password = "pass"
            allowInsecure = true
            quic = true
            name = "node"
        }

        val parsed = parseTrustTunnel(bean.toUri())

        assertEquals(bean.serverAddress, parsed.serverAddress)
        assertEquals(bean.serverPort, parsed.serverPort)
        assertEquals(bean.serverName, parsed.serverName)
        assertEquals(bean.username, parsed.username)
        assertEquals(bean.password, parsed.password)
        assertEquals(bean.allowInsecure, parsed.allowInsecure)
        assertEquals(bean.quic, parsed.quic)
        assertEquals(bean.name, parsed.name)
        assertEquals("", parsed.certificates)
    }

    @Test
    fun `bean keeps a certificate chain through a link`() {
        val bean = TrustTunnelBean().apply {
            serverAddress = "203.0.113.1"
            serverPort = 443
            username = "user"
            password = "pass"
            certificates = certificateChainToPem(bytes(0x30, 0x03, 0x01, 0x02, 0x03, 0x30, 0x04, 0x04, 0x05, 0x06, 0x07))
        }

        assertEquals(bean.certificates, parseTrustTunnel(bean.toUri()).certificates)
    }

    private val minimalServer = TrustTunnelLink(
        hostname = "vpn.example.com",
        addresses = listOf("1.2.3.4:443"),
        username = "alice",
        password = "secret",
    )

    private val minimalServerRecords = arrayOf(
        TAG_HOSTNAME to "vpn.example.com".encodeUtf8(),
        TAG_ADDRESSES to "1.2.3.4:443".encodeUtf8(),
        TAG_USERNAME to "alice".encodeUtf8(),
        TAG_PASSWORD to "secret".encodeUtf8(),
    )

    private fun versionRecord(version: Long) = TAG_VERSION to Buffer().also { it.writeQuicVarint(version) }.readByteString()

    private fun link(vararg records: Pair<Long, ByteString>): String {
        val payload = Buffer()
        for ((tag, value) in records) {
            payload.writeQuicVarint(tag)
            payload.writeQuicVarint(value.size.toLong())
            payload.write(value)
        }
        return "tt://?" + payload.readByteString().base64Url().trimEnd('=')
    }

    private fun bytes(vararg values: Int): ByteString = ByteArray(values.size) { values[it].toByte() }.toByteString()

    private companion object {
        const val TAG_VERSION = 0x00L
        const val TAG_HOSTNAME = 0x01L
        const val TAG_ADDRESSES = 0x02L
        const val TAG_USERNAME = 0x05L
        const val TAG_PASSWORD = 0x06L
        const val TAG_SKIP_VERIFICATION = 0x07L
        const val TAG_UPSTREAM_PROTOCOL = 0x09L
        const val TAG_SUBSCRIPTION_URL = 0x0EL
    }
}
