package fr.husi.fmt.tailscale

import fr.husi.fmt.BeanConverters
import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.parseOutbound
import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
        assertEquals(listOf("192.168.1.0/24", "10.0.0.0/8"), endpoint.advertise_routes)
        assertNull(endpoint.advertise_exit_node)
        assertEquals(listOf("tag:a", "tag:b"), endpoint.advertise_tags)
        assertEquals(41641, endpoint.listen_port)
        assertEquals(true, endpoint.ssh_server?.enabled)
        // Tests run on the desktop JVM, where the Taildrop directory is honored.
        assertEquals("/home/user/Taildrop", endpoint.taildrop_directory)
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
        }

        val parsed = BeanConverters.deserialize(TailscaleBean(), BeanConverters.serialize(source))

        assertEquals(BeanConverters.serialize(source).toList(), BeanConverters.serialize(parsed).toList())
        assertEquals("drop", parsed.taildropDirectory)
    }

    @Test
    fun `TailscaleBean display name should fall back to hostname then type`() {
        assertEquals(SingBoxOptions.TYPE_TAILSCALE, TailscaleBean().displayName())
        assertEquals("host", TailscaleBean().apply { hostname = "host" }.displayName())
        assertEquals("named", TailscaleBean().apply {
            hostname = "host"
            name = "named"
        }.displayName())
    }

    @Test
    fun `buildSingBoxOutboundTailcatBean should use DERP map without custom servers`() {
        val bean = TailcatBean().apply {
            serverPublicKey = "nodekey:server"
            serverDiscoKey = "discokey:server"
            derpMapURL = "https://derp.example.com/map.json"
            derpRegion = 900
        }

        val outbound = buildSingBoxOutboundTailcatBean(bean)

        assertEquals(SingBoxOptions.TYPE_TAILCAT, outbound.type)
        assertNull(outbound.private_key)
        assertEquals("nodekey:server", outbound.server_public_key)
        assertEquals("discokey:server", outbound.server_disco_key)
        assertNull(outbound.pre_shared_key)
        assertEquals("https://derp.example.com/map.json", outbound.derp_map_url)
        assertEquals(900, outbound.derp_region)
        assertNull(outbound.derp_servers)
    }

    @Test
    fun `buildSingBoxOutboundTailcatBean should drop DERP map when servers are set`() {
        val bean = TailcatBean().apply {
            privateKey = "privkey:client"
            serverPublicKey = "nodekey:server"
            serverDiscoKey = "discokey:server"
            preSharedKey = "psk"
            derpMapURL = "https://derp.example.com/map.json"
            derpRegion = 900
            derpServers = "derp1.example.com\nderp2.example.com"
        }

        val outbound = buildSingBoxOutboundTailcatBean(bean)

        assertEquals("privkey:client", outbound.private_key)
        assertEquals("psk", outbound.pre_shared_key)
        assertNull(outbound.derp_map_url)
        assertNull(outbound.derp_region)
        assertEquals(
            listOf("derp1.example.com", "derp2.example.com"),
            outbound.derp_servers?.map { it.host },
        )
    }

    @Test
    fun `parseOutbound should read a Tailcat outbound`() {
        val json: JSONMap = mutableMapOf(
            "type" to "tailcat",
            "tag" to "tailcat-out",
            "server_public_key" to "nodekey:server",
            "server_disco_key" to "discokey:server",
            "derp_region" to 900L,
            "derp_servers" to listOf(
                "derp1.example.com",
                mapOf("host" to "derp2.example.com", "derp_port" to 8443L),
            ),
        )

        val bean = assertIs<TailcatBean>(parseOutbound(json))

        assertEquals("tailcat-out", bean.name)
        assertEquals("nodekey:server", bean.serverPublicKey)
        assertEquals("discokey:server", bean.serverDiscoKey)
        assertEquals(900, bean.derpRegion)
        assertEquals("derp1.example.com\nderp2.example.com", bean.derpServers)
    }

    @Test
    fun `TailcatBean should survive binary serialization`() {
        val source = TailcatBean().apply {
            name = "tailcat"
            privateKey = "private"
            serverPublicKey = "public"
            serverDiscoKey = "disco"
            preSharedKey = "psk"
            derpMapURL = "https://derp.example.com/map.json"
            derpRegion = 7
            derpServers = "derp.example.com"
        }

        val parsed = BeanConverters.deserialize(TailcatBean(), BeanConverters.serialize(source))

        assertEquals(BeanConverters.serialize(source).toList(), BeanConverters.serialize(parsed).toList())
        assertEquals(7, parsed.derpRegion)
    }
}
