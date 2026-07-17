package fr.husi.fmt.openvpn

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.isEndpoint
import fr.husi.fmt.parseOutbound
import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenVPNFmtTest {

    @Test
    fun `endpoint maps transport authentication and TLS material`() {
        val endpoint = buildSingBoxEndpointOpenVPNBean(OpenVPNBean().apply {
            serverAddress = "vpn.example.com"
            serverPort = 443
            network = "tcp"
            username = "alice"
            password = "secret"
            certificate = "ca"
            clientCertificate = "client-cert"
            clientKey = "client-key"
            controlWrapType = "tls_crypt"
            controlWrapKey = "wrap-key"
            serverName = "vpn.example.com"
            serverNameType = "name"
            peerFingerprint = "SHA256:abc"
            remoteCertificateKU = "a0"
            dataCiphers = "AES-256-GCM\nCHACHA20-POLY1305"
            auth = "SHA256"
            compression = "stub-v2"
            redirectGateway = true
            mtu = 1400
        })

        assertEquals(SingBoxOptions.TYPE_OPENVPN_CLIENT, endpoint.type)
        assertEquals("vpn.example.com", endpoint.server)
        assertEquals(443, endpoint.server_port)
        assertEquals("tcp", endpoint.network)
        assertEquals<List<String>?>(listOf("ca"), endpoint.tls?.certificate)
        assertEquals("tls_crypt", endpoint.tls?.control_wrap?.type)
        assertEquals<List<String>?>(listOf("wrap-key"), endpoint.tls?.control_wrap?.key)
        assertEquals("vpn.example.com", endpoint.tls?.server_name)
        assertEquals("name", endpoint.tls?.server_name_type)
        assertEquals<List<String>?>(listOf("SHA256:abc"), endpoint.tls?.peer_fingerprint)
        assertEquals<List<String>?>(listOf("a0"), endpoint.tls?.remote_certificate_ku)
        assertEquals<List<String>?>(listOf("AES-256-GCM", "CHACHA20-POLY1305"), endpoint.data_ciphers)
        assertEquals("SHA256", endpoint.auth)
        assertEquals("stub-v2", endpoint.compression)
        assertEquals(true, endpoint.redirect_gateway)
        assertEquals(1400, endpoint.mtu)
        assertTrue(isEndpoint(endpoint.type!!))
    }

    @Test
    fun `parseOutbound recognizes OpenVPN endpoint`() {
        val json: JSONMap = mutableMapOf(
            "type" to SingBoxOptions.TYPE_OPENVPN_CLIENT,
            "tag" to "work-vpn",
            "server" to "vpn.example.com",
            "server_port" to 443L,
            "network" to "tcp",
            "username" to "alice",
            "password" to "secret",
            "data_ciphers" to listOf("AES-256-GCM", "CHACHA20-POLY1305"),
            "auth" to "SHA256",
            "compression" to "stub-v2",
            "redirect_gateway" to true,
            "mtu" to 1400L,
            "tls" to mutableMapOf(
                "server_name" to "vpn.example.com",
                "server_name_type" to "name",
                "certificate" to listOf("ca-one", "ca-two"),
                "client_certificate" to listOf("client-cert"),
                "client_key" to listOf("client-key"),
                "peer_fingerprint" to listOf("SHA256:abc", "SHA256:def"),
                "remote_certificate_ku" to listOf("a0", "88"),
                "remote_certificate_eku" to "TLS Web Server Authentication",
                "control_wrap" to mutableMapOf(
                    "type" to "tls_crypt",
                    "key" to listOf("wrap-one", "wrap-two"),
                    "direction" to "1",
                ),
            ),
        )

        val bean = assertIs<OpenVPNBean>(parseOutbound(json))

        assertEquals("work-vpn", bean.name)
        assertEquals("vpn.example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("tcp", bean.network)
        assertEquals("AES-256-GCM\nCHACHA20-POLY1305", bean.dataCiphers)
        assertEquals("SHA256", bean.auth)
        assertEquals("stub-v2", bean.compression)
        assertEquals(true, bean.redirectGateway)
        assertEquals(1400, bean.mtu)
        assertEquals("vpn.example.com", bean.serverName)
        assertEquals("name", bean.serverNameType)
        assertEquals("ca-one\nca-two", bean.certificate)
        assertEquals("client-cert", bean.clientCertificate)
        assertEquals("client-key", bean.clientKey)
        assertEquals("SHA256:abc\nSHA256:def", bean.peerFingerprint)
        assertEquals("a0\n88", bean.remoteCertificateKU)
        assertEquals("TLS Web Server Authentication", bean.remoteCertificateEKU)
        assertEquals("tls_crypt", bean.controlWrapType)
        assertEquals("wrap-one\nwrap-two", bean.controlWrapKey)
        assertEquals("1", bean.controlWrapDirection)
    }
}
