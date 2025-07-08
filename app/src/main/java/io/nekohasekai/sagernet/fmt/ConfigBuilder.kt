package io.nekohasekai.sagernet.fmt

import android.widget.Toast
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.NetworkInterfaceStrategy
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.RuleProvider
import io.nekohasekai.sagernet.SagerNet.Companion.app
import io.nekohasekai.sagernet.TunImplementation
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.bg.VpnService.Companion.PRIVATE_VLAN4_ROUTER
import io.nekohasekai.sagernet.bg.VpnService.Companion.PRIVATE_VLAN6_ROUTER
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_CONFIG
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.ConfigBuildResult.IndexEntity
import io.nekohasekai.sagernet.fmt.SingBoxOptions.CacheFileOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.DNSOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.DNSRule_Default
import io.nekohasekai.sagernet.fmt.SingBoxOptions.DomainResolveOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.ExperimentalOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Inbound_DirectOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Inbound_HTTPMixedOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Inbound_TunOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.LogOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.MyOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.NTPOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.NewDNSServerOptions_FakeIPDNSServerOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.NewDNSServerOptions_HostsDNSServerOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.NewDNSServerOptions_LocalDNSServerOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Outbound
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Outbound_DirectOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Outbound_SOCKSOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.RouteOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Rule_Default
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Rule_Logical
import io.nekohasekai.sagernet.fmt.SingBoxOptions.User
import io.nekohasekai.sagernet.fmt.anytls.AnyTLSBean
import io.nekohasekai.sagernet.fmt.anytls.buildSingBoxOutboundAnyTLSBean
import io.nekohasekai.sagernet.fmt.config.ConfigBean
import io.nekohasekai.sagernet.fmt.direct.DirectBean
import io.nekohasekai.sagernet.fmt.direct.buildSingBoxOutboundDirectBean
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria.buildSingBoxOutboundHysteriaBean
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.fmt.internal.ProxySetBean
import io.nekohasekai.sagernet.fmt.internal.buildSingBoxOutboundProxySetBean
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.buildSingBoxOutboundShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowtls.ShadowTLSBean
import io.nekohasekai.sagernet.fmt.shadowtls.buildSingBoxOutboundShadowTLSBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.socks.buildSingBoxOutboundSocksBean
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.ssh.buildSingBoxOutboundSSHBean
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.tuic.buildSingBoxOutboundTuicBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.buildSingBoxOutboundStandardV2RayBean
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.fmt.wireguard.buildSingBoxEndpointWireGuardBean
import io.nekohasekai.sagernet.ktx.JSONMap
import io.nekohasekai.sagernet.ktx.asMap
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.defaultOr
import io.nekohasekai.sagernet.ktx.isExpert
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.listByLineOrComma
import io.nekohasekai.sagernet.ktx.mapX
import io.nekohasekai.sagernet.ktx.mergeJson
import io.nekohasekai.sagernet.ktx.mkPort
import io.nekohasekai.sagernet.ktx.reverse
import io.nekohasekai.sagernet.ktx.toJsonMap
import io.nekohasekai.sagernet.logLevelString
import io.nekohasekai.sagernet.utils.PackageCache
import libcore.Libcore
import moe.matsuri.nb4a.utils.JavaUtil.gson

// Inbound
const val TAG_MIXED = "mixed-in"
const val TAG_TUN = "tun-in"
const val TAG_DNS_IN = "dns-in" // strategic

// Outbound
const val TAG_PROXY = "proxy"
const val TAG_DIRECT = "direct"
const val TAG_BLOCK = "block" // Build block outbound for custom json

// DNS
const val TAG_DNS_REMOTE = "dns-remote"
const val TAG_DNS_DIRECT = "dns-direct"
const val TAG_DNS_LOCAL = "dns-local"
const val TAG_DNS_FAKE = "dns-fake"
const val TAG_DNS_HOSTS = "dns-hosts"

const val LOCALHOST4 = "127.0.0.1"

val FAKE_DNS_QUERY_TYPE get() = listOf("A", "AAAA")

class ConfigBuildResult(
    var config: String,
    var externalIndex: List<IndexEntity>,
    var trafficMap: Map<String, List<ProxyEntity>>,
    val main: Long,
    val tagToID: Map<String, Long>,
) {
    data class IndexEntity(var chain: LinkedHashMap<Int, ProxyEntity>)
}

