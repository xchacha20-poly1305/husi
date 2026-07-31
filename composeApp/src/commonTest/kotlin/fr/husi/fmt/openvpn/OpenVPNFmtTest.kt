package fr.husi.fmt.openvpn

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.isEndpoint
import fr.husi.fmt.parseOutbound
import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

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

    @Test
    fun `parseOpenVPNConfig recognizes inline configuration`() {
        val config = """
            ############################
            #        VPN Gate          #
            ############################
            client
            dev tun
            proto tcp-client
            remote vpn.example.com 443
            auth SHA256
            data-ciphers AES-256-GCM:CHACHA20-POLY1305
            redirect-gateway def1
            tun-mtu 1400
            verify-x509-name vpn.example.com name
            remote-cert-tls server
            tls-auth [inline] 1
            <ca>
            -----BEGIN CERTIFICATE-----
            test-ca
            -----END CERTIFICATE-----
            </ca>
            <cert>
            -----BEGIN CERTIFICATE-----
            test-client-cert
            -----END CERTIFICATE-----
            </cert>
            <key>
            -----BEGIN PRIVATE KEY-----
            test-client-key
            -----END PRIVATE KEY-----
            </key>
            <tls-auth>
            test-control-key
            </tls-auth>
        """.trimIndent()

        val bean = assertNotNull(parseOpenVPNConfig(config))

        assertEquals("vpn.example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("tcp", bean.network)
        assertEquals("AES-256-GCM\nCHACHA20-POLY1305", bean.dataCiphers)
        assertEquals("SHA256", bean.auth)
        assertTrue(bean.redirectGateway)
        assertEquals(1400, bean.mtu)
        assertEquals("vpn.example.com", bean.serverName)
        assertEquals("name", bean.serverNameType)
        assertEquals("server", bean.remoteCertificateEKU)
        assertEquals("tls_auth", bean.controlWrapType)
        assertEquals("client", bean.controlWrapDirection)
        assertTrue(bean.certificate.contains("test-ca"))
        assertTrue(bean.clientCertificate.contains("test-client-cert"))
        assertTrue(bean.clientKey.contains("test-client-key"))
        assertEquals("test-control-key", bean.controlWrapKey)
    }

    @Test
    fun `parseOpenVPNConfig reads standalone key-direction`() {
        val config = """
            client
            dev tun
            proto udp
            remote vpn.example.com 1194
            remote-cert-tls server
            auth-user-pass
            cipher AES-256-CBC
            auth SHA512
            <ca>
            test-ca
            </ca>
            key-direction 1
            <tls-auth>
            #
            # 2048 bit OpenVPN static key
            #
            test-control-key
            </tls-auth>
        """.trimIndent()

        val bean = assertNotNull(parseOpenVPNConfig(config))

        assertEquals("vpn.example.com", bean.serverAddress)
        assertEquals(1194, bean.serverPort)
        assertEquals("udp", bean.network)
        assertEquals("AES-256-CBC", bean.dataCiphers)
        assertEquals("tls_auth", bean.controlWrapType)
        assertEquals("client", bean.controlWrapDirection)
        assertTrue(bean.controlWrapKey.contains("test-control-key"))
    }

    @Test
    fun `parseOpenVPNConfig ignores key-direction without tls-auth`() {
        val config = """
            client
            remote vpn.example.com 1194
            key-direction 1
            <ca>
            test-ca
            </ca>
            <tls-crypt>
            test-control-key
            </tls-crypt>
        """.trimIndent()

        val bean = assertNotNull(parseOpenVPNConfig(config))

        assertEquals("tls_crypt", bean.controlWrapType)
        assertEquals("", bean.controlWrapDirection)
    }

    @Test
    fun `parseOpenVPNConfig reads inline credentials`() {
        val config = """
            client
            remote vpn.example.com 1194
            compress
            <auth-user-pass>
            alice
            secret
            </auth-user-pass>
            <ca>
            test-ca
            </ca>
        """.trimIndent()

        val bean = assertNotNull(parseOpenVPNConfig(config))

        assertEquals("alice", bean.username)
        assertEquals("secret", bean.password)
        assertEquals("stub", bean.compression)
    }

    @Test
    fun `parseOpenVPNConfig reads remote from connection block`() {
        val config = """
            client
            dev tun
            data-ciphers AES-256-GCM:AES-256-CBC

            <connection>
            remote 192.0.2.1 443 udp
            nobind
            connect-retry 5 5
            </connection>

            <connection>
            remote 192.0.2.1 443 tcp-client
            nobind
            </connection>

            <ca>
            test-ca
            </ca>
        """.trimIndent()

        val bean = assertNotNull(parseOpenVPNConfig(config))

        assertEquals("192.0.2.1", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("udp", bean.network)
        assertEquals("AES-256-GCM\nAES-256-CBC", bean.dataCiphers)
    }

    @Test
    fun `parseOpenVPNConfig ignores directive like lines inside material blocks`() {
        val config = """
            client
            remote vpn.example.com 1194
            <ca>
            test-ca
            auth SHA512
            </ca>
        """.trimIndent()

        val bean = assertNotNull(parseOpenVPNConfig(config))

        assertEquals("", bean.auth)
        assertTrue(bean.certificate.contains("auth SHA512"))
    }

    @Test
    fun `parseOpenVPNConfig rejects missing trusted server certificate`() {
        val error = assertFailsWith<IllegalStateException> {
            parseOpenVPNConfig("remote vpn.example.com 443")
        }

        assertEquals("OpenVPN configuration requires an inline <ca> block or peer-fingerprint.", error.message)
    }

    @Test
    fun `parseOpenVPNConfig rejects missing remote server`() {
        val error = assertFailsWith<IllegalStateException> {
            parseOpenVPNConfig(
                """
                client
                <ca>
                test-ca
                </ca>
                """.trimIndent(),
            )
        }

        assertEquals("OpenVPN configuration is missing a remote server.", error.message)
    }

    @Test
    fun `parseOpenVPNConfig rejects unpaired client certificate`() {
        val error = assertFailsWith<IllegalStateException> {
            parseOpenVPNConfig(
                """
                remote vpn.example.com 443
                <ca>
                test-ca
                </ca>
                <cert>
                test-client-cert
                </cert>
                """.trimIndent(),
            )
        }

        assertEquals("OpenVPN client certificate and private key must be provided together.", error.message)
    }

    @Test
    fun `parseOpenVPNConfig rejects unclosed inline block`() {
        val error = assertFailsWith<IllegalStateException> {
            parseOpenVPNConfig(
                """
                remote vpn.example.com 443
                <ca>
                -----BEGIN CERTIFICATE-----
                test-ca
                """.trimIndent(),
            )
        }

        assertEquals("OpenVPN inline <ca> block is not closed.", error.message)
    }
}
