package fr.husi.fmt

import fr.husi.Key
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyEntity
import fr.husi.database.ProxyGroup
import fr.husi.database.RuleEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.internal.ChainBean
import fr.husi.fmt.internal.ProxySetBean
import fr.husi.fmt.mieru.MieruBean
import fr.husi.fmt.openconnect.OpenConnectBean
import fr.husi.fmt.openvpn.OpenVPNBean
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
import kotlin.test.assertFailsWith
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
        assertEquals("https://sing-box.sagernet.org/schema.json", root[$$"$schema"]?.jsonPrimitive?.content)
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
    fun `buildConfig should reject a wrapper that re-expands a main chain member`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val shared = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "shared",
            host = "1.1.1.1",
            port = 1080,
        )
        val main = createChain(
            groupId = group.id,
            order = 2,
            name = "main",
            proxies = listOf(shared.id),
        )
        val front = createChain(
            groupId = group.id,
            order = 3,
            name = "front",
            proxies = listOf(shared.id),
        )
        val landing = createChain(
            groupId = group.id,
            order = 4,
            name = "landing",
            proxies = listOf(shared.id),
        )

        group.frontProxy = front.id
        SagerDatabase.groupDao.updateGroup(group)
        val frontError = assertFailsWith<IllegalStateException> {
            buildConfig(main, forTest = true)
        }
        assertEquals(
            "Duplicate proxy reference: ${shared.id} @ ${front.id}",
            frontError.message,
        )

        group.frontProxy = -1L
        group.landingProxy = landing.id
        SagerDatabase.groupDao.updateGroup(group)
        val landingError = assertFailsWith<IllegalStateException> {
            buildConfig(main, forTest = true)
        }
        assertEquals(
            "Duplicate proxy reference: ${shared.id} @ ${landing.id}",
            landingError.message,
        )
    }

    @Test
    fun `buildConfig for URL test applies server domain strategy`() = runBlocking {
        DataStore.networkStrategy = SingBoxOptions.STRATEGY_PREFER_IPV6

        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "server-domain",
            host = "server.example.com",
            port = 1080,
        )

        val outbounds = parseOutbounds(buildConfig(proxy, forTest = true))
        val outbound = assertNotNull(outbounds["server-domain"])
        val domainResolver = assertNotNull(outbound["domain_resolver"]).jsonObject

        assertEquals(TAG_DNS_LOCAL, domainResolver["server"]?.jsonPrimitive?.content)
        assertEquals(
            SingBoxOptions.STRATEGY_PREFER_IPV6,
            domainResolver["strategy"]?.jsonPrimitive?.content,
        )
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
    fun `buildConfig should add endpoint DNS server and preferred rule for OpenConnect`() =
        runBlocking {
            val group = ProxyGroup(name = "group").applyDefaultValues()
            group.id = SagerDatabase.groupDao.createGroup(group)
            val proxy = ProxyEntity(groupId = group.id, userOrder = 1).putBean(
                OpenConnectBean().apply {
                    name = "openconnect"
                    server = "https://vpn.example.com"
                }.applyDefaultValues(),
            )
            proxy.id = SagerDatabase.proxyDao.addProxy(proxy)

            assertEndpointDNS(
                buildConfig(proxy),
                "openconnect",
                SingBoxOptions.DNS_TYPE_OPENCONNECT,
            )
        }

    @Test
    fun `buildConfig should add endpoint DNS server and preferred rule for OpenVPN`() =
        runBlocking {
            val group = ProxyGroup(name = "group").applyDefaultValues()
            group.id = SagerDatabase.groupDao.createGroup(group)
            val proxy = ProxyEntity(groupId = group.id, userOrder = 1).putBean(
                OpenVPNBean().apply {
                    name = "openvpn"
                    serverAddress = "vpn.example.com"
                    serverPort = 1194
                }.applyDefaultValues(),
            )
            proxy.id = SagerDatabase.proxyDao.addProxy(proxy)

            assertEndpointDNS(buildConfig(proxy), "openvpn", SingBoxOptions.DNS_TYPE_OPENVPN)
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
    fun `buildConfig should preserve proxy set order through multiple nested chains`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val terminal = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "terminal",
            host = "1.1.1.1",
            port = 1081,
        )
        val member = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "member",
            host = "2.2.2.2",
            port = 1082,
        )
        val proxySet = createProxySet(
            groupId = group.id,
            order = 3,
            name = "set",
            proxies = listOf(member.id),
        )
        val innerChain = createChain(
            groupId = group.id,
            order = 4,
            name = "inner-chain",
            proxies = listOf(terminal.id, proxySet.id),
        )
        val entry = createSocksProxy(
            groupId = group.id,
            order = 5,
            name = "entry",
            host = "3.3.3.3",
            port = 1083,
        )
        val middleChain = createChain(
            groupId = group.id,
            order = 6,
            name = "middle-chain",
            proxies = listOf(innerChain.id, entry.id),
        )
        val outerChain = createChain(
            groupId = group.id,
            order = 7,
            name = "outer-chain",
            proxies = listOf(middleChain.id),
        )

        val result = buildConfig(outerChain, forTest = true)
        val outbounds = parseOutboundList(result)
        val tags = outbounds.map { it["tag"]!!.jsonPrimitive.content }

        assertEquals(tags.size, tags.toSet().size)
        assertEquals(1, result.tagToID.values.count { it == terminal.id })
        assertEquals(1, result.tagToID.values.count { it == member.id })
        assertEquals(1, result.tagToID.values.count { it == proxySet.id })
        assertEquals(1, result.tagToID.values.count { it == entry.id })
        assertEquals("entry", result.mainTag)
        assertEquals(
            "set",
            outbounds.single { it["tag"]?.jsonPrimitive?.content == "entry" }["detour"]
                ?.jsonPrimitive?.content,
        )
        assertEquals(
            "terminal",
            outbounds.single { it["tag"]?.jsonPrimitive?.content == "member" }["detour"]
                ?.jsonPrimitive?.content,
        )
        assertEquals(
            null,
            outbounds.single { it["tag"]?.jsonPrimitive?.content == "terminal" }["detour"],
        )
    }

    @Test
    fun `buildConfig should preserve list proxy set member order`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val first = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "first",
            host = "1.1.1.1",
            port = 1081,
        )
        val second = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "second",
            host = "2.2.2.2",
            port = 1082,
        )
        val proxySet = createProxySet(
            groupId = group.id,
            order = 3,
            name = "set",
            proxies = listOf(second.id, first.id),
        )
        val chain = createChain(
            groupId = group.id,
            order = 4,
            name = "chain",
            proxies = listOf(proxySet.id),
        )

        val result = buildConfig(chain, forTest = true)
        val outbounds = parseOutbounds(result)

        assertEquals(
            listOf("second", "first"),
            outbounds["set"]!!["outbounds"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(1, result.tagToID.values.count { it == first.id })
        assertEquals(1, result.tagToID.values.count { it == second.id })
    }

    @Test
    fun `buildConfig should keep shared members independent in landing and front proxy sets`() =
        runBlocking {
            val group = ProxyGroup(name = "group").applyDefaultValues()
            group.id = SagerDatabase.groupDao.createGroup(group)

            val shared = createSocksProxy(
                groupId = group.id,
                order = 1,
                name = "shared",
                host = "1.1.1.1",
                port = 1081,
            )
            val landingOnly = createSocksProxy(
                groupId = group.id,
                order = 2,
                name = "landing-only",
                host = "2.2.2.2",
                port = 1082,
            )
            val frontOnly = createSocksProxy(
                groupId = group.id,
                order = 3,
                name = "front-only",
                host = "3.3.3.3",
                port = 1083,
            )
            val landingSet = createProxySet(
                groupId = group.id,
                order = 4,
                name = "landing-set",
                proxies = listOf(shared.id, landingOnly.id),
            )
            val frontSet = createProxySet(
                groupId = group.id,
                order = 5,
                name = "front-set",
                proxies = listOf(shared.id, frontOnly.id),
            )
            val main = createSocksProxy(
                groupId = group.id,
                order = 6,
                name = "main",
                host = "4.4.4.4",
                port = 1084,
            )

            group.landingProxy = landingSet.id
            group.frontProxy = frontSet.id
            SagerDatabase.groupDao.updateGroup(group)

            val result = buildConfig(main, forTest = true)
            val outbounds = parseOutboundList(result)
            val landingSelector = outbounds.single {
                it["tag"]?.jsonPrimitive?.content == "landing-set"
            }
            val frontSelector = outbounds.single {
                it["tag"]?.jsonPrimitive?.content == "front-set"
            }
            val landingSharedTag = landingSelector["outbounds"]!!.jsonArray
                .map { it.jsonPrimitive.content }
                .single { result.tagToID[it] == shared.id }
            val frontSharedTag = frontSelector["outbounds"]!!.jsonArray
                .map { it.jsonPrimitive.content }
                .single { result.tagToID[it] == shared.id }

            assertEquals("landing-set", result.mainTag)
            assertEquals(2, result.tagToID.values.count { it == shared.id })
            assertEquals(1, result.tagToID.values.count { it == landingOnly.id })
            assertEquals(1, result.tagToID.values.count { it == frontOnly.id })
            assertEquals(1, result.tagToID.values.count { it == landingSet.id })
            assertEquals(1, result.tagToID.values.count { it == frontSet.id })
            assertEquals(1, result.tagToID.values.count { it == main.id })
            assertTrue(landingSharedTag != frontSharedTag)
            assertEquals(shared.id, result.tagToID[landingSharedTag])
            assertEquals(shared.id, result.tagToID[frontSharedTag])
            assertEquals(
                "main",
                outbounds.single {
                    it["tag"]?.jsonPrimitive?.content == landingSharedTag
                }["detour"]?.jsonPrimitive?.content,
            )
            assertEquals(
                "front-set",
                outbounds.single { it["tag"]?.jsonPrimitive?.content == "main" }["detour"]
                    ?.jsonPrimitive?.content,
            )
            assertEquals(
                null,
                outbounds.single {
                    it["tag"]?.jsonPrimitive?.content == frontSharedTag
                }["detour"],
            )
        }

    @Test
    fun `buildConfig should expand nested front and landing chains containing proxy sets`() =
        runBlocking {
            val rootGroup = ProxyGroup(name = "root").applyDefaultValues()
            rootGroup.id = SagerDatabase.groupDao.createGroup(rootGroup)
            val memberGroup = ProxyGroup(name = "landing-members").applyDefaultValues()
            memberGroup.id = SagerDatabase.groupDao.createGroup(memberGroup)

            val landingMember = createSocksProxy(
                groupId = memberGroup.id,
                order = 1,
                name = "landing-member",
                host = "1.1.1.1",
                port = 1081,
            )
            val landingSet = createProxySet(
                groupId = rootGroup.id,
                order = 1,
                name = "landing-set",
                proxies = emptyList(),
                type = ProxySetBean.TYPE_GROUP,
                collectGroupId = memberGroup.id,
            )
            val landingTail = createSocksProxy(
                groupId = rootGroup.id,
                order = 2,
                name = "landing-tail",
                host = "2.2.2.2",
                port = 1082,
            )
            val landingInner = createChain(
                groupId = rootGroup.id,
                order = 3,
                name = "landing-inner",
                proxies = listOf(landingTail.id, landingSet.id),
            )
            val landingOuter = createChain(
                groupId = rootGroup.id,
                order = 4,
                name = "landing-outer",
                proxies = listOf(landingInner.id),
            )

            val frontMember = createSocksProxy(
                groupId = rootGroup.id,
                order = 5,
                name = "front-member",
                host = "3.3.3.3",
                port = 1083,
            )
            val frontSet = createProxySet(
                groupId = rootGroup.id,
                order = 6,
                name = "front-set",
                proxies = listOf(frontMember.id),
            )
            val frontTail = createSocksProxy(
                groupId = rootGroup.id,
                order = 7,
                name = "front-tail",
                host = "4.4.4.4",
                port = 1084,
            )
            val frontInner = createChain(
                groupId = rootGroup.id,
                order = 8,
                name = "front-inner",
                proxies = listOf(frontTail.id, frontSet.id),
            )
            val frontOuter = createChain(
                groupId = rootGroup.id,
                order = 9,
                name = "front-outer",
                proxies = listOf(frontInner.id),
            )
            val mainTail = createSocksProxy(
                groupId = rootGroup.id,
                order = 10,
                name = "main-tail",
                host = "5.5.5.5",
                port = 1085,
            )
            val mainEntry = createSocksProxy(
                groupId = rootGroup.id,
                order = 11,
                name = "main-entry",
                host = "6.6.6.6",
                port = 1086,
            )
            val mainInner = createChain(
                groupId = rootGroup.id,
                order = 12,
                name = "main-inner",
                proxies = listOf(mainTail.id, mainEntry.id),
            )
            val mainOuter = createChain(
                groupId = rootGroup.id,
                order = 13,
                name = "main-outer",
                proxies = listOf(mainInner.id),
            )

            rootGroup.landingProxy = landingOuter.id
            rootGroup.frontProxy = frontOuter.id
            SagerDatabase.groupDao.updateGroup(rootGroup)

            val result = buildConfig(mainOuter, forTest = true)
            val outbounds = parseOutbounds(result)

            assertEquals("landing-set", result.mainTag)
            assertEquals(1, result.tagToID.values.count { it == landingMember.id })
            assertEquals(1, result.tagToID.values.count { it == landingSet.id })
            assertEquals(1, result.tagToID.values.count { it == landingTail.id })
            assertEquals(1, result.tagToID.values.count { it == frontMember.id })
            assertEquals(1, result.tagToID.values.count { it == frontSet.id })
            assertEquals(1, result.tagToID.values.count { it == frontTail.id })
            assertEquals(1, result.tagToID.values.count { it == mainEntry.id })
            assertEquals(1, result.tagToID.values.count { it == mainTail.id })
            assertEquals(
                listOf("landing-member"),
                outbounds["landing-set"]!!["outbounds"]!!.jsonArray
                    .map { it.jsonPrimitive.content },
            )
            assertEquals(
                "landing-tail",
                outbounds["landing-member"]?.get("detour")?.jsonPrimitive?.content,
            )
            assertEquals(
                "main-entry",
                outbounds["landing-tail"]?.get("detour")?.jsonPrimitive?.content,
            )
            assertEquals(
                "main-tail",
                outbounds["main-entry"]?.get("detour")?.jsonPrimitive?.content,
            )
            assertEquals(
                "front-set",
                outbounds["main-tail"]?.get("detour")?.jsonPrimitive?.content,
            )
            assertEquals(
                "front-tail",
                outbounds["front-member"]?.get("detour")?.jsonPrimitive?.content,
            )
            assertEquals(null, outbounds["front-tail"]?.get("detour"))
        }

    @Test
    fun `buildConfig should keep rule targets on their root chain contexts`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val main = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1081,
        )
        val target = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "target",
            host = "2.2.2.2",
            port = 1082,
        )
        val tail = createSocksProxy(
            groupId = group.id,
            order = 3,
            name = "tail",
            host = "3.3.3.3",
            port = 1083,
        )
        val nestedChain = createChain(
            groupId = group.id,
            order = 4,
            name = "nested-chain",
            proxies = listOf(tail.id, target.id),
        )
        ProfileManager.createRule(
            RuleEntity(
                enabled = true,
                name = "target-root",
                domains = "full:target.example",
                outbound = target.id,
            ),
        )
        ProfileManager.createRule(
            RuleEntity(
                enabled = true,
                name = "nested-root",
                domains = "full:nested.example",
                outbound = nestedChain.id,
            ),
        )

        val result = buildConfig(main)
        val routeRules = parseRouteRules(result)
        fun ruleFor(domain: String) = routeRules.first {
            it["domain"]?.jsonArray?.map { item -> item.jsonPrimitive.content } == listOf(domain)
        }

        assertEquals(2, result.tagToID.values.count { it == target.id })
        assertEquals("target", ruleFor("target.example")["outbound"]?.jsonPrimitive?.content)
        assertEquals(
            "target-0",
            ruleFor("nested.example")["outbound"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `buildConfig should expand proxy set referenced by a chain without duplicate tags`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val member = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "member",
            host = "1.1.1.1",
            port = 1080,
        )
        val proxySet = createProxySet(
            groupId = group.id,
            order = 2,
            name = "set",
            proxies = listOf(member.id),
        )
        val chain = createChain(
            groupId = group.id,
            order = 3,
            name = "chain",
            proxies = listOf(proxySet.id),
        )

        val result = buildConfig(chain, forTest = true)
        val outbounds = parseOutboundList(result)
        val tags = outbounds.map { it["tag"]!!.jsonPrimitive.content }

        assertEquals(tags.size, tags.toSet().size)
        assertEquals(1, result.tagToID.values.count { it == member.id })
        assertEquals(1, result.tagToID.values.count { it == proxySet.id })
        assertEquals(1, tags.count { it == "member" })
        assertEquals(1, tags.count { it == "set" })
        assertEquals(
            listOf("member"),
            outbounds.single { it["tag"]?.jsonPrimitive?.content == "set" }["outbounds"]!!
                .jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `buildConfig should expand group proxy set referenced by a chain`() = runBlocking {
        val rootGroup = ProxyGroup(name = "root").applyDefaultValues()
        rootGroup.id = SagerDatabase.groupDao.createGroup(rootGroup)
        val memberGroup = ProxyGroup(name = "members").applyDefaultValues()
        memberGroup.id = SagerDatabase.groupDao.createGroup(memberGroup)

        val memberA = createSocksProxy(
            groupId = memberGroup.id,
            order = 1,
            name = "group-member-a",
            host = "1.1.1.1",
            port = 1080,
        )
        val memberB = createSocksProxy(
            groupId = memberGroup.id,
            order = 2,
            name = "group-member-b",
            host = "2.2.2.2",
            port = 1081,
        )
        val proxySet = createProxySet(
            groupId = rootGroup.id,
            order = 1,
            name = "group-set",
            proxies = emptyList(),
            type = ProxySetBean.TYPE_GROUP,
            collectGroupId = memberGroup.id,
        )
        val chain = createChain(
            groupId = rootGroup.id,
            order = 2,
            name = "group-set-chain",
            proxies = listOf(proxySet.id),
        )

        val result = buildConfig(chain, forTest = true)
        val outbounds = parseOutboundList(result)
        val tags = outbounds.map { it["tag"]!!.jsonPrimitive.content }
        assertEquals(tags.size, tags.toSet().size)
        assertEquals(1, result.tagToID.values.count { it == memberA.id })
        assertEquals(1, result.tagToID.values.count { it == memberB.id })
        assertEquals(1, result.tagToID.values.count { it == proxySet.id })
        assertEquals(1, tags.count { it == "group-set" })
        assertEquals(1, tags.count { it == "group-member-a" })
        assertEquals(1, tags.count { it == "group-member-b" })
        assertEquals(
            setOf("group-member-a", "group-member-b"),
            outbounds.single { it["tag"]?.jsonPrimitive?.content == "group-set" }["outbounds"]!!
                .jsonArray.map { it.jsonPrimitive.content }.toSet(),
        )
    }

    @Test
    fun `buildConfig should use chain root as proxy set member`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val entry = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "entry",
            host = "1.1.1.1",
            port = 1081,
        )
        val exit = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "exit",
            host = "2.2.2.2",
            port = 1082,
        )
        val memberChain = createChain(
            groupId = group.id,
            order = 3,
            name = "member-chain",
            proxies = listOf(exit.id, entry.id),
        )
        val proxySet = createProxySet(
            groupId = group.id,
            order = 4,
            name = "set",
            proxies = listOf(memberChain.id),
        )

        val result = buildConfig(proxySet, forTest = true)
        val outbounds = parseOutbounds(result)

        assertEquals("set", result.mainTag)
        assertEquals(
            listOf("entry"),
            outbounds["set"]!!["outbounds"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("exit", outbounds["entry"]?.get("detour")?.jsonPrimitive?.content)
        assertEquals(null, outbounds["exit"]?.get("detour"))
    }

    @Test
    fun `buildConfig should keep nested proxy set membership hierarchical`() = runBlocking {
        val rootGroup = ProxyGroup(name = "root").applyDefaultValues()
        rootGroup.id = SagerDatabase.groupDao.createGroup(rootGroup)
        val memberGroup = ProxyGroup(name = "members").applyDefaultValues()
        memberGroup.id = SagerDatabase.groupDao.createGroup(memberGroup)
        val leafGroup = ProxyGroup(name = "leaves").applyDefaultValues()
        leafGroup.id = SagerDatabase.groupDao.createGroup(leafGroup)
        val leaf = createSocksProxy(
            groupId = leafGroup.id,
            order = 1,
            name = "leaf",
            host = "1.1.1.1",
            port = 1080,
        )
        val innerSet = createProxySet(
            groupId = memberGroup.id,
            order = 1,
            name = "inner-set",
            proxies = listOf(leaf.id),
        )
        val outerSet = createProxySet(
            groupId = rootGroup.id,
            order = 1,
            name = "outer-set",
            proxies = emptyList(),
            type = ProxySetBean.TYPE_GROUP,
            collectGroupId = memberGroup.id,
        )

        val result = buildConfig(outerSet, forTest = true)
        val outboundList = parseOutboundList(result)
        val tags = outboundList.map { it["tag"]!!.jsonPrimitive.content }
        val outbounds = outboundList.associateBy { it["tag"]!!.jsonPrimitive.content }

        assertEquals("outer-set", result.mainTag)
        assertEquals(tags.size, tags.toSet().size)
        assertEquals(1, result.tagToID.values.count { it == outerSet.id })
        assertEquals(1, result.tagToID.values.count { it == innerSet.id })
        assertEquals(1, result.tagToID.values.count { it == leaf.id })
        assertEquals(
            listOf("inner-set"),
            outbounds["outer-set"]!!["outbounds"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("leaf"),
            outbounds["inner-set"]!!["outbounds"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(outbounds.keys.containsAll(listOf("outer-set", "inner-set", "leaf")))
    }

    @Test
    fun `buildConfig should connect mixed nested proxy graphs at branch exits`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val innerEntry = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "inner-entry",
            host = "1.1.1.1",
            port = 1081,
        )
        val innerExit = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "inner-exit",
            host = "2.2.2.2",
            port = 1082,
        )
        val innerChain = createChain(
            groupId = group.id,
            order = 3,
            name = "inner-chain",
            proxies = listOf(innerExit.id, innerEntry.id),
        )
        val innerSet = createProxySet(
            groupId = group.id,
            order = 4,
            name = "inner-set",
            proxies = listOf(innerChain.id),
        )
        val outerExit = createSocksProxy(
            groupId = group.id,
            order = 5,
            name = "outer-exit",
            host = "3.3.3.3",
            port = 1083,
        )
        val outerChain = createChain(
            groupId = group.id,
            order = 6,
            name = "outer-chain",
            proxies = listOf(outerExit.id, innerSet.id),
        )
        val rootSet = createProxySet(
            groupId = group.id,
            order = 7,
            name = "root-set",
            proxies = listOf(outerChain.id),
        )

        val result = buildConfig(rootSet, forTest = true)
        val outboundList = parseOutboundList(result)
        val tags = outboundList.map { it["tag"]!!.jsonPrimitive.content }
        val outbounds = outboundList.associateBy { it["tag"]!!.jsonPrimitive.content }

        assertEquals("root-set", result.mainTag)
        assertEquals(tags.size, tags.toSet().size)
        assertEquals(1, result.tagToID.values.count { it == rootSet.id })
        assertEquals(1, result.tagToID.values.count { it == innerSet.id })
        assertEquals(1, result.tagToID.values.count { it == innerEntry.id })
        assertEquals(
            listOf("inner-set"),
            outbounds["root-set"]!!["outbounds"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("inner-entry"),
            outbounds["inner-set"]!!["outbounds"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            "inner-exit",
            outbounds["inner-entry"]?.get("detour")?.jsonPrimitive?.content,
        )
        assertEquals(
            "outer-exit",
            outbounds["inner-exit"]?.get("detour")?.jsonPrimitive?.content,
        )
        assertEquals(null, outbounds["outer-exit"]?.get("detour"))
    }

    @Test
    fun `buildConfig should report direct and mixed proxy reference cycles`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val selfSet = createProxySet(
            groupId = group.id,
            order = 1,
            name = "self-set",
            proxies = emptyList(),
        )
        selfSet.proxySetBean!!.proxies = listOf(selfSet.id)
        SagerDatabase.proxyDao.updateProxy(selfSet)

        val selfError = assertFailsWith<IllegalStateException> {
            buildConfig(selfSet, forTest = true)
        }
        assertEquals(
            "Circular proxy reference: ${selfSet.id} -> ${selfSet.id}",
            selfError.message,
        )

        val mixedSet = createProxySet(
            groupId = group.id,
            order = 2,
            name = "mixed-set",
            proxies = emptyList(),
        )
        val mixedChain = createChain(
            groupId = group.id,
            order = 3,
            name = "mixed-chain",
            proxies = listOf(mixedSet.id),
        )
        mixedSet.proxySetBean!!.proxies = listOf(mixedChain.id)
        SagerDatabase.proxyDao.updateProxy(mixedSet)

        val mixedError = assertFailsWith<IllegalStateException> {
            buildConfig(mixedSet, forTest = true)
        }
        assertEquals(
            "Circular proxy reference: ${mixedSet.id} -> ${mixedChain.id} -> ${mixedSet.id}",
            mixedError.message,
        )

        val selfChain = createChain(
            groupId = group.id,
            order = 4,
            name = "self-chain",
            proxies = emptyList(),
        )
        selfChain.chainBean!!.proxies = listOf(selfChain.id)
        SagerDatabase.proxyDao.updateProxy(selfChain)

        val chainError = assertFailsWith<IllegalStateException> {
            buildConfig(selfChain, forTest = true)
        }
        assertEquals(
            "Circular proxy reference: ${selfChain.id} -> ${selfChain.id}",
            chainError.message,
        )

        val indirectChainA = createChain(
            groupId = group.id,
            order = 5,
            name = "indirect-a",
            proxies = emptyList(),
        )
        val indirectSet = createProxySet(
            groupId = group.id,
            order = 6,
            name = "indirect-set",
            proxies = emptyList(),
        )
        val indirectChainB = createChain(
            groupId = group.id,
            order = 7,
            name = "indirect-b",
            proxies = listOf(indirectChainA.id),
        )
        indirectSet.proxySetBean!!.proxies = listOf(indirectChainB.id)
        SagerDatabase.proxyDao.updateProxy(indirectSet)
        indirectChainA.chainBean!!.proxies = listOf(indirectSet.id)
        SagerDatabase.proxyDao.updateProxy(indirectChainA)

        val indirectError = assertFailsWith<IllegalStateException> {
            buildConfig(indirectChainA, forTest = true)
        }
        assertEquals(
            "Circular proxy reference: ${indirectChainA.id} -> " +
                "${indirectSet.id} -> ${indirectChainB.id} -> ${indirectChainA.id}",
            indirectError.message,
        )

        val collectedGroup = ProxyGroup(name = "collected").applyDefaultValues()
        collectedGroup.id = SagerDatabase.groupDao.createGroup(collectedGroup)
        val collectedSet = createProxySet(
            groupId = group.id,
            order = 8,
            name = "collected-set",
            proxies = emptyList(),
            type = ProxySetBean.TYPE_GROUP,
            collectGroupId = collectedGroup.id,
        )
        val collectedChainA = createChain(
            groupId = collectedGroup.id,
            order = 1,
            name = "collected-a",
            proxies = emptyList(),
        )
        val collectedChainB = createChain(
            groupId = collectedGroup.id,
            order = 2,
            name = "collected-b",
            proxies = listOf(collectedSet.id),
        )
        collectedChainA.chainBean!!.proxies = listOf(collectedChainB.id)
        SagerDatabase.proxyDao.updateProxy(collectedChainA)

        val collectedError = assertFailsWith<IllegalStateException> {
            buildConfig(collectedSet, forTest = true)
        }
        assertEquals(
            "Circular proxy reference: ${collectedSet.id} -> " +
                "${collectedChainA.id} -> ${collectedChainB.id} -> ${collectedSet.id}",
            collectedError.message,
        )
    }

    @Test
    fun `buildConfig should report missing proxy references`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val missingId = Long.MAX_VALUE

        val chain = createChain(
            groupId = group.id,
            order = 1,
            name = "missing-chain",
            proxies = listOf(missingId),
        )
        val chainError = assertFailsWith<IllegalStateException> {
            buildConfig(chain, forTest = true)
        }
        assertEquals("Missing proxy reference in chain ${chain.id}: $missingId", chainError.message)

        val proxySet = createProxySet(
            groupId = group.id,
            order = 2,
            name = "missing-set",
            proxies = listOf(missingId),
        )
        val setError = assertFailsWith<IllegalStateException> {
            buildConfig(proxySet, forTest = true)
        }
        assertEquals(
            "Missing proxy reference in proxy set ${proxySet.id}: $missingId",
            setError.message,
        )
    }

    @Test
    fun `buildConfig should tolerate a profile whose group was removed`() = runBlocking {
        val group = ProxyGroup(name = "orphaned").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "orphan",
            host = "1.1.1.1",
            port = 1080,
        )
        SagerDatabase.groupDao.deleteById(group.id)

        val result = buildConfig(proxy, forTest = true)

        assertEquals("orphan", result.mainTag)
        assertEquals(proxy.id, result.tagToID["orphan"])
    }

    @Test
    fun `buildConfig should isolate a shared chain between proxy sets`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val sharedEntry = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "shared",
            host = "1.1.1.1",
            port = 1081,
        )
        val sharedExit = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "shared",
            host = "2.2.2.2",
            port = 1082,
        )
        val sharedChain = createChain(
            groupId = group.id,
            order = 3,
            name = "shared-chain",
            proxies = listOf(sharedExit.id, sharedEntry.id),
        )
        val firstSet = createProxySet(
            groupId = group.id,
            order = 4,
            name = "first-set",
            proxies = listOf(sharedChain.id),
        )
        val secondSet = createProxySet(
            groupId = group.id,
            order = 5,
            name = "second-set",
            proxies = listOf(sharedChain.id),
        )
        val rootChain = createChain(
            groupId = group.id,
            order = 6,
            name = "root-chain",
            proxies = listOf(firstSet.id, secondSet.id),
        )

        val result = buildConfig(rootChain, forTest = true)
        val outboundList = parseOutboundList(result)
        val tags = outboundList.map { it["tag"]!!.jsonPrimitive.content }
        val outbounds = outboundList.associateBy { it["tag"]!!.jsonPrimitive.content }
        val firstEntryTag = outbounds["first-set"]!!["outbounds"]!!.jsonArray
            .single().jsonPrimitive.content
        val secondEntryTag = outbounds["second-set"]!!["outbounds"]!!.jsonArray
            .single().jsonPrimitive.content
        val firstExitTag = outbounds[firstEntryTag]!!["detour"]!!.jsonPrimitive.content
        val secondExitTag = outbounds[secondEntryTag]!!["detour"]!!.jsonPrimitive.content

        assertEquals(tags.size, tags.toSet().size)
        assertTrue(firstEntryTag != secondEntryTag)
        assertTrue(firstExitTag != secondExitTag)
        assertEquals(2, result.tagToID.values.count { it == sharedEntry.id })
        assertEquals(2, result.tagToID.values.count { it == sharedExit.id })
        assertEquals(sharedEntry.id, result.tagToID[firstEntryTag])
        assertEquals(sharedEntry.id, result.tagToID[secondEntryTag])
        assertEquals(sharedExit.id, result.tagToID[firstExitTag])
        assertEquals(sharedExit.id, result.tagToID[secondExitTag])
        assertEquals(null, outbounds[firstExitTag]!!["detour"])
        assertEquals(
            "first-set",
            outbounds[secondExitTag]!!["detour"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `buildConfig should isolate an external proxy across proxy set member chains`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val external = ProxyEntity(groupId = group.id, userOrder = 1).putBean(
            MieruBean().apply {
                name = "external"
                serverAddress = "1.1.1.1"
                serverPort = 8964
            }.applyDefaultValues(),
        )
        external.id = SagerDatabase.proxyDao.addProxy(external)
        val firstExit = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "first-exit",
            host = "2.2.2.2",
            port = 1080,
        )
        val secondExit = createSocksProxy(
            groupId = group.id,
            order = 3,
            name = "second-exit",
            host = "3.3.3.3",
            port = 1081,
        )
        val firstChain = createChain(
            groupId = group.id,
            order = 4,
            name = "first-chain",
            proxies = listOf(firstExit.id, external.id),
        )
        val secondChain = createChain(
            groupId = group.id,
            order = 5,
            name = "second-chain",
            proxies = listOf(secondExit.id, external.id),
        )
        val proxySet = createProxySet(
            groupId = group.id,
            order = 6,
            name = "external-set",
            proxies = listOf(firstChain.id, secondChain.id),
        )

        val result = buildConfig(proxySet, forTest = true)
        val externalEntries = result.externalIndex
            .flatMap { it.chain.values }
            .filter { it.id == external.id }

        assertEquals(2, externalEntries.size)
        assertEquals(2, externalEntries.map { it.requireBean().finalPort }.toSet().size)
        assertTrue(externalEntries.all { it !== external })
    }

    @Test
    fun `buildConfig should isolate direct and nested references in one chain`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val shared = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "shared",
            host = "1.1.1.1",
            port = 1080,
        )
        val nested = createChain(
            groupId = group.id,
            order = 2,
            name = "nested",
            proxies = listOf(shared.id),
        )
        val root = createChain(
            groupId = group.id,
            order = 3,
            name = "root",
            proxies = listOf(shared.id, nested.id),
        )

        val result = buildConfig(root, forTest = true)
        val outbounds = parseOutbounds(result)
        val sharedTags = result.tagToID.filterValues { it == shared.id }.keys.toList()

        assertEquals(2, sharedTags.size)
        assertEquals(2, sharedTags.toSet().size)
        val entryTag = sharedTags.single { outbounds[it]!!["detour"] != null }
        val exitTag = sharedTags.single { it != entryTag }
        assertEquals(exitTag, outbounds[entryTag]!!["detour"]?.jsonPrimitive?.content)
        assertEquals(null, outbounds[exitTag]!!["detour"])
    }

    @Test
    fun `buildConfig should isolate shared leaves across chains in one proxy set`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val sharedLeaf = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "shared-leaf",
            host = "1.1.1.1",
            port = 1080,
        )
        val firstExit = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "first-exit",
            host = "2.2.2.2",
            port = 1081,
        )
        val secondExit = createSocksProxy(
            groupId = group.id,
            order = 3,
            name = "second-exit",
            host = "3.3.3.3",
            port = 1082,
        )
        val memberChainA = createChain(
            groupId = group.id,
            order = 4,
            name = "member-chain-a",
            proxies = listOf(firstExit.id, sharedLeaf.id),
        )
        val memberChainB = createChain(
            groupId = group.id,
            order = 5,
            name = "member-chain-b",
            proxies = listOf(secondExit.id, sharedLeaf.id),
        )
        val proxySet = createProxySet(
            groupId = group.id,
            order = 6,
            name = "branch-set",
            proxies = listOf(memberChainA.id, memberChainB.id),
        )
        val outerExit = createSocksProxy(
            groupId = group.id,
            order = 7,
            name = "outer-exit",
            host = "4.4.4.4",
            port = 1083,
        )
        val rootChain = createChain(
            groupId = group.id,
            order = 8,
            name = "root-chain",
            proxies = listOf(outerExit.id, proxySet.id),
        )

        val result = buildConfig(rootChain, forTest = true)
        val outboundList = parseOutboundList(result)
        val tags = outboundList.map { it["tag"]!!.jsonPrimitive.content }
        val outbounds = outboundList.associateBy { it["tag"]!!.jsonPrimitive.content }
        val memberTags = outbounds["branch-set"]!!["outbounds"]!!.jsonArray
            .map { it.jsonPrimitive.content }

        assertEquals("branch-set", result.mainTag)
        assertEquals(tags.size, tags.toSet().size)
        assertEquals(2, memberTags.size)
        assertEquals(2, memberTags.toSet().size)
        assertTrue(memberTags.all { result.tagToID[it] == sharedLeaf.id })
        assertEquals(2, result.tagToID.values.count { it == sharedLeaf.id })

        val exitTags = memberTags.map { memberTag ->
            outbounds[memberTag]!!["detour"]!!.jsonPrimitive.content
        }
        assertEquals(setOf("first-exit", "second-exit"), exitTags.toSet())
        assertEquals(setOf(firstExit.id, secondExit.id), exitTags.map { result.tagToID[it] }.toSet())
        assertEquals("outer-exit", outbounds["first-exit"]!!["detour"]?.jsonPrimitive?.content)
        assertEquals("outer-exit", outbounds["second-exit"]!!["detour"]?.jsonPrimitive?.content)
        assertEquals(null, outbounds["outer-exit"]!!["detour"])
    }

    @Test
    fun `buildConfig should allow a group proxy set to collect a chain`() = runBlocking {
        val rootGroup = ProxyGroup(name = "root").applyDefaultValues()
        rootGroup.id = SagerDatabase.groupDao.createGroup(rootGroup)
        val memberGroup = ProxyGroup(name = "members").applyDefaultValues()
        memberGroup.id = SagerDatabase.groupDao.createGroup(memberGroup)
        val leafGroup = ProxyGroup(name = "leaves").applyDefaultValues()
        leafGroup.id = SagerDatabase.groupDao.createGroup(leafGroup)
        val entry = createSocksProxy(
            groupId = leafGroup.id,
            order = 1,
            name = "entry",
            host = "1.1.1.1",
            port = 1080,
        )
        val exit = createSocksProxy(
            groupId = leafGroup.id,
            order = 2,
            name = "exit",
            host = "2.2.2.2",
            port = 1081,
        )
        val chain = createChain(
            groupId = memberGroup.id,
            order = 1,
            name = "member-chain",
            proxies = listOf(exit.id, entry.id),
        )
        val proxySet = createProxySet(
            groupId = rootGroup.id,
            order = 1,
            name = "group-set",
            proxies = emptyList(),
            type = ProxySetBean.TYPE_GROUP,
            collectGroupId = memberGroup.id,
        )

        val result = buildConfig(proxySet, forTest = true)
        val outbounds = parseOutbounds(result)
        val memberTag = outbounds["group-set"]!!["outbounds"]!!.jsonArray
            .single().jsonPrimitive.content

        assertEquals("entry", memberTag)
        assertEquals(entry.id, result.tagToID[memberTag])
        assertEquals("exit", outbounds[memberTag]!!["detour"]?.jsonPrimitive?.content)
        assertEquals(null, outbounds["exit"]!!["detour"])
        assertEquals(1, result.tagToID.values.count { it == entry.id })
        assertEquals(1, result.tagToID.values.count { it == exit.id })
    }

    @Test
    fun `buildConfig should reject a group proxy set with no filtered members`() = runBlocking {
        val rootGroup = ProxyGroup(name = "root").applyDefaultValues()
        rootGroup.id = SagerDatabase.groupDao.createGroup(rootGroup)
        val memberGroup = ProxyGroup(name = "members").applyDefaultValues()
        memberGroup.id = SagerDatabase.groupDao.createGroup(memberGroup)
        createSocksProxy(
            groupId = memberGroup.id,
            order = 1,
            name = "excluded",
            host = "1.1.1.1",
            port = 1080,
        )
        val proxySet = createProxySet(
            groupId = rootGroup.id,
            order = 1,
            name = "empty-set",
            proxies = emptyList(),
            type = ProxySetBean.TYPE_GROUP,
            collectGroupId = memberGroup.id,
        )
        proxySet.proxySetBean!!.groupFilterNotRegex = "^does-not-match$"
        SagerDatabase.proxyDao.updateProxy(proxySet)
        val rootChain = createChain(
            groupId = rootGroup.id,
            order = 2,
            name = "root-chain",
            proxies = listOf(proxySet.id),
        )

        val error = assertFailsWith<IllegalStateException> {
            buildConfig(rootChain, forTest = true)
        }
        assertEquals("Proxy set ${proxySet.id} has no usable members", error.message)
    }

    @Test
    fun `buildConfig should reject an invalid group proxy set filter`(): Unit = runBlocking {
        val rootGroup = ProxyGroup(name = "root").applyDefaultValues()
        rootGroup.id = SagerDatabase.groupDao.createGroup(rootGroup)
        val memberGroup = ProxyGroup(name = "members").applyDefaultValues()
        memberGroup.id = SagerDatabase.groupDao.createGroup(memberGroup)
        createSocksProxy(
            groupId = memberGroup.id,
            order = 1,
            name = "member",
            host = "1.1.1.1",
            port = 1080,
        )
        val proxySet = createProxySet(
            groupId = rootGroup.id,
            order = 1,
            name = "invalid-filter",
            proxies = emptyList(),
            type = ProxySetBean.TYPE_GROUP,
            collectGroupId = memberGroup.id,
        )
        proxySet.proxySetBean!!.groupFilterNotRegex = "["
        SagerDatabase.proxyDao.updateProxy(proxySet)

        assertFailsWith<IllegalArgumentException> {
            buildConfig(proxySet, forTest = true)
        }
    }

    @Test
    fun `buildConfig should exclude a group proxy set from its implicit members`(): Unit = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val member = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "member",
            host = "1.1.1.1",
            port = 1080,
        )
        val proxySet = createProxySet(
            groupId = group.id,
            order = 2,
            name = "group-set",
            proxies = emptyList(),
            type = ProxySetBean.TYPE_GROUP,
            collectGroupId = group.id,
        )

        val outbounds = parseOutbounds(buildConfig(proxySet, forTest = true))

        assertEquals(
            listOf("member"),
            outbounds["group-set"]!!["outbounds"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertNotNull(outbounds["member"])
    }

    @Test
    fun `buildConfig should keep shared proxy set members independent`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val member = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "shared",
            host = "1.1.1.1",
            port = 1080,
        )
        val firstSet = createProxySet(
            groupId = group.id,
            order = 2,
            name = "first-set",
            proxies = listOf(member.id),
        )
        val secondSet = createProxySet(
            groupId = group.id,
            order = 3,
            name = "second-set",
            proxies = listOf(member.id),
        )
        val chain = createChain(
            groupId = group.id,
            order = 4,
            name = "chain",
            proxies = listOf(firstSet.id, secondSet.id),
        )

        val result = buildConfig(chain, forTest = true)
        val outbounds = parseOutboundList(result)
        val firstSelector = outbounds.single {
            it["tag"]?.jsonPrimitive?.content == "first-set"
        }
        val secondSelector = outbounds.single {
            it["tag"]?.jsonPrimitive?.content == "second-set"
        }
        val firstMemberTag = firstSelector["outbounds"]!!.jsonArray.single().jsonPrimitive.content
        val secondMemberTag = secondSelector["outbounds"]!!.jsonArray.single().jsonPrimitive.content

        assertTrue(firstMemberTag != secondMemberTag)
        assertEquals(2, result.tagToID.values.count { it == member.id })
        assertEquals(member.id, result.tagToID[firstMemberTag])
        assertEquals(member.id, result.tagToID[secondMemberTag])
        assertEquals(
            "first-set",
            outbounds.single { it["tag"]?.jsonPrimitive?.content == secondMemberTag }["detour"]
                ?.jsonPrimitive?.content,
        )
        assertEquals(
            null,
            outbounds.single { it["tag"]?.jsonPrimitive?.content == firstMemberTag }["detour"],
        )
    }

    @Test
    fun `buildConfig should keep nested endpoint chain usable by urltest`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val endpoint = ProxyEntity(groupId = group.id, userOrder = 1).putBean(
            OpenVPNBean().apply {
                name = "endpoint"
                serverAddress = "vpn.example.com"
                serverPort = 1194
            }.applyDefaultValues(),
        )
        endpoint.id = SagerDatabase.proxyDao.addProxy(endpoint)
        val exit = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "exit",
            host = "2.2.2.2",
            port = 1080,
        )
        val memberChain = createChain(
            groupId = group.id,
            order = 3,
            name = "member-chain",
            proxies = listOf(exit.id, endpoint.id),
        )
        val proxySet = createProxySet(
            groupId = group.id,
            order = 4,
            name = "urltest",
            proxies = listOf(memberChain.id),
            management = ProxySetBean.MANAGEMENT_URLTEST,
        )

        val result = buildConfig(proxySet, forTest = true)
        val outbounds = parseOutbounds(result)
        val endpoints = parseEndpoints(result)

        assertEquals("urltest", result.mainTag)
        assertEquals(
            SingBoxOptions.TYPE_URLTEST,
            outbounds["urltest"]!!["type"]?.jsonPrimitive?.content,
        )
        assertEquals(
            listOf("endpoint"),
            outbounds["urltest"]!!["outbounds"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(null, outbounds["endpoint"])
        assertEquals(
            SingBoxOptions.TYPE_OPENVPN_CLIENT,
            endpoints["endpoint"]!!["type"]?.jsonPrimitive?.content,
        )
        assertEquals("exit", endpoints["endpoint"]!!["detour"]?.jsonPrimitive?.content)
        assertEquals(null, outbounds["exit"]!!["detour"])
        assertEquals(endpoint.id, result.tagToID["endpoint"])
    }

    @Test
    fun `buildConfig should connect nested external mapping to its continuation`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val external = ProxyEntity(groupId = group.id, userOrder = 1).putBean(
            MieruBean().apply {
                name = "external"
                serverAddress = "1.1.1.1"
                serverPort = 8964
            }.applyDefaultValues(),
        )
        external.id = SagerDatabase.proxyDao.addProxy(external)
        val memberExit = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "member-exit",
            host = "2.2.2.2",
            port = 1080,
        )
        val memberChain = createChain(
            groupId = group.id,
            order = 3,
            name = "member-chain",
            proxies = listOf(memberExit.id, external.id),
        )
        val proxySet = createProxySet(
            groupId = group.id,
            order = 4,
            name = "set",
            proxies = listOf(memberChain.id),
        )
        val tail = createSocksProxy(
            groupId = group.id,
            order = 5,
            name = "tail",
            host = "3.3.3.3",
            port = 1080,
        )
        val outerChain = createChain(
            groupId = group.id,
            order = 6,
            name = "outer-chain",
            proxies = listOf(tail.id, proxySet.id),
        )

        val result = buildConfig(outerChain, forTest = true)
        val outbounds = parseOutbounds(result)
        val mappingRule = parseRouteRules(result).single {
            it["outbound"]?.jsonPrimitive?.content == "member-exit" && it["inbound"] != null
        }
        val mappingTag = mappingRule["inbound"]!!.jsonArray.single().jsonPrimitive.content

        assertEquals("set", result.mainTag)
        assertEquals(
            listOf("external"),
            outbounds["set"]!!["outbounds"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(mappingTag.startsWith("c-0-mapping-${external.id}-"))
        assertTrue(
            parseRouteRules(result).none {
                it["outbound"]?.jsonPrimitive?.content == TAG_DIRECT &&
                    it["inbound"]?.jsonArray?.any { tag -> tag.jsonPrimitive.content == mappingTag } == true
            },
        )
        assertEquals("tail", outbounds["member-exit"]!!["detour"]?.jsonPrimitive?.content)
        assertEquals(
            1,
            result.externalIndex.flatMap { it.chain.values }.count { it.id == external.id },
        )
    }

    @Test
    fun `buildConfig should isolate cached external profiles between chains`() = runBlocking {
        DataStore.serviceMode = Key.MODE_VPN

        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val external = ProxyEntity(groupId = group.id, userOrder = 1).putBean(
            MieruBean().apply {
                name = "external"
                serverAddress = "1.1.1.1"
                serverPort = 8964
            }.applyDefaultValues(),
        )
        external.id = SagerDatabase.proxyDao.addProxy(external)
        val landing = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "landing",
            host = "2.2.2.2",
            port = 1080,
        )
        val chain = createChain(
            groupId = group.id,
            order = 3,
            name = "main-chain",
            proxies = listOf(external.id, landing.id),
        )
        ProfileManager.createRule(
            RuleEntity(
                enabled = true,
                name = "external-rule",
                domains = "full:external.example",
                outbound = external.id,
            ),
        )

        val externalEntries = buildConfig(chain).externalIndex
            .flatMap { it.chain.entries }
            .filter { it.value.id == external.id }

        assertEquals(2, externalEntries.size)
        assertEquals(2, externalEntries.map { it.key }.toSet().size)
        assertTrue(externalEntries[0].value !== externalEntries[1].value)
        assertEquals(
            2,
            externalEntries.map { it.value.requireBean().finalPort }.toSet().size,
        )
        for ((_, profile) in externalEntries) {
            val bean = profile.requireBean()
            assertEquals(LOCALHOST4, bean.finalAddress)
            assertTrue(bean.finalPort in 1..65535)
        }
    }

    @Test
    fun `buildConfig should reject repeated references and report cycles`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val member = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "member",
            host = "1.1.1.1",
            port = 1080,
        )
        val proxySet = createProxySet(
            groupId = group.id,
            order = 2,
            name = "set",
            proxies = listOf(member.id),
        )
        val repeatedChain = createChain(
            groupId = group.id,
            order = 3,
            name = "repeated-chain",
            proxies = listOf(proxySet.id, proxySet.id),
        )

        val repeatedChainError = assertFailsWith<IllegalStateException> {
            buildConfig(repeatedChain, forTest = true)
        }
        assertEquals(
            "Duplicate proxy reference in chain ${repeatedChain.id}: ${proxySet.id}",
            repeatedChainError.message,
        )

        val repeatedSet = createProxySet(
            groupId = group.id,
            order = 4,
            name = "repeated-set",
            proxies = listOf(member.id, member.id),
        )
        val repeatedSetError = assertFailsWith<IllegalStateException> {
            buildConfig(repeatedSet, forTest = true)
        }
        assertEquals(
            "Duplicate proxy reference in proxy set ${repeatedSet.id}: ${member.id}",
            repeatedSetError.message,
        )

        group.landingProxy = member.id
        SagerDatabase.groupDao.updateGroup(group)
        val repeatedTopLevelError = assertFailsWith<IllegalStateException> {
            buildConfig(member, forTest = true)
        }
        assertEquals(
            "Duplicate proxy reference: ${member.id}",
            repeatedTopLevelError.message,
        )
        group.landingProxy = -1L
        SagerDatabase.groupDao.updateGroup(group)

        val firstChain = createChain(
            groupId = group.id,
            order = 5,
            name = "first-chain",
            proxies = emptyList(),
        )
        val secondChain = createChain(
            groupId = group.id,
            order = 6,
            name = "second-chain",
            proxies = listOf(firstChain.id),
        )
        firstChain.chainBean!!.proxies = listOf(secondChain.id)
        SagerDatabase.proxyDao.updateProxy(firstChain)

        val error = assertFailsWith<IllegalStateException> {
            buildConfig(firstChain, forTest = true)
        }
        assertTrue(error.message.orEmpty().contains("Circular proxy reference"))
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
    fun `buildConfig should force process search only on desktop`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)

        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )

        DataStore.forcedSearchProcess = true
        val forcedRoute = parseRouteOptions(buildConfig(proxy))
        if (PlatformInfo.isAndroid) {
            assertEquals(null, forcedRoute["find_process"])
        } else {
            assertEquals("true", forcedRoute["find_process"]?.jsonPrimitive?.content)
        }

        DataStore.forcedSearchProcess = false
        val defaultRoute = parseRouteOptions(buildConfig(proxy))
        assertEquals(null, defaultRoute["find_process"])
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
    fun `buildConfig should emit bypass action with proxy fallback and without dns bypass action`() = runBlocking {
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
                name = "bypass-kernel",
                domains = "full:bypass.example",
                action = SingBoxOptions.ACTION_BYPASS,
            ),
        )

        val result = buildConfig(proxy)
        val bypassRule = parseRouteRules(result).first {
            it["action"]?.jsonPrimitive?.content == SingBoxOptions.ACTION_BYPASS
        }

        assertEquals("main", bypassRule["outbound"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("bypass.example"),
            bypassRule["domain"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            -1,
            parseDnsRules(result).indexOfFirst {
                it["action"]?.jsonPrimitive?.content == SingBoxOptions.ACTION_BYPASS
            },
        )
    }

    @Test
    fun `buildConfig should emit bypass fallback outbound when set`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )
        val fallback = createSocksProxy(
            groupId = group.id,
            order = 2,
            name = "fallback",
            host = "2.2.2.2",
            port = 2080,
        )
        ProfileManager.createRule(
            RuleEntity(
                enabled = true,
                name = "bypass-custom-fallback",
                domains = "full:custom-fallback.example",
                action = SingBoxOptions.ACTION_BYPASS,
                outbound = fallback.id,
            ),
        )
        ProfileManager.createRule(
            RuleEntity(
                enabled = true,
                name = "bypass-proxy-fallback",
                domains = "full:proxy-fallback.example",
                action = SingBoxOptions.ACTION_BYPASS,
                outbound = RuleEntity.OUTBOUND_PROXY,
            ),
        )

        val routeRules = parseRouteRules(buildConfig(proxy))
        fun ruleFor(domain: String) = routeRules.first {
            it["domain"]?.jsonArray?.map { item -> item.jsonPrimitive.content } == listOf(domain)
        }

        assertEquals(
            "fallback",
            ruleFor("custom-fallback.example")["outbound"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "main",
            ruleFor("proxy-fallback.example")["outbound"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `buildConfig should only add bridge outbound when a bridge rule exists`() = runBlocking {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val proxy = createSocksProxy(
            groupId = group.id,
            order = 1,
            name = "main",
            host = "1.1.1.1",
            port = 1080,
        )

        assertEquals(null, parseOutbounds(buildConfig(proxy))[TAG_BRIDGE])

        ProfileManager.createRule(
            RuleEntity(
                enabled = true,
                name = "bridge",
                domains = "full:bridge.example",
                outbound = RuleEntity.OUTBOUND_BRIDGE,
            ),
        )

        assertEquals(
            SingBoxOptions.TYPE_BRIDGE,
            parseOutbounds(buildConfig(proxy))[TAG_BRIDGE]?.get("type")?.jsonPrimitive?.content,
        )
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

    private fun parseOutboundList(result: ConfigBuildResult) =
        Json.parseToJsonElement(result.config).jsonObject["outbounds"]!!
            .jsonArray
            .map { it.jsonObject }

    private fun parseEndpoints(result: ConfigBuildResult) =
        Json.parseToJsonElement(result.config).jsonObject["endpoints"]!!
            .jsonArray
            .associateBy { it.jsonObject["tag"]!!.jsonPrimitive.content }
            .mapValues { it.value.jsonObject }

    private fun parseDnsRules(result: ConfigBuildResult) =
        Json.parseToJsonElement(result.config).jsonObject["dns"]!!
            .jsonObject["rules"]!!
            .jsonArray
            .map { it.jsonObject }

    private fun parseRouteOptions(result: ConfigBuildResult) =
        Json.parseToJsonElement(result.config).jsonObject["route"]!!.jsonObject

    private fun parseRouteRules(result: ConfigBuildResult) =
        Json.parseToJsonElement(result.config).jsonObject["route"]!!
            .jsonObject["rules"]!!
            .jsonArray
            .map { it.jsonObject }

    private fun parseDnsServers(result: ConfigBuildResult) =
        Json.parseToJsonElement(result.config).jsonObject["dns"]!!
            .jsonObject["servers"]!!
            .jsonArray
            .associateBy { it.jsonObject["tag"]!!.jsonPrimitive.content }
            .mapValues { it.value.jsonObject }

    private fun assertEndpointDNS(result: ConfigBuildResult, tag: String, type: String) {
        val dnsTag = "dns-$tag"
        val server = assertNotNull(parseDnsServers(result)[dnsTag])
        val rule = parseDnsRules(result).first {
            it["preferred_by"]?.jsonArray?.map { item -> item.jsonPrimitive.content } == listOf(
                dnsTag,
            )
        }

        assertEquals(type, server["type"]?.jsonPrimitive?.content)
        assertEquals(tag, server["endpoint"]?.jsonPrimitive?.content)
        assertEquals(dnsTag, rule["server"]?.jsonPrimitive?.content)
    }

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

    private suspend fun createProxySet(
        groupId: Long,
        order: Long,
        name: String,
        proxies: List<Long>,
        management: Int = ProxySetBean.MANAGEMENT_SELECTOR,
        type: Int = ProxySetBean.TYPE_LIST,
        collectGroupId: Long = 0L,
    ): ProxyEntity {
        val proxySet = ProxyEntity(groupId = groupId, userOrder = order).putBean(
            ProxySetBean().apply {
                this.name = name
                this.management = management
                this.type = type
                this.proxies = proxies
                this.groupId = collectGroupId
            }.applyDefaultValues(),
        )
        proxySet.id = SagerDatabase.proxyDao.addProxy(proxySet)
        return proxySet
    }
}
