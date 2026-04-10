package fr.husi.fmt

import fr.husi.Key
import fr.husi.database.DataStore
import fr.husi.database.ProxyEntity
import fr.husi.database.ProxyGroup
import fr.husi.database.SagerDatabase
import fr.husi.fmt.internal.ChainBean
import fr.husi.fmt.internal.ProxySetBean
import fr.husi.fmt.socks.SOCKSBean
import fr.husi.ktx.applyDefaultValues
import fr.husi.platform.PlatformInfo
import fr.husi.test.HusiKoinTest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigBuilderTest : HusiKoinTest() {

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
        SagerDatabase.proxyDao.reset()
        SagerDatabase.groupDao.reset()
        SagerDatabase.rulesDao.reset()
        SagerDatabase.assetDao.reset()
        SagerDatabase.pluginDao.reset()
    }

    @Test
    fun `buildConfig should wire front and landing around proxy set`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val memberA = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "member-a",
            host = "1.1.1.1",
            port = 1081,
        )
        val memberB = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "member-b",
            host = "2.2.2.2",
            port = 1082,
        )
        val front = createSocksProxy(
            groupId = group.id,
            order = 3,
            name = "front",
            host = "3.3.3.3",
            port = 1083,
        )
        val landing = createSocksProxy(
            groupId = group.id,
            order = 4,
            name = "landing",
            host = "4.4.4.4",
            port = 1084,
        )

        val proxySet = ProxyEntity(groupId = group.id, userOrder = 5).putBean(
            ProxySetBean().apply {
                name = "set-main"
                management = ProxySetBean.MANAGEMENT_SELECTOR
                type = ProxySetBean.TYPE_LIST
                proxies = listOf(memberA.id, memberB.id)
            }.applyDefaultValues(),
        )
        proxySet.id = SagerDatabase.proxyDao.addProxy(proxySet)

        group.frontProxy = front.id
        group.landingProxy = landing.id
        SagerDatabase.groupDao.updateGroup(group)

        val result = buildConfig(proxySet, forTest = true)

        assertEquals("landing", result.mainTag)
        assertEquals(proxySet.id, result.tagToID["set-main"])
        assertEquals(landing.id, result.tagToID["landing"])

        val trafficGroup = result.trafficMap["set-main"]
        assertNotNull(trafficGroup)
        assertEquals(landing.id, trafficGroup.last().id)

        val root = Json.parseToJsonElement(result.config).jsonObject
        val outbounds = root["outbounds"]!!.jsonArray.map { it.jsonObject }
        fun outboundByTag(tag: String) = outbounds.first { it["tag"]?.jsonPrimitive?.content == tag }

        val selector = outboundByTag("set-main")
        val selectorChildren = selector["outbounds"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(setOf("member-a", "member-b"), selectorChildren)
        assertTrue("front" !in selectorChildren)
        assertTrue("landing" !in selectorChildren)

        assertEquals("front", outboundByTag("member-a")["detour"]?.jsonPrimitive?.content)
        assertEquals("front", outboundByTag("member-b")["detour"]?.jsonPrimitive?.content)
        assertEquals("set-main", outboundByTag("landing")["detour"]?.jsonPrimitive?.content)
    }

    @Test
    fun `buildConfig should expand group chain front and landing for regular profile`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val main = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1081,
        )
        val landingA = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "landing-a",
            host = "2.2.2.2",
            port = 1082,
        )
        val landingB = createSocksProxy(
            groupId = group.id,
            order = 3,
            name = "landing-b",
            host = "3.3.3.3",
            port = 1083,
        )
        val frontA = createSocksProxy(
            groupId = group.id,
            order = 4,
            name = "front-a",
            host = "4.4.4.4",
            port = 1084,
        )
        val frontB = createSocksProxy(
            groupId = group.id,
            order = 5,
            name = "front-b",
            host = "5.5.5.5",
            port = 1085,
        )
        val landingChain = createChain(
            groupId = group.id,
            order = 6,
            name = "landing-chain",
            proxies = listOf(landingA.id, landingB.id),
        )
        val frontChain = createChain(
            groupId = group.id,
            order = 7,
            name = "front-chain",
            proxies = listOf(frontA.id, frontB.id),
        )

        group.frontProxy = frontChain.id
        group.landingProxy = landingChain.id
        SagerDatabase.groupDao.updateGroup(group)

        val result = buildConfig(main, forTest = true)

        assertEquals("landing-b", result.mainTag)

        val outbounds = parseOutbounds(result)
        assertEquals("landing-a", outbounds["landing-b"]?.get("detour")?.jsonPrimitive?.content)
        assertEquals("main", outbounds["landing-a"]?.get("detour")?.jsonPrimitive?.content)
        assertEquals("front-b", outbounds["main"]?.get("detour")?.jsonPrimitive?.content)
        assertEquals("front-a", outbounds["front-b"]?.get("detour")?.jsonPrimitive?.content)
    }

    @Test
    fun `buildConfig should expand group chain front and landing around proxy set`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val memberA = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "member-a",
            host = "1.1.1.1",
            port = 1081,
        )
        val memberB = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "member-b",
            host = "2.2.2.2",
            port = 1082,
        )
        val landingA = createSocksProxy(
            groupId = group.id,
            order = 3,
            name = "landing-a",
            host = "3.3.3.3",
            port = 1083,
        )
        val landingB = createSocksProxy(
            groupId = group.id,
            order = 4,
            name = "landing-b",
            host = "4.4.4.4",
            port = 1084,
        )
        val frontA = createSocksProxy(
            groupId = group.id,
            order = 5,
            name = "front-a",
            host = "5.5.5.5",
            port = 1085,
        )
        val frontB = createSocksProxy(
            groupId = group.id,
            order = 6,
            name = "front-b",
            host = "6.6.6.6",
            port = 1086,
        )
        val landingChain = createChain(
            groupId = group.id,
            order = 7,
            name = "landing-chain",
            proxies = listOf(landingA.id, landingB.id),
        )
        val frontChain = createChain(
            groupId = group.id,
            order = 8,
            name = "front-chain",
            proxies = listOf(frontA.id, frontB.id),
        )
        val proxySet = ProxyEntity(groupId = group.id, userOrder = 9).putBean(
            ProxySetBean().apply {
                name = "set-main"
                management = ProxySetBean.MANAGEMENT_SELECTOR
                type = ProxySetBean.TYPE_LIST
                proxies = listOf(memberA.id, memberB.id)
            }.applyDefaultValues(),
        )
        proxySet.id = SagerDatabase.proxyDao.addProxy(proxySet)

        group.frontProxy = frontChain.id
        group.landingProxy = landingChain.id
        SagerDatabase.groupDao.updateGroup(group)

        val result = buildConfig(proxySet, forTest = true)

        assertEquals("landing-b", result.mainTag)

        val trafficGroup = result.trafficMap["set-main"]
        assertNotNull(trafficGroup)
        assertEquals(landingB.id, trafficGroup.last().id)

        val outbounds = parseOutbounds(result)
        val selectorChildren =
            outbounds["set-main"]?.get("outbounds")?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
        assertEquals(setOf("member-a", "member-b"), selectorChildren)

        assertEquals("landing-a", outbounds["landing-b"]?.get("detour")?.jsonPrimitive?.content)
        assertEquals("set-main", outbounds["landing-a"]?.get("detour")?.jsonPrimitive?.content)
        assertEquals("front-b", outbounds["member-a"]?.get("detour")?.jsonPrimitive?.content)
        assertEquals("front-b", outbounds["member-b"]?.get("detour")?.jsonPrimitive?.content)
        assertEquals("front-a", outbounds["front-b"]?.get("detour")?.jsonPrimitive?.content)
    }

    @Test
    fun `buildConfig should follow tun auto redirect setting on desktop`() = runBlocking {
        DataStore.serviceMode = Key.MODE_VPN

        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )

        DataStore.tunAutoRedirect = true
        val enabledTunInbound = parseTunInbound(buildConfig(proxy))
        if (PlatformInfo.isLinux) {
            assertEquals("true", enabledTunInbound["auto_redirect"]?.jsonPrimitive?.content)
        } else {
            assertEquals(null, enabledTunInbound["auto_redirect"])
        }

        DataStore.tunAutoRedirect = false
        val disabledTunInbound = parseTunInbound(buildConfig(proxy))
        assertEquals(null, disabledTunInbound["auto_redirect"])
    }

    @Test
    fun `buildConfig should forward tun interface name on desktop`() = runBlocking {
        DataStore.serviceMode = Key.MODE_VPN

        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )

        DataStore.tunInterfaceName = "tun0"
        val configuredTunInbound = parseTunInbound(buildConfig(proxy))
        assertEquals("tun0", configuredTunInbound["interface_name"]?.jsonPrimitive?.content)

        DataStore.tunInterfaceName = ""
        val defaultTunInbound = parseTunInbound(buildConfig(proxy))
        assertEquals(null, defaultTunInbound["interface_name"])
    }

    private fun parseOutbounds(result: ConfigBuildResult) =
        Json.parseToJsonElement(result.config).jsonObject["outbounds"]!!
            .jsonArray
            .associateBy { it.jsonObject["tag"]!!.jsonPrimitive.content }
            .mapValues { it.value.jsonObject }

    private fun parseTunInbound(result: ConfigBuildResult) =
        Json.parseToJsonElement(result.config).jsonObject["inbounds"]!!
            .jsonArray
            .first { it.jsonObject["tag"]!!.jsonPrimitive.content == TAG_TUN }
            .jsonObject

    private suspend fun createSocksProxy(
        groupId: Long,
        order: Long,
        name: String,
        host: String,
        port: Int,
    ): ProxyEntity {
        val proxy = ProxyEntity(groupId = groupId, userOrder = order).putBean(
            SOCKSBean().apply {
                this.name = name
                serverAddress = host
                serverPort = port
            }.applyDefaultValues(),
        )
        proxy.id = SagerDatabase.proxyDao.addProxy(proxy)
        return proxy
    }

    private suspend fun createChain(
        groupId: Long,
        order: Long,
        name: String,
        proxies: List<Long>,
    ): ProxyEntity {
        val chain = ProxyEntity(groupId = groupId, userOrder = order).putBean(
            ChainBean().apply {
                this.name = name
                this.proxies = proxies
            }.applyDefaultValues(),
        )
        chain.id = SagerDatabase.proxyDao.addProxy(chain)
        return chain
    }
}