fun buildConfig(
    proxy: ProxyEntity, forTest: Boolean = false, forExport: Boolean = false,
): ConfigBuildResult {

    if (proxy.type == TYPE_CONFIG) {
        val bean = proxy.configBean!!
        if (bean.type == ConfigBean.TYPE_CONFIG) {
            return ConfigBuildResult(
                bean.config,
                listOf(),
                mapOf(TAG_PROXY to listOf(proxy)),
                proxy.id,
                mapOf(TAG_PROXY to proxy.id),
            )
        }
    }

    val trafficMap = HashMap<String, List<ProxyEntity>>()
    val tagMap = HashMap<Long, String>()
    val globalOutbounds = HashMap<Long, String>()
    val optionsToMerge = proxy.requireBean().customConfigJson ?: ""

    fun ProxyEntity.resolveChainInternal(): MutableList<ProxyEntity> {
        return when (val bean = requireBean()) {
            is ChainBean -> {
                val beans = SagerDatabase.proxyDao.getEntities(bean.proxies)
                val beansMap = beans.associateBy { it.id }
                val beanList = mutableListOf<ProxyEntity>()
                for (proxyId in bean.proxies) {
                    val item = beansMap[proxyId] ?: continue
                    beanList.addAll(item.resolveChainInternal())
                }
                beanList.asReversed()
            }

            is ProxySetBean -> {
                val beans = when (bean.type) {
                    ProxySetBean.TYPE_LIST -> SagerDatabase.proxyDao.getEntities(bean.proxies)
                    ProxySetBean.TYPE_GROUP -> SagerDatabase.proxyDao.getByGroup(bean.groupId)
                    else -> throw IllegalStateException("invalid proxy set type ${bean.type}")
                }

                val beansMap = beans.associateBy { it.id }
                val beanList = mutableListOf<ProxyEntity>()
                val regex = bean.groupFilterNotRegex.blankAsNull()?.toRegex()
                for (proxyId in beansMap.keys) {
                    val item = beansMap[proxyId] ?: continue
                    if (item.id == id) continue
                    if (regex?.containsMatchIn(item.displayName()) == false) continue
                    when (item.type) {
                        ProxyEntity.TYPE_PROXY_SET -> error("Nested proxy set are not supported")
                        ProxyEntity.TYPE_CHAIN -> error("Chain is incompatible with group bean")
                    }
                    beanList.add(item)
                }
                beanList.add(this)
                beanList
            }

            else -> mutableListOf(this)
        }
    }

    fun ProxyEntity.resolveChain(): MutableList<ProxyEntity> {
        val thisGroup = SagerDatabase.groupDao.getById(groupId)
        val frontProxy = thisGroup?.frontProxy?.let { SagerDatabase.proxyDao.getById(it) }
        val landingProxy = thisGroup?.landingProxy?.let { SagerDatabase.proxyDao.getById(it) }
        val list = resolveChainInternal()
        if (frontProxy != null) {
            if (type == ProxyEntity.TYPE_PROXY_SET) {
                error("front proxy with proxy set")
            }
            list.add(frontProxy)
        }
        if (landingProxy != null) {
            if (type == ProxyEntity.TYPE_PROXY_SET) {
                error("landing proxy with proxy set")
            }
            list.add(0, landingProxy)
        }
        return list
    }

    val logLevel = DataStore.logLevel
    val extraRules = if (forTest) listOf() else SagerDatabase.rulesDao.enabledRules()
    val extraProxies =
        if (forTest) mapOf() else SagerDatabase.proxyDao.getEntities(extraRules.mapNotNull { rule ->
            rule.outbound.takeIf { it > 0 && it != proxy.id }
        }.toHashSet().toList()).associateBy { it.id }
    val userDNSRuleList = mutableListOf<DNSRule_Default>()
    val domainListDNSDirectForce = mutableListOf<String>()
    val bypassDNSBeans = hashSetOf<AbstractBean>()
    val isVPN = DataStore.serviceMode == Key.MODE_VPN
    val bind = if (!forTest && DataStore.allowAccess) "0.0.0.0" else LOCALHOST4
    val remoteDns = DataStore.remoteDns.split("\n")
        .mapNotNull { dns -> dns.trim().takeIf { it.isNotBlank() && !it.startsWith("#") } }
    val directDNS = DataStore.directDns.split("\n")
        .mapNotNull { dns -> dns.trim().takeIf { it.isNotBlank() && !it.startsWith("#") } }
    val localDNSPort = DataStore.localDNSPort.takeIf { it > 0 }
    val useFakeDns by lazy { DataStore.enableFakeDns && !forTest }
    val dnsHosts by lazy {
        DataStore.dnsHosts.blankAsNull()?.lineSequence()
            ?.mapNotNull { line ->
                val trimmed = line.trim()
                // Promote the compatibility.
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
                val tokens = trimmed.split("\\s+".toRegex()) // Handle direct copy from host file.
                if (tokens.size < 2) return@mapNotNull null
                val host = tokens[0]
                val ips = tokens.drop(1)
                host to ips
            }
            ?.toMap()
            ?.takeIf { it.isNotEmpty() }
    }
    val externalIndexMap = ArrayList<IndexEntity>()
    val networkStrategy = DataStore.networkStrategy
    val networkInterfaceStrategy = DataStore.networkInterfaceType
    val networkPreferredInterfaces = DataStore.networkPreferredInterfaces.toList()
    var hasJuicity = false
    val defaultStrategy = DataStore.networkStrategy.blankAsNull()

    // server+port:tags
    // This structure may reduce rules when multiple rules share the same server+port.
    val mappingOverride: LinkedHashMap<Pair<String, Int>, MutableList<String>> =
        LinkedHashMap()

    return MyOptions().apply {
        if (!forTest) experimental = ExperimentalOptions().apply {
            if (!forExport) {
                if (isExpert) DataStore.debugListen.blankAsNull()?.let {
                    debug = SingBoxOptions.DebugOptions().apply {
                        listen = it
                    }
                }
            }
            cache_file = CacheFileOptions().apply {
                enabled = true
                store_fakeip = true
                path = "../cache/cache.db"
            }
        }

        log = LogOptions().apply {
            level = logLevelString(logLevel)
        }

        if (DataStore.ntpEnable) ntp = NTPOptions().apply {
            enabled = true
            server = DataStore.ntpAddress
            server_port = DataStore.ntpPort
            interval = DataStore.ntpInterval

            if (!server.isIpAddress()) {
                domainListDNSDirectForce.add(server)
            }
        }

        dns = DNSOptions().apply {
            servers = mutableListOf()
            rules = mutableListOf()
            independent_cache = true
        }

        inbounds = mutableListOf()

        if (!forTest) {
            if (isVPN) inbounds.add(Inbound_TunOptions().apply {
                type = SingBoxOptions.TYPE_TUN
                tag = TAG_TUN
                stack = when (DataStore.tunImplementation) {
                    TunImplementation.GVISOR -> "gvisor"
                    TunImplementation.SYSTEM -> "system"
                    else -> "mixed"
                }
                mtu = DataStore.mtu
                when (networkStrategy) {
                    SingBoxOptions.STRATEGY_IPV4_ONLY -> {
                        address = listOf(VpnService.PRIVATE_VLAN4_CLIENT + "/28")
                    }

                    SingBoxOptions.STRATEGY_IPV6_ONLY -> {
                        address = listOf(VpnService.PRIVATE_VLAN6_CLIENT + "/126")
                    }

                    else -> {
                        address = listOf(
                            VpnService.PRIVATE_VLAN4_CLIENT + "/28",
                            VpnService.PRIVATE_VLAN6_CLIENT + "/126",
                        )
                    }
                }
            })
            inbounds.add(Inbound_HTTPMixedOptions().apply {
                type = SingBoxOptions.TYPE_MIXED
                tag = TAG_MIXED
                listen = bind
                listen_port = DataStore.mixedPort
                if (DataStore.inboundUsername.isNotBlank() || DataStore.inboundPassword.isNotBlank()) {
                    users = listOf(
                        User().apply {
                            username = DataStore.inboundUsername
                            password = DataStore.inboundPassword
                        }
                    )
                }
            })
        }

        outbounds = mutableListOf()

        // init routing object
        route = RouteOptions().apply {
            auto_detect_interface = true
            rules = mutableListOf()
            rule_set = mutableListOf()
        }

        // returns outbound tag
        // chainId == 0L => main proxy
        fun buildChain(chainId: Long, entity: ProxyEntity): String {
            val profileList = entity.resolveChain()
            val chainTrafficSet = LinkedHashSet<ProxyEntity>().apply {
                addAll(profileList)
                add(entity)
            }

            var currentOutbound = mutableMapOf<String, Any?>()
            lateinit var pastOutbound: JSONMap
            lateinit var pastInboundTag: String
            var pastEntity: ProxyEntity? = null
            val externalChainMap = LinkedHashMap<Int, ProxyEntity>()
            externalIndexMap.add(IndexEntity(externalChainMap))
            val chainOutbounds = ArrayList<JSONMap>()

            // chainTagOut: v2ray outbound tag for this chain
            var chainTagOut = ""
            val chainTag = "c-$chainId"

            val isProxySet = entity.type == ProxyEntity.TYPE_PROXY_SET
            val readableNames = if (isProxySet) {
                mutableSetOf<String>()
            } else {
                null
            }

            fun addReadableName(name: String): String {
                if (readableNames == null) return name
                if (readableNames.add(name)) {
                    return name
                }
                var count = 0
                var newName = "$name-$count"
                while (!readableNames.add(newName)) {
                    count++
                    newName = "$name-$count"
                }
                return newName
            }

            profileList.forEachIndexed { index, proxyEntity ->
                val bean = proxyEntity.requireBean()

                // needGlobal: can only contain one?
                var needGlobal = false

                // first profile set as global
                if (index == profileList.lastIndex) {
                    needGlobal = true
                    bypassDNSBeans.add(proxyEntity.requireBean())
                }

                val tagOut = if (
                    chainId == 0L &&
                    ((isProxySet && index == profileList.lastIndex) || (!isProxySet && index == 0))
                ) {
                    TAG_PROXY
                } else {
                    addReadableName(proxyEntity.displayName())
                }

                // chain rules
                if (!isProxySet) {
                    if (index > 0) {
                        // chain route/proxy rules
                        if (pastEntity!!.needExternal()) {
                            route.rules.add(Rule_Default().apply {
                                inbound = listOf(pastInboundTag)
                                outbound = tagOut
                            })
                        } else {
                            pastOutbound["detour"] = tagOut
                        }
                    } else {
                        // index == 0 means last profile in chain / not chain
                        chainTagOut = tagOut
                    }
                } else {
                    if (index == profileList.lastIndex) {
                        chainTagOut = tagOut
                    }
                }

                // now tagOut is determined
                if (needGlobal) {
                    globalOutbounds[proxyEntity.id]?.let {
                        if (index == 0) chainTagOut = it // single, duplicate chain
                        return@forEachIndexed
                    }
                    globalOutbounds[proxyEntity.id] = tagOut
                }

                if (proxyEntity.needExternal()) { // external outbound
                    val localPort = mkPort()
                    externalChainMap[localPort] = proxyEntity
                    currentOutbound = Outbound_SOCKSOptions().apply {
                        type = SingBoxOptions.TYPE_SOCKS
                        server = LOCALHOST4
                        server_port = localPort
                    }.asMap()
                    if (bean is JuicityBean) hasJuicity = true
                } else { // internal outbound
                    currentOutbound = when (bean) {
                        is ConfigBean -> bean.config.toJsonMap()

                        is ShadowTLSBean -> // before StandardV2RayBean
                            buildSingBoxOutboundShadowTLSBean(bean).asMap()

                        is StandardV2RayBean -> // http/trojan/vmess/vless
                            buildSingBoxOutboundStandardV2RayBean(bean).asMap()

                        is HysteriaBean -> buildSingBoxOutboundHysteriaBean(bean).asMap()

                        is TuicBean -> buildSingBoxOutboundTuicBean(bean).asMap()

                        is SOCKSBean -> buildSingBoxOutboundSocksBean(bean).asMap()

                        is ShadowsocksBean -> buildSingBoxOutboundShadowsocksBean(bean).asMap()

                        is WireGuardBean -> buildSingBoxEndpointWireGuardBean(bean).asMap()

                        is SSHBean -> buildSingBoxOutboundSSHBean(bean).asMap()

                        is DirectBean -> buildSingBoxOutboundDirectBean(bean).asMap()

                        is AnyTLSBean -> buildSingBoxOutboundAnyTLSBean(bean).asMap()

                        is ProxySetBean -> {
                            val tags = readableNames!!.toList().filterNot { it == tagOut }
                            buildSingBoxOutboundProxySetBean(
                                bean,
                                tags,
                            ).asMap()
                        }

                        else -> throw IllegalStateException("can't reach")
                    }

                    currentOutbound.apply {
//                        val keepAliveInterval = DataStore.tcpKeepAliveInterval
//                        val needKeepAliveInterval = keepAliveInterval !in intArrayOf(0, 15)
                        if (!forTest && bean !is ProxySetBean) {
                            if (networkPreferredInterfaces.isNotEmpty()) {
                                this["network_type"] = networkPreferredInterfaces
                                this["network_strategy"] =
                                    mapNetworkInterfaceStrategy(networkInterfaceStrategy)
                            }
                        }
                    }
                }

                // internal & external
                currentOutbound.apply {
                    pastEntity?.requireBean()?.let { pastBean ->
                        // don't loopback
                        if (!pastBean.serverAddress.isIpAddress()) {
                            domainListDNSDirectForce.add(pastBean.serverAddress)
                        }
                    }

                    // Set uot here so that naive socks can apply it.
                    // And it is not necessarily to enable it when enabling multiplex.
                    if (bean.needUDPOverTCP() && this["multiplex"] == null) {
                        this["udp_over_tcp"] = true
                    }

                    this["domain_resolver"] = if (forTest || bean is ProxySetBean) {
                        null
                    } else {
                        DomainResolveOptions().apply {
                            server = TAG_DNS_DIRECT
                            strategy = defaultOr(
                                DataStore.domainStrategyForServer.replace("auto", "").blankAsNull(),
                                { defaultStrategy },
                            )
                        }.asMap()
                    }

                    // custom JSON merge
                    bean.customOutboundJson.blankAsNull()?.toJsonMap()?.let {
                        mergeJson(it, currentOutbound)
                    }
                }

                currentOutbound["tag"] = tagOut
                tagMap[proxyEntity.id] = tagOut

                // External proxy need a direct inbound to forward the traffic
                // For external proxy software, their traffic must goes to sing-box to use protected fd.
                bean.finalAddress = bean.serverAddress
                bean.finalPort = bean.serverPort
                if (bean.canMapping() && proxyEntity.needExternal()) {
                    val mappingPort = mkPort()
                    bean.finalAddress = LOCALHOST4
                    bean.finalPort = mappingPort

                    inbounds.add(Inbound_DirectOptions().apply {
                        type = SingBoxOptions.TYPE_DIRECT
                        listen = LOCALHOST4
                        listen_port = mappingPort
                        tag = "$chainTag-mapping-${proxyEntity.id}"

                        val pair = Pair(bean.serverAddress, bean.serverPort)
                        mappingOverride.getOrPut(pair) { mutableListOf() }.add(tag)

                        pastInboundTag = tag

                        // no chain rule and not outbound, so need to set to direct
                        if (index == profileList.lastIndex) {
                            route.rules.add(Rule_Default().apply {
                                inbound = listOf(tag)
                                outbound = TAG_DIRECT
                            })
                        }
                    })
                }

                outbounds.add(currentOutbound)
                chainOutbounds.add(currentOutbound)
                pastOutbound = currentOutbound
                pastEntity = proxyEntity
            }

            // If this is proxy set, migrate it to the new list's top.
            // Then the structure is clear and make sure TAG_PROXY is the first.
            if (isProxySet) {
                outbounds.add(
                    outbounds.size - profileList.size,
                    outbounds.removeAt(outbounds.lastIndex),
                )
            }

            trafficMap[chainTagOut] = chainTrafficSet.toList()
            return chainTagOut
        }

        // build outbounds
        tagMap[proxy.id] = buildChain(0, proxy)
        // build outbounds from route item
        extraProxies.forEach { (key, p) ->
            tagMap[key] = buildChain(key, p)
        }

        // apply user rules
        for (rule in extraRules) {
            if (rule.packages.isNotEmpty()) {
                PackageCache.awaitLoadSync()
            }
            val uidList = rule.packages.mapNotNullTo(LinkedHashSet()) {
                if (!isVPN) {
                    Toast.makeText(
                        app,
                        app.getString(R.string.route_need_vpn, rule.displayName()),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                PackageCache[it]?.takeIf { uid -> uid >= 1000 }
            }.toList()

            if (rule.packages.isNotEmpty() && uidList.isEmpty()) {
                // all packages in the rule are not installed
                // the rule would be never hit, skipping
                continue
            }

            val ruleObj = Rule_Default().apply {
                action = SingBoxOptions.ACTION_ROUTE
                if (uidList.isNotEmpty()) {
                    PackageCache.awaitLoadSync()
                    user_id = uidList
                }
                var domainList: List<RuleItem> = listOf()
                if (rule.domains.isNotBlank()) {
                    domainList = RuleItem.parseRules(rule.domains.listByLineOrComma(), true)
                    makeCommonRule(domainList, false)
                }
                if (rule.ip.isNotBlank()) {
                    makeCommonRule(RuleItem.parseRules(rule.ip.listByLineOrComma(), false), true)
                }
                if (rule.port.isNotBlank()) {
                    port = mutableListOf()
                    port_range = mutableListOf()
                    rule.port.listByLineOrComma().mapX {
                        if (it.contains(":")) {
                            port_range.add(it)
                        } else {
                            it.toIntOrNull()?.apply { port.add(this) }
                        }
                    }
                }
                if (rule.sourcePort.isNotBlank()) {
                    source_port = mutableListOf()
                    source_port_range = mutableListOf()
                    rule.sourcePort.listByLineOrComma().mapX {
                        if (it.contains(":")) {
                            source_port_range.add(it)
                        } else {
                            it.toIntOrNull()?.apply { source_port.add(this) }
                        }
                    }
                }
                if (rule.network.isNotBlank()) {
                    network = listOf(rule.network)
                }
                if (rule.source.isNotBlank()) {
                    val sourceIPs = mutableListOf<String>()
                    for (source in rule.source.listByLineOrComma()) {
                        if (source == RuleItem.CONTENT_PRIVATE) {
                            source_ip_is_private = true
                        } else {
                            sourceIPs.add(source)
                        }
                    }
                    if (sourceIPs.isNotEmpty()) source_ip_cidr = sourceIPs
                }
                if (rule.protocol.isNotEmpty()) {
                    protocol = rule.protocol.toList()
                }
                if (rule.clientType.isNotEmpty()) {
                    client = rule.clientType.toList()
                }
                if (rule.ssid.isNotBlank()) {
                    wifi_ssid = rule.ssid.listByLineOrComma()
                }
                if (rule.bssid.isNotBlank()) {
                    wifi_bssid = rule.bssid.listByLineOrComma()
                }
                if (rule.clashMode.isNotBlank()) {
                    clash_mode = rule.clashMode
                }
                if (rule.networkType.isNotEmpty()) {
                    network_type = rule.networkType.toList()
                }
                if (rule.networkIsExpensive) {
                    network_is_expensive = true
                }

                fun makeDnsRuleObj(): DNSRule_Default {
                    return DNSRule_Default().apply {
                        if (uidList.isNotEmpty()) user_id = uidList
                        val ips = RuleItem.parseRules(rule.ip.listByLineOrComma(), false)
                        makeCommonRule((domainList + ips).filter { it.dns })
                    }
                }

                when (val ruleAction = rule.action) {
                    "", SingBoxOptions.ACTION_ROUTE -> {
                        action = SingBoxOptions.ACTION_ROUTE

                        when (val outID = rule.outbound) {
                            RuleEntity.OUTBOUND_DIRECT -> {
                                userDNSRuleList.add(makeDnsRuleObj().apply {
                                    server = TAG_DNS_DIRECT
                                })
                                outbound = TAG_DIRECT
                            }

                            RuleEntity.OUTBOUND_PROXY -> {
                                if (useFakeDns) userDNSRuleList.add(makeDnsRuleObj().apply {
                                    server = TAG_DNS_FAKE
                                    inbound = listOf(TAG_TUN)
                                    query_type = FAKE_DNS_QUERY_TYPE
                                })
                                userDNSRuleList.add(makeDnsRuleObj().apply {
                                    server = TAG_DNS_REMOTE
                                })
                                outbound = TAG_PROXY
                            }

                            RuleEntity.OUTBOUND_BLOCK -> {
                                userDNSRuleList.add(makeDnsRuleObj().apply {
                                    action = SingBoxOptions.ACTION_REJECT
                                })
                                action = SingBoxOptions.ACTION_REJECT
                            }

                            else -> outbound = if (outID == proxy.id) {
                                TAG_PROXY
                            } else {
                                tagMap[outID] ?: ""
                            }
                        }
                    }

                    SingBoxOptions.ACTION_ROUTE_OPTIONS -> {
                        action = ruleAction

                        override_address = rule.overrideAddress.blankAsNull()
                        override_port = rule.overridePort.takeIf { it > 0 }
                        if (rule.tlsFragment) {
                            tls_fragment = true
                            tls_fragment_fallback_delay =
                                rule.tlsFragmentFallbackDelay.blankAsNull()
                        }
                        if (rule.tlsRecordFragment) {
                            tls_record_fragment = true
                        }
                    }

                    SingBoxOptions.ACTION_RESOLVE -> {
                        action = ruleAction

                        strategy = rule.resolveStrategy
                        if (rule.resolveDisableCache) {
                            disable_cache = true
                        }
                        rewrite_ttl = rule.resolveRewriteTTL.takeIf { it >= 0 }
                        client_subnet = rule.resolveClientSubnet.blankAsNull()
                    }

                    SingBoxOptions.ACTION_SNIFF -> {
                        action = ruleAction

                        timeout = rule.sniffTimeout.blankAsNull()
                        sniffer = rule.sniffers.takeIf { it.isNotEmpty() }?.toList()
                    }

                    SingBoxOptions.ACTION_HIJACK_DNS -> {
                        action = ruleAction
                    }

                    else -> error("unsupported action: $ruleAction")
                }

            }

            if (!ruleObj.checkEmpty()) {
                // Empty or "route"
                val needOutbound = when (ruleObj.action) {
                    null, "", SingBoxOptions.ACTION_ROUTE -> true
                    else -> false
                }
                if (needOutbound && ruleObj.outbound.isNullOrBlank()) {
                    Toast.makeText(
                        app,
                        "Warning: " + rule.displayName() + ": A non-existent outbound was specified.",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    route.rules.add(ruleObj)
                }
            } else if (ruleObj.action != SingBoxOptions.ACTION_ROUTE) {
                route.rules.add(ruleObj)
            } else if (rule.domains.isBlank() && rule.ip.isBlank()) {
                route.rules.add(ruleObj)
            }
        }

        outbounds.add(Outbound_DirectOptions().apply {
            tag = TAG_DIRECT
            type = SingBoxOptions.TYPE_DIRECT
            domain_resolver = DomainResolveOptions().apply {
                server = if (forTest) {
                    TAG_DNS_LOCAL
                } else {
                    TAG_DNS_DIRECT
                }
                strategy = defaultOr(
                    DataStore.domainStrategyForDirect.replace("auto", "").blankAsNull(),
                    { defaultStrategy },
                )
            }

            if (!forTest) {
                if (networkPreferredInterfaces.isNotEmpty()) {
                    network_type = networkPreferredInterfaces
                    network_strategy = mapNetworkInterfaceStrategy(networkInterfaceStrategy)
                }
            }
        }.asMap())
        outbounds.add(Outbound().apply {
            tag = TAG_BLOCK
            type = SingBoxOptions.TYPE_BLOCK
        }.asMap())

        if (!forTest) localDNSPort?.let {
            inbounds.add(0, Inbound_DirectOptions().apply {
                type = SingBoxOptions.TYPE_DIRECT
                tag = TAG_DNS_IN
                listen = bind
                listen_port = it
                override_address = "8.8.8.8"
                override_port = 53
            })
        }

        // Bypass Lookup for the first profile
        bypassDNSBeans.forEach {
            var serverAddr = it.serverAddress

            if (it is ConfigBean) {
                val config = it.config.toJsonMap()
                config["server"]?.let { server ->
                    serverAddr = server.toString()
                }
            }

            if (!serverAddr.isIpAddress()) {
                domainListDNSDirectForce.add(serverAddr)
            }
        }

        remoteDns.forEach {
            var address = it
            if (address.contains("://")) {
                address = address.substringAfter("://")
            }
            try {
                Libcore.parseURL("https://$address").apply {
                    if (!host.isIpAddress()) {
                        domainListDNSDirectForce.add(host)
                    }
                }
            } catch (_: Exception) {
            }
        }

        // remote dns obj
        remoteDns.firstOrNull()?.let {
            dns.servers.add(
                buildDNSServer(
                    it,
                    TAG_PROXY,
                    TAG_DNS_REMOTE,
                    DomainResolveOptions().apply {
                        server = TAG_DNS_DIRECT
                    },
                )
            )
        } ?: error("missing remote DNS")

        // add directDNS objects here
        directDNS.firstOrNull()?.let {
            dns.servers.add(
                buildDNSServer(
                    it,
                    null,
                    TAG_DNS_DIRECT,
                    DomainResolveOptions().apply {
                        server = TAG_DNS_LOCAL
                    }
                ))
        } ?: error("missing direct DNS")

        // underlyingDns
        dns.servers.add(NewDNSServerOptions_LocalDNSServerOptions().apply {
            tag = TAG_DNS_LOCAL
            type = SingBoxOptions.DNS_TYPE_LOCAL
        })

        // dns object user rules
        userDNSRuleList.forEach {
            if (!it.checkEmpty()) dns.rules.add(it)
        }

        if (forTest) {
            // Always use system DNS for urlTest
            dns.servers = listOf(NewDNSServerOptions_LocalDNSServerOptions().apply {
                tag = TAG_DNS_LOCAL
                type = SingBoxOptions.DNS_TYPE_LOCAL
            })
            dns.rules = mutableListOf()
        } else {
            // clash mode
            route.rules.add(0, Rule_Default().apply {
                clash_mode = RuleEntity.MODE_GLOBAL
                outbound = TAG_PROXY
            })
            route.rules.add(0, Rule_Default().apply {
                clash_mode = RuleEntity.MODE_DIRECT
                outbound = TAG_DIRECT
            })
            route.rules.add(0, Rule_Default().apply {
                clash_mode = RuleEntity.MODE_BLOCK
                action = SingBoxOptions.ACTION_REJECT
            })

            // built-in DNS rules
            val builtInDNSRule = localDNSPort?.let {
                Rule_Logical().also {
                    it.type = SingBoxOptions.TYPE_LOGICAL
                    it.mode = SingBoxOptions.LOGICAL_OR
                    it.rules = listOf(
                        Rule_Default().apply {
                            inbound = listOf(TAG_DNS_IN)
                        },
                        Rule_Default().apply {
                            ip_cidr = listOf(PRIVATE_VLAN4_ROUTER, PRIVATE_VLAN6_ROUTER)
                        },
                    )
                    it.action = SingBoxOptions.ACTION_HIJACK_DNS
                }
            } ?: Rule_Default().apply {
                ip_cidr = listOf(PRIVATE_VLAN4_ROUTER, PRIVATE_VLAN6_ROUTER)
                action = SingBoxOptions.ACTION_HIJACK_DNS
            }
            route.rules.add(0, builtInDNSRule)

            if (DataStore.bypassLanInCore) {
                route.rules.add(Rule_Default().apply {
                    outbound = TAG_DIRECT
                    ip_is_private = true
                })
            }
            // block mcast
            route.rules.add(Rule_Default().apply {
                ip_cidr = listOf("224.0.0.0/3", "ff00::/8")
                source_ip_cidr = listOf("224.0.0.0/3", "ff00::/8")
                action = SingBoxOptions.ACTION_REJECT
            })

            // FakeDNS obj
            if (useFakeDns) {
                dns.servers.add(NewDNSServerOptions_FakeIPDNSServerOptions().apply {
                    type = SingBoxOptions.DNS_TYPE_FAKEIP
                    tag = TAG_DNS_FAKE
                    inet4_range = "198.18.0.0/15"
                    inet6_range = "fc00::/18"
                })
                dns.rules.add(DNSRule_Default().apply {
                    inbound = listOf(TAG_TUN)
                    server = TAG_DNS_FAKE
                    disable_cache = true
                    query_type = FAKE_DNS_QUERY_TYPE
                })
            }

            dnsHosts?.let {
                dns.servers.add(NewDNSServerOptions_HostsDNSServerOptions().apply {
                    type = SingBoxOptions.DNS_TYPE_HOSTS
                    tag = TAG_DNS_HOSTS
                    predefined = it
                })
                dns.rules.add(0, DNSRule_Default().apply {
                    server = TAG_DNS_HOSTS
                    ip_accept_any = true
                })
            }

            // clash mode
            dns.rules.add(0, DNSRule_Default().apply {
                clash_mode = RuleEntity.MODE_GLOBAL
                server = TAG_DNS_REMOTE
            })
            dns.rules.add(0, DNSRule_Default().apply {
                clash_mode = RuleEntity.MODE_DIRECT
                server = TAG_DNS_DIRECT
            })
            dns.rules.add(0, DNSRule_Default().apply {
                clash_mode = RuleEntity.MODE_BLOCK
                action = SingBoxOptions.ACTION_REJECT
            })

            if (domainListDNSDirectForce.isNotEmpty()) {
                dns.rules.add(0, DNSRule_Default().apply {
                    domain = domainListDNSDirectForce.distinct()
                    server = TAG_DNS_DIRECT
                })
            }

            // https://github.com/juicity/juicity/issues/140
            // FIXME: improve this workaround or remove it when juicity fix it.
            if (hasJuicity && useFakeDns) route.rules.add(0, Rule_Default().apply {
                action = SingBoxOptions.ACTION_RESOLVE
                network = listOf(SingBoxOptions.NetworkUDP)
            })

            route.final_ = TAG_PROXY
        }
        if (!forTest) dns.final_ = TAG_DNS_REMOTE

        // mapping for plugin
        for ((serverInfo, inboundTags) in mappingOverride) {
            route.rules.add(0, Rule_Default().apply {
                action = SingBoxOptions.ACTION_ROUTE_OPTIONS
                inbound = inboundTags
                override_address = serverInfo.first
                override_port = serverInfo.second
            })
        }

        var ruleSetResource: String? = null
        var geositeLink: String? = null
        var geoipLink: String? = null
        if (forExport) {
            // "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-cn.srs"
            val pathPrefix = "https://raw.githubusercontent.com"
            val provider = DataStore.rulesProvider

            val normalBranch = "rule-set"
            val geoipBranch = normalBranch
            val geositeBranch = if (RuleProvider.hasUnstableBranch(provider)) {
                "rule-set-unstable"
            } else {
                normalBranch
            }

            when (provider) {
                RuleProvider.OFFICIAL -> {
                    geositeLink = "$pathPrefix/SagerNet/sing-geosite/$geositeBranch"
                    geoipLink = "$pathPrefix/SagerNet/sing-geoip/$geoipBranch"
                }

                RuleProvider.LOYALSOLDIER -> {
                    geositeLink = "$pathPrefix/xchacha20-poly1305/sing-geosite/$geositeBranch"
                    geoipLink = "$pathPrefix/xchacha20-poly1305/sing-geoip/$geoipBranch"
                }

                RuleProvider.CHOCOLATE4U -> {
                    geositeLink = "$pathPrefix/Chocolate4U/sing-geosite/$geositeBranch"
                    geoipLink = "$pathPrefix/Chocolate4U/sing-geoip/$geoipBranch"
                }

                RuleProvider.CUSTOM -> {} // Can't generate.
            }
        }
        if (geositeLink == null) {
            ruleSetResource = app.externalAssets.absolutePath + "/geo"
        }
        buildRuleSets(geoipLink, geositeLink, ruleSetResource)
        partitionEndpoints()
    }.let {
        ConfigBuildResult(
            gson.toJson(it.asMap().apply {
                optionsToMerge.blankAsNull()?.toJsonMap()?.let { jsonMap ->
                    mergeJson(jsonMap, this)
                }
            }),
            externalIndexMap,
            trafficMap,
            proxy.id,
            tagMap.reverse(),
        )
    }

}

/**
 * Partition outbounds and endpoints.
 */
fun MyOptions.partitionEndpoints() {
    val pair = outbounds.partition { isEndpoint(it["type"].toString()) }
    endpoints = pair.first
    outbounds = pair.second
}

fun mapNetworkInterfaceStrategy(strategy: Int): String = when (strategy) {
    NetworkInterfaceStrategy.DEFAULT -> SingBoxOptions.STRATEGY_DEFAULT
    NetworkInterfaceStrategy.HYBRID -> SingBoxOptions.STRATEGY_HYBRID
    NetworkInterfaceStrategy.FALLBACK -> SingBoxOptions.STRATEGY_FALLBACK
    else -> throw IllegalStateException()
}
