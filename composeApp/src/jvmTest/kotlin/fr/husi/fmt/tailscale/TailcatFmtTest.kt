package fr.husi.fmt.tailscale

import fr.husi.fmt.BeanConverters
import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.parseOutbound
import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TailcatFmtTest {

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
