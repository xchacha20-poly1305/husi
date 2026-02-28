package fr.husi.fmt.wireguard

import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WireGuardFmtTest {

    @Test
    fun `genReserved should encode three-element comma list as base64`() {
        assertEquals("AAEC", genReserved("0,1,2"))
    }

    @Test
    fun `genReserved should handle bracketed whitespace list`() {
        assertEquals("AAEC", genReserved("[0, 1, 2]"))
    }

    @Test
    fun `genReserved should pass through non-list string unchanged`() {
        assertEquals("AAEC", genReserved("AAEC"))
    }

    @Test
    fun `genReserved should pass through base64 string with single element`() {
        val base64 = "abc"
        assertEquals(base64, genReserved(base64))
    }

    @Test
    fun `buildSingBoxEndpointWireGuardBean should map all fields`() {
        val bean = WireGuardBean().apply {
            serverAddress = "vpn.example.com"
            serverPort = 51820
            publicKey = "pub-key-base64"
            preSharedKey = "psk-base64"
            privateKey = "priv-key-base64"
            localAddress = "10.0.0.2/32\nfc00::2/128"
            mtu = 1420
            persistentKeepaliveInterval = 25
            reserved = "AAEC"
        }

        val endpoint = buildSingBoxEndpointWireGuardBean(bean)

        assertEquals("wireguard", endpoint.type)
        assertEquals("priv-key-base64", endpoint.private_key)
        assertEquals(1420, endpoint.mtu)
        assertEquals(listOf("10.0.0.2/32", "fc00::2/128"), endpoint.address?.toList())

        val peer = assertNotNull(endpoint.peers?.firstOrNull())
        assertEquals("vpn.example.com", peer.address)
        assertEquals(51820, peer.port)
        assertEquals("pub-key-base64", peer.public_key)
        assertEquals("psk-base64", peer.pre_shared_key)
        assertEquals(25, peer.persistent_keepalive_interval)
        assertEquals("AAEC", peer.reserved)
    }

    @Test
    fun `buildSingBoxEndpointWireGuardBean should omit listen_port when zero`() {
        val bean = WireGuardBean().apply {
            serverAddress = "example.com"
            serverPort = 51820
            privateKey = "priv-key"
            localAddress = "10.0.0.2/32"
            listenPort = 0
        }

        val endpoint = buildSingBoxEndpointWireGuardBean(bean)

        assertNull(endpoint.listen_port)
    }

    @Test
    fun `parseWireGuardEndpoint should map tag address and peer fields`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "wg-node",
            "mtu" to 1420L,
            "address" to listOf("10.0.0.2/32", "fc00::2/128"),
            "listen_port" to 12345L,
            "private_key" to "priv-key-base64",
            "peers" to listOf(
                mutableMapOf(
                    "address" to "vpn.example.com",
                    "port" to 51820L,
                    "public_key" to "pub-key-base64",
                    "pre_shared_key" to "psk-base64",
                    "persistent_keepalive_interval" to 25L,
                    "reserved" to "AAEC",
                ),
            ),
        )

        val bean = assertNotNull(parseWireGuardEndpoint(json))

        assertEquals("wg-node", bean.name)
        assertEquals(1420, bean.mtu)
        assertEquals("10.0.0.2/32\nfc00::2/128", bean.localAddress)
        assertEquals(12345, bean.listenPort)
        assertEquals("priv-key-base64", bean.privateKey)
        assertEquals("vpn.example.com", bean.serverAddress)
        assertEquals(51820, bean.serverPort)
        assertEquals("pub-key-base64", bean.publicKey)
        assertEquals("psk-base64", bean.preSharedKey)
        assertEquals(25, bean.persistentKeepaliveInterval)
        assertEquals("AAEC", bean.reserved)
    }

    @Test
    fun `parseWireGuardEndpoint should return null when peers is missing`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "wg-node",
            "private_key" to "key",
        )

        assertNull(parseWireGuardEndpoint(json))
    }
}
