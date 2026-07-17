package fr.husi.fmt.openconnect

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.isEndpoint
import fr.husi.fmt.parseOutbound
import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenConnectFmtTest {

    @Test
    fun `endpoint maps authentication and TLS material`() {
        val endpoint = buildSingBoxEndpointOpenConnectBean(OpenConnectBean().apply {
            server = "https://vpn.example.com"
            flavor = "anyconnect"
            username = "alice"
            password = "secret"
            authGroup = "employees"
            reportedOS = "android"
            userAgent = "Husi/1.0"
            allowInsecureCrypto = true
            certificateAuthority = "ca-one\nca-two"
            mcaCertificate = "machine-cert"
            mcaKey = "machine-key"
        })

        assertEquals(SingBoxOptions.TYPE_OPENCONNECT, endpoint.type)
        assertEquals("https://vpn.example.com", endpoint.server)
        assertEquals("anyconnect", endpoint.flavor)
        assertEquals("employees", endpoint.auth_group)
        assertEquals("android", endpoint.reported_os)
        assertEquals("Husi/1.0", endpoint.user_agent)
        assertEquals(true, endpoint.allow_insecure_crypto)
        assertEquals(null, endpoint.no_udp)
        assertEquals<List<String>?>(listOf("ca-one", "ca-two"), endpoint.tls?.certificate_authority)
        assertEquals<List<String>?>(listOf("machine-cert"), endpoint.tls?.mca_certificate)
        assertEquals<List<String>?>(listOf("machine-key"), endpoint.tls?.mca_key)
        assertTrue(isEndpoint(endpoint.type!!))
    }

    @Test
    fun `parseOutbound recognizes OpenConnect endpoint`() {
        val json: JSONMap = mutableMapOf(
            "type" to SingBoxOptions.TYPE_OPENCONNECT,
            "tag" to "work-vpn",
            "server" to "https://vpn.example.com",
            "flavor" to "anyconnect",
            "username" to "alice",
            "password" to "secret",
            "auth_group" to "employees",
            "reported_os" to "android",
            "user_agent" to "Husi/1.0",
            "allow_insecure_crypto" to true,
            "tls" to mutableMapOf(
                "certificate_authority" to listOf("ca-one", "ca-two"),
                "client_certificate" to listOf("client-cert"),
                "client_key" to listOf("client-key"),
                "client_key_password" to "client-password",
                "mca_certificate" to listOf("machine-cert"),
                "mca_key" to listOf("machine-key"),
                "mca_key_password" to "machine-password",
            ),
        )

        val bean = assertIs<OpenConnectBean>(parseOutbound(json))

        assertEquals("work-vpn", bean.name)
        assertEquals("https://vpn.example.com", bean.server)
        assertEquals("anyconnect", bean.flavor)
        assertEquals("employees", bean.authGroup)
        assertEquals("android", bean.reportedOS)
        assertEquals("Husi/1.0", bean.userAgent)
        assertEquals(true, bean.allowInsecureCrypto)
        assertEquals("ca-one\nca-two", bean.certificateAuthority)
        assertEquals("client-cert", bean.clientCertificate)
        assertEquals("client-key", bean.clientKey)
        assertEquals("client-password", bean.clientKeyPassword)
        assertEquals("machine-cert", bean.mcaCertificate)
        assertEquals("machine-key", bean.mcaKey)
        assertEquals("machine-password", bean.mcaKeyPassword)
    }
}
