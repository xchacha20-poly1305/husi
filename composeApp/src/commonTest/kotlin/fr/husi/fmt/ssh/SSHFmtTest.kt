package fr.husi.fmt.ssh

import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SSHFmtTest {

    @Test
    fun `buildSingBoxOutboundSSHBean should map password auth fields`() {
        val bean = SSHBean().apply {
            serverAddress = "example.com"
            serverPort = 22
            username = "admin"
            authType = SSHBean.AUTH_TYPE_PASSWORD
            password = "secret"
        }

        val outbound = buildSingBoxOutboundSSHBean(bean)

        assertEquals("ssh", outbound.type)
        assertEquals("example.com", outbound.server)
        assertEquals(22, outbound.server_port)
        assertEquals("admin", outbound.user)
        assertEquals("secret", outbound.password)
        assertNull(outbound.private_key)
    }

    @Test
    fun `buildSingBoxOutboundSSHBean should map private key auth fields`() {
        val bean = SSHBean().apply {
            serverAddress = "example.com"
            serverPort = 22
            username = "user"
            authType = SSHBean.AUTH_TYPE_PRIVATE_KEY
            privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nABCD\n-----END OPENSSH PRIVATE KEY-----"
            privateKeyPassphrase = "passphrase"
        }

        val outbound = buildSingBoxOutboundSSHBean(bean)

        assertEquals(listOf(bean.privateKey), outbound.private_key?.toList())
        assertEquals("passphrase", outbound.private_key_passphrase)
        assertNull(outbound.password)
    }

    @Test
    fun `buildSingBoxOutboundSSHBean should set host_key when public key is not blank`() {
        val bean = SSHBean().apply {
            serverAddress = "example.com"
            serverPort = 22
            username = "user"
            authType = SSHBean.AUTH_TYPE_PASSWORD
            password = "pass"
            publicKey = "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAA"
        }

        val outbound = buildSingBoxOutboundSSHBean(bean)

        assertEquals(listOf(bean.publicKey), outbound.host_key?.toList())
    }

    @Test
    fun `parseSSHOutbound should map user and password auth`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "node-1",
            "server" to "example.com",
            "server_port" to 22L,
            "user" to "admin",
            "password" to "secret",
            "host_key" to "ecdsa-sha2-nistp256 AAAA",
        )

        val bean = parseSSHOutbound(json)

        assertEquals("node-1", bean.name)
        assertEquals("example.com", bean.serverAddress)
        assertEquals(22, bean.serverPort)
        assertEquals("admin", bean.username)
        assertEquals("secret", bean.password)
        assertEquals(SSHBean.AUTH_TYPE_PASSWORD, bean.authType)
        assertEquals("ecdsa-sha2-nistp256 AAAA", bean.publicKey)
    }

    @Test
    fun `parseSSHOutbound should map private key auth`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "node-1",
            "server" to "example.com",
            "server_port" to 22L,
            "user" to "user",
            "private_key" to listOf("-----BEGIN OPENSSH PRIVATE KEY-----\nABCD"),
            "private_key_passphrase" to "passphrase",
        )

        val bean = parseSSHOutbound(json)

        assertEquals("-----BEGIN OPENSSH PRIVATE KEY-----\nABCD", bean.privateKey)
        assertEquals("passphrase", bean.privateKeyPassphrase)
        assertEquals(SSHBean.AUTH_TYPE_PRIVATE_KEY, bean.authType)
    }
}
