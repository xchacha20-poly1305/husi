package fr.husi.fmt

import fr.husi.Key
import fr.husi.database.DataStore
import fr.husi.database.ProxyEntity
import fr.husi.database.ProxyGroup
import fr.husi.database.ProfileManager
import fr.husi.database.RuleEntity
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
    fun `buildConfig should keep server domain for chained outbound`() = runBlocking {
        DataStore.domainStrategyForServer = "auto"

        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val local = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "local",
            host = "127.0.0.1",
            port = 1080,
        )
        val remote = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "remote",
            host = "chain.example.com",
            port = 1080,
        )
        val chain = createChain(
            groupId = group.id,
            order = 3,
            name = "chain",
            proxies = listOf(local.id, remote.id),
        )

        val outbounds = parseOutbounds(buildConfig(chain))

        assertEquals("local", outbounds["remote"]?.get("detour")?.jsonPrimitive?.content)
        assertEquals(null, outbounds["remote"]?.get("domain_resolver"))
    }

    @Test
    fun `buildConfig should keep remote DNS domain through detour`() = runBlocking {
        DataStore.remoteDns = "tcp://dns.example.com"

        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )

        val dnsServers = parseDnsServers(buildConfig(proxy))

        assertEquals("main", dnsServers[TAG_DNS_REMOTE]?.get("detour")?.jsonPrimitive?.content)
        assertEquals(null, dnsServers[TAG_DNS_REMOTE]?.get("domain_resolver"))
    }

    @Test
    fun `buildConfig should resolve mDNS by local DNS when interface list is empty`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )

        val result = buildConfig(proxy)
        val dnsServers = parseDnsServers(result)
        val dnsRules = parseDnsRules(result)
        val mdnsRule = dnsRules.first {
            it["preferred_by"]?.jsonArray?.map { item -> item.jsonPrimitive.content } == listOf(TAG_DNS_MDNS)
        }

        assertEquals(null, dnsServers[TAG_DNS_MDNS])
        assertEquals(TAG_DNS_LOCAL, mdnsRule["server"]?.jsonPrimitive?.content)
    }

    @Test
    fun `buildConfig should add mDNS server for configured interfaces`() = runBlocking {
        DataStore.mDNS = "wlan0, eth0\nap0"

        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )

        val result = buildConfig(proxy)
        val dnsServers = parseDnsServers(result)
        val dnsRules = parseDnsRules(result)
        val mdnsServer = dnsServers[TAG_DNS_MDNS]
        assertNotNull(mdnsServer)
        val mdnsRule = dnsRules.first {
            it["preferred_by"]?.jsonArray?.map { item -> item.jsonPrimitive.content } == listOf(TAG_DNS_MDNS)
        }

        assertEquals(SingBoxOptions.DNS_TYPE_MDNS, mdnsServer["type"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("wlan0", "eth0", "ap0"),
            mdnsServer["interface"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(TAG_DNS_MDNS, mdnsRule["server"]?.jsonPrimitive?.content)
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

    @Test
    fun `buildConfig should migrate response-based direct DNS rules to evaluate then route`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )
        ProfileManager.createRule(
            RuleEntity(
                enabled = true,
                name = "dns-geoip-direct",
                ip = "set+dns:geoip-cn",
                outbound = RuleEntity.OUTBOUND_DIRECT,
            ),
        )

        val dnsRules = parseDnsRules(buildConfig(proxy))
        val evaluateIndex = dnsRules.indexOfFirst {
            it["action"]?.jsonPrimitive?.content == SingBoxOptions.ACTION_EVALUATE
        }

        assertTrue(evaluateIndex >= 0)
        assertEquals(TAG_DNS_REMOTE, dnsRules[evaluateIndex]["server"]?.jsonPrimitive?.content)

        val responseRule = dnsRules[evaluateIndex + 1]
        assertEquals(SingBoxOptions.ACTION_ROUTE, responseRule["action"]?.jsonPrimitive?.content)
        assertEquals("true", responseRule["match_response"]?.jsonPrimitive?.content)
        assertEquals(TAG_DNS_DIRECT, responseRule["server"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("geoip-cn"),
            responseRule["rule_set"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `buildConfig should migrate response-based proxy DNS rules to evaluate then respond`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )
        ProfileManager.createRule(
            RuleEntity(
                enabled = true,
                name = "dns-geoip-proxy",
                ip = "set+dns:geoip-cn",
                outbound = RuleEntity.OUTBOUND_PROXY,
            ),
        )

        val dnsRules = parseDnsRules(buildConfig(proxy))
        val evaluateIndex = dnsRules.indexOfFirst {
            it["action"]?.jsonPrimitive?.content == SingBoxOptions.ACTION_EVALUATE
        }

        assertTrue(evaluateIndex >= 0)
        assertEquals(TAG_DNS_REMOTE, dnsRules[evaluateIndex]["server"]?.jsonPrimitive?.content)

        val responseRule = dnsRules[evaluateIndex + 1]
        assertEquals(SingBoxOptions.ACTION_RESPOND, responseRule["action"]?.jsonPrimitive?.content)
        assertEquals("true", responseRule["match_response"]?.jsonPrimitive?.content)
        assertEquals(null, responseRule["server"])
        assertEquals(
            listOf("geoip-cn"),
            responseRule["rule_set"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `buildConfig should keep request-based DNS rules as direct route without evaluate`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )
        ProfileManager.createRule(
            RuleEntity(
                enabled = true,
                name = "dns-geosite-direct",
                domains = "set+dns:geosite-cn",
                outbound = RuleEntity.OUTBOUND_DIRECT,
            ),
        )

        val dnsRules = parseDnsRules(buildConfig(proxy))
        assertEquals(
            -1,
            dnsRules.indexOfFirst {
                it["action"]?.jsonPrimitive?.content == SingBoxOptions.ACTION_EVALUATE
            },
        )

        val routeRule = dnsRules.firstOrNull {
            it["server"]?.jsonPrimitive?.content == TAG_DNS_DIRECT &&
                it["rule_set"]?.jsonArray?.map { item -> item.jsonPrimitive.content } == listOf("geosite-cn")
        }
        assertNotNull(routeRule)
        assertEquals(null, routeRule["match_response"])
    }

    @Test
    fun `buildConfig should treat ip field dns rule set as response-based without geoip prefix`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )
        ProfileManager.createRule(
            RuleEntity(
                enabled = true,
                name = "dns-custom-ip-set",
                ip = "set+dns:custom-ip-set",
                outbound = RuleEntity.OUTBOUND_DIRECT,
            ),
        )

        val dnsRules = parseDnsRules(buildConfig(proxy))
        val evaluateIndex = dnsRules.indexOfFirst {
            it["action"]?.jsonPrimitive?.content == SingBoxOptions.ACTION_EVALUATE
        }

        assertTrue(evaluateIndex >= 0)
        assertEquals(TAG_DNS_REMOTE, dnsRules[evaluateIndex]["server"]?.jsonPrimitive?.content)

        val responseRule = dnsRules[evaluateIndex + 1]
        assertEquals("true", responseRule["match_response"]?.jsonPrimitive?.content)
        assertEquals(TAG_DNS_DIRECT, responseRule["server"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("custom-ip-set"),
            responseRule["rule_set"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `buildConfig should keep domain field dns rule set as request-based without geosite prefix`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )
        ProfileManager.createRule(
            RuleEntity(
                enabled = true,
                name = "dns-custom-domain-set",
                domains = "set+dns:custom-domain-set",
                outbound = RuleEntity.OUTBOUND_DIRECT,
            ),
        )

        val dnsRules = parseDnsRules(buildConfig(proxy))
        assertEquals(
            -1,
            dnsRules.indexOfFirst {
                it["action"]?.jsonPrimitive?.content == SingBoxOptions.ACTION_EVALUATE
            },
        )

        val routeRule = dnsRules.firstOrNull {
            it["server"]?.jsonPrimitive?.content == TAG_DNS_DIRECT &&
                it["rule_set"]?.jsonArray?.map { item -> item.jsonPrimitive.content } == listOf("custom-domain-set")
        }
        assertNotNull(routeRule)
        assertEquals(null, routeRule["match_response"])
    }

    @Test
    fun `buildConfig should preserve request dns rule set when ip dns rule set needs response matching`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )
        ProfileManager.createRule(
            RuleEntity(
                enabled = true,
                name = "dns-mixed-set",
                domains = "set+dns:custom-domain-set",
                ip = "set+dns:custom-ip-set",
                outbound = RuleEntity.OUTBOUND_DIRECT,
            ),
        )

        val dnsRules = parseDnsRules(buildConfig(proxy))
        val evaluateIndex = dnsRules.indexOfFirst {
            it["action"]?.jsonPrimitive?.content == SingBoxOptions.ACTION_EVALUATE
        }

        assertTrue(evaluateIndex >= 0)

        val evaluateRule = dnsRules[evaluateIndex]
        assertEquals(
            listOf("custom-domain-set"),
            evaluateRule["rule_set"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(null, evaluateRule["match_response"])

        val responseRule = dnsRules[evaluateIndex + 1]
        assertEquals("true", responseRule["match_response"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("custom-domain-set", "custom-ip-set"),
            responseRule["rule_set"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    private fun parseOutbounds(result: ConfigBuildResult) =
        Json.parseToJsonElement(result.config).jsonObject["outbounds"]!!
            .jsonArray
            .associateBy { it.jsonObject["tag"]!!.jsonPrimitive.content }
            .mapValues { it.value.jsonObject }

    private fun parseDnsRules(result: ConfigBuildResult) =
        Json.parseToJsonElement(result.config).jsonObject["dns"]!!
            .jsonObject["rules"]!!
            .jsonArray
            .map { it.jsonObject }

    private fun parseDnsServers(result: ConfigBuildResult) =
        Json.parseToJsonElement(result.config).jsonObject["dns"]!!
            .jsonObject["servers"]!!
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
