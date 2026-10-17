package fr.husi.fmt.tailscale

import fr.husi.fmt.BeanConverters
import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.parseOutbound
import fr.husi.ktx.JSONMap
import fr.husi.platform.PlatformInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TailscaleFmtTest {

    @Test
    fun `buildSingBoxEndpointTailscaleBean should map configured fields`() {
        val bean = TailscaleBean().apply {
            stateDirectory = "tailscale-home"
            authKey = "tskey-auth"
            controlURL = "https://headscale.example.com"
            ephemeral = true
            hostname = "husi"
            acceptRoutes = true
            exitNode = "100.64.0.1"
            exitNodeAllowLANAccess = true
            advertiseRoutes = "192.168.1.0/24\n10.0.0.0/8"
            advertiseTags = "tag:a,tag:b"
            listenPort = 41641
            sshServer = true
            taildropDirectory = "/home/user/Taildrop"
        }

        val endpoint = buildSingBoxEndpointTailscaleBean(bean)

        assertEquals(SingBoxOptions.TYPE_TAILSCALE, endpoint.type)
        assertEquals("tailscale-home", endpoint.state_directory)
        assertEquals("tskey-auth", endpoint.auth_key)
        assertEquals("https://headscale.example.com", endpoint.control_url)
        assertEquals(true, endpoint.ephemeral)
        assertEquals("husi", endpoint.hostname)
        assertEquals(true, endpoint.accept_routes)
        assertEquals("100.64.0.1", endpoint.exit_node)
        assertEquals(true, endpoint.exit_node_allow_lan_access)
        assertEquals<List<String>?>(listOf("192.168.1.0/24", "10.0.0.0/8"), endpoint.advertise_routes)
        assertNull(endpoint.advertise_exit_node)
        assertEquals<List<String>?>(listOf("tag:a", "tag:b"), endpoint.advertise_tags)
        assertEquals(41641, endpoint.listen_port)
        assertEquals(true, endpoint.ssh_server?.enabled)
        if (PlatformInfo.isAndroid) {
            assertNotEquals("/home/user/Taildrop", endpoint.taildrop_directory)
        } else {
            assertEquals("/home/user/Taildrop", endpoint.taildrop_directory)
        }
    }

    @Test
    fun `buildSingBoxEndpointTailscaleBean should leave defaults unset`() {
        val endpoint = buildSingBoxEndpointTailscaleBean(TailscaleBean())

        assertNull(endpoint.state_directory)
        assertNull(endpoint.auth_key)
        assertNull(endpoint.control_url)
        assertNull(endpoint.ephemeral)
        assertNull(endpoint.hostname)
        assertNull(endpoint.advertise_routes)
        assertNull(endpoint.listen_port)
        assertNull(endpoint.ssh_server)
        assertNull(endpoint.taildrop_directory)
    }

    @Test
    fun `buildSingBoxEndpointTailscaleBean should append extra routes to advertised routes`() {
        val bean = TailscaleBean().apply {
            advertiseRoutes = "192.168.1.0/24"
        }

        val endpoint = buildSingBoxEndpointTailscaleBean(bean, listOf("198.18.0.0/15", "fc00::/18"))

        assertEquals<List<String>?>(
            listOf("192.168.1.0/24", "198.18.0.0/15", "fc00::/18"),
            endpoint.advertise_routes,
        )
    }

    @Test
    fun `buildSingBoxEndpointTailscaleBean should advertise extra routes alone`() {
        val endpoint = buildSingBoxEndpointTailscaleBean(TailscaleBean(), listOf("198.18.0.0/15"))

        assertEquals<List<String>?>(listOf("198.18.0.0/15"), endpoint.advertise_routes)
    }

    @Test
    fun `buildSingBoxEndpointTailscaleBean should deduplicate extra routes`() {
        val bean = TailscaleBean().apply {
            advertiseRoutes = "198.18.0.0/15\n10.0.0.0/8"
        }

        val endpoint = buildSingBoxEndpointTailscaleBean(bean, listOf("198.18.0.0/15", "198.18.0.0/15"))

        assertEquals<List<String>?>(listOf("198.18.0.0/15", "10.0.0.0/8"), endpoint.advertise_routes)
    }

    @Test
    fun `parseOutbound should read a Tailscale endpoint`() {
        val json: JSONMap = mutableMapOf(
            "type" to "tailscale",
            "tag" to "ts-ep",
            "control_url" to "https://headscale.example.com",
            "advertise_routes" to listOf("192.168.1.0/24"),
            "advertise_tags" to "tag:server",
            "accept_routes" to true,
            "listen_port" to 41641L,
            "ssh_server" to mapOf("enabled" to true, "disable_pty" to true),
            "taildrop_directory" to "Inbox",
        )

        val bean = assertIs<TailscaleBean>(parseOutbound(json))

        assertEquals("ts-ep", bean.name)
        assertEquals("https://headscale.example.com", bean.controlURL)
        assertEquals("192.168.1.0/24", bean.advertiseRoutes)
        assertEquals("tag:server", bean.advertiseTags)
        assertTrue(bean.acceptRoutes)
        assertEquals(41641, bean.listenPort)
        assertTrue(bean.sshServer)
        assertEquals("Inbox", bean.taildropDirectory)
    }

    @Test
    fun `TailscaleBean should survive binary serialization`() {
        val source = TailscaleBean().apply {
            name = "ts"
            stateDirectory = "state"
            authKey = "key"
            controlURL = "https://control.example.com"
            ephemeral = true
            hostname = "host"
            acceptRoutes = true
            exitNode = "exit"
            exitNodeAllowLANAccess = true
            advertiseRoutes = "10.0.0.0/8"
            advertiseExitNode = true
            advertiseTags = "tag:a"
            listenPort = 1234
            sshServer = true
            taildropDirectory = "drop"
            advertiseFakeDNSRoutes = true
        }

        val parsed = BeanConverters.deserialize(TailscaleBean(), BeanConverters.serialize(source))

        assertEquals(BeanConverters.serialize(source).toList(), BeanConverters.serialize(parsed).toList())
        assertEquals("drop", parsed.taildropDirectory)
        assertTrue(parsed.advertiseFakeDNSRoutes)
    }
}
