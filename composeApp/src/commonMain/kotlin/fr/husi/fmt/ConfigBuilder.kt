package fr.husi.fmt

import fr.husi.Key
import fr.husi.NetworkInterfaceStrategy
import fr.husi.RuleProvider
import fr.husi.TunImplementation
import fr.husi.bg.VpnConstants
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyEntity
import fr.husi.database.ProxyEntity.Companion.TYPE_CONFIG
import fr.husi.database.RuleEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.ConfigBuildResult.IndexEntity
import fr.husi.fmt.SingBoxOptions.CacheFileOptions
import fr.husi.fmt.SingBoxOptions.DNSRule_Default
import fr.husi.fmt.SingBoxOptions.DomainResolveOptions
import fr.husi.fmt.SingBoxOptions.ExperimentalOptions
import fr.husi.fmt.SingBoxOptions.Inbound_DirectOptions
import fr.husi.fmt.SingBoxOptions.Inbound_HTTPMixedOptions
import fr.husi.fmt.SingBoxOptions.Inbound_TunOptions
import fr.husi.fmt.SingBoxOptions.LogOptions
import fr.husi.fmt.SingBoxOptions.MyDNSOptions
import fr.husi.fmt.SingBoxOptions.MyOptions
import fr.husi.fmt.SingBoxOptions.MyRouteOptions
import fr.husi.fmt.SingBoxOptions.NTPOptions
import fr.husi.fmt.SingBoxOptions.NewDNSServerOptions_FakeIPDNSServerOptions
import fr.husi.fmt.SingBoxOptions.NewDNSServerOptions_HostsDNSServerOptions
import fr.husi.fmt.SingBoxOptions.NewDNSServerOptions_LocalDNSServerOptions
import fr.husi.fmt.SingBoxOptions.NewDNSServerOptions_MDNSDNSServerOptions
import fr.husi.fmt.SingBoxOptions.OptimisticDNSOptions
import fr.husi.fmt.SingBoxOptions.Outbound
import fr.husi.fmt.SingBoxOptions.Outbound_DirectOptions
import fr.husi.fmt.SingBoxOptions.Outbound_SOCKSOptions
import fr.husi.fmt.SingBoxOptions.Rule_Default
import fr.husi.fmt.SingBoxOptions.Rule_Logical
import fr.husi.fmt.SingBoxOptions.User
import fr.husi.fmt.anytls.AnyTLSBean
import fr.husi.fmt.anytls.buildSingBoxOutboundAnyTLSBean
import fr.husi.fmt.config.ConfigBean
import fr.husi.fmt.direct.DirectBean
import fr.husi.fmt.direct.buildSingBoxOutboundDirectBean
import fr.husi.fmt.hysteria.HysteriaBean
import fr.husi.fmt.hysteria.buildSingBoxOutboundHysteriaBean
import fr.husi.fmt.internal.ChainBean
import fr.husi.fmt.internal.ProxySetBean
import fr.husi.fmt.internal.buildSingBoxOutboundProxySetBean
import fr.husi.fmt.juicity.JuicityBean
import fr.husi.fmt.juicity.buildSingBoxOutboundJuicityBean
import fr.husi.fmt.mieru.MieruBean
import fr.husi.fmt.naive.NaiveBean
import fr.husi.fmt.naive.buildSingBoxOutboundNaiveBean
import fr.husi.fmt.openconnect.OpenConnectBean
import fr.husi.fmt.openconnect.buildSingBoxEndpointOpenConnectBean
import fr.husi.fmt.openvpn.OpenVPNBean
import fr.husi.fmt.openvpn.buildSingBoxEndpointOpenVPNBean
import fr.husi.fmt.shadowquic.ShadowQUICBean
import fr.husi.fmt.shadowsocks.ShadowsocksBean
import fr.husi.fmt.shadowsocks.buildSingBoxOutboundShadowsocksBean
import fr.husi.fmt.shadowtls.ShadowTLSBean
import fr.husi.fmt.shadowtls.buildSingBoxOutboundShadowTLSBean
import fr.husi.fmt.snell.SnellBean
import fr.husi.fmt.snell.buildSingBoxOutboundSnellBean
import fr.husi.fmt.socks.SOCKSBean
import fr.husi.fmt.socks.buildSingBoxOutboundSocksBean
import fr.husi.fmt.ssh.SSHBean
import fr.husi.fmt.ssh.buildSingBoxOutboundSSHBean
import fr.husi.fmt.trusttunnel.TrustTunnelBean
import fr.husi.fmt.trusttunnel.buildSingBoxOutboundTrustTunnelBean
import fr.husi.fmt.tuic.TuicBean
import fr.husi.fmt.tuic.buildSingBoxOutboundTuicBean
import fr.husi.fmt.v2ray.StandardV2RayBean
import fr.husi.fmt.v2ray.buildSingBoxOutboundStandardV2RayBean
import fr.husi.fmt.wireguard.WireGuardBean
import fr.husi.fmt.wireguard.buildSingBoxEndpointWireGuardBean
import fr.husi.ktx.JSONMap
import fr.husi.ktx.asKxsMap
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.defaultOr
import fr.husi.ktx.invariantPathString
import fr.husi.ktx.isIpAddress
import fr.husi.ktx.kxs
import fr.husi.ktx.listByLineOrComma
import fr.husi.ktx.mergeJson
import fr.husi.ktx.mkPort
import fr.husi.ktx.reverse
import fr.husi.ktx.serverAddressDomainStrategy
import fr.husi.ktx.showToast
import fr.husi.ktx.toJsonElementKxs
import fr.husi.ktx.toJsonMapKxs
import fr.husi.ktx.toJsonObjectKxs
import fr.husi.libcore.Libcore
import fr.husi.logLevelString
import fr.husi.platform.PlatformInfo
import fr.husi.repository.resolveRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

// Inbound
const val TAG_MIXED = "mixed-in"
const val TAG_TUN = "tun-in"
const val TAG_DNS_IN = "dns-in" // strategic

// Outbound
const val TAG_DIRECT = "direct"
const val TAG_BLOCK = "block"
const val TAG_BRIDGE = "bridge"

// DNS
const val TAG_DNS_REMOTE = "dns-remote"
const val TAG_DNS_DIRECT = "dns-direct"
const val TAG_DNS_LOCAL = "dns-local"
const val TAG_DNS_FAKE = "dns-fake"
const val TAG_DNS_HOSTS = "dns-hosts"
const val TAG_DNS_MDNS = "dns-mdns"
const val TAG_SERVICE_ANCHOR = "service-anchor"

const val LOCALHOST4 = "127.0.0.1"
private const val ANCHOR_PORT = 45947

// For a certain version schema, maybe we should use [typebox](https://github.com/jiang-zhexin/typebox) ?
const val CONFIG_SCHEMA_URL = "https://sing-box.sagernet.org/schema.json"

val FAKE_DNS_QUERY_TYPE get() = listOf("A", "AAAA")

class ConfigBuildResult(
    val mainTag: String,
    var config: String,
    var externalIndex: List<IndexEntity>,
    var trafficMap: Map<String, List<ProxyEntity>>,
    val tagToID: Map<String, Long>,
) {
    data class IndexEntity(var chain: LinkedHashMap<Int, ProxyEntity>)
}

fun buildConfig(
    proxy: ProxyEntity, forTest: Boolean = false, forExport: Boolean = false,
): ConfigBuildResult {
    val repository = resolveRepository()

    if (proxy.type == TYPE_CONFIG) {
        val bean = proxy.configBean!!
        if (bean.type == ConfigBean.TYPE_CONFIG) {
            val tagProxy = bean.displayName()
            return ConfigBuildResult(
                tagProxy,
                bean.config,
                listOf(),
                mapOf(tagProxy to listOf(proxy)),
                mapOf(tagProxy to proxy.id),
            )
        }
    }

    val trafficMap = HashMap<String, List<ProxyEntity>>()
    val tagMap = HashMap<Long, String>()
    val optionsToMerge = proxy.requireBean().customConfigJson

    fun ProxyEntity.resolveChainInternal(): MutableList<ProxyEntity> {
        return when (val bean = requireBean()) {
            is ChainBean -> {
                val beans = runBlocking { SagerDatabase.proxyDao.getEntities(bean.proxies) }
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
                    ProxySetBean.TYPE_LIST -> runBlocking { SagerDatabase.proxyDao.getEntities(bean.proxies) }
                    ProxySetBean.TYPE_GROUP -> runBlocking {
                        SagerDatabase.proxyDao.getByGroup(bean.groupId).first()
                    }

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
        val thisGroup = runBlocking { SagerDatabase.groupDao.getById(groupId).first() }
        val frontProxy =
            thisGroup?.frontProxy?.let { runBlocking { SagerDatabase.proxyDao.getById(it) } }
        val landingProxy =
            thisGroup?.landingProxy?.let { runBlocking { SagerDatabase.proxyDao.getById(it) } }
        val list = resolveChainInternal()
        if (frontProxy != null) {
            list.addAll(frontProxy.resolveChainInternal())
        }
        if (landingProxy != null) {
            list.addAll(0, landingProxy.resolveChainInternal())
        }
        return list
    }

    val logLevel = DataStore.logLevel
    val extraRules = if (forTest) {
        emptyList()
    } else runBlocking {
        ProfileManager.enabledRules().first()
    }
    val extraProxies =
        if (forTest) mapOf() else runBlocking {
            SagerDatabase.proxyDao.getEntities(
                extraRules.mapNotNull { rule ->
                    rule.outbound.takeIf { it > 0 && it != proxy.id }
                }.toHashSet().toList(),
            ).associateBy { it.id }
        }
    val userDNSRuleList = mutableListOf<JSONMap>()
    val domainListDNSDirectForce = mutableSetOf<String>()
    val bypassDNSBeans = hashSetOf<AbstractBean>()
    val isVPN = DataStore.serviceMode == Key.MODE_VPN
    val bind = if (!forTest && DataStore.allowAccess) "0.0.0.0" else LOCALHOST4
    val remoteDns = DataStore.remoteDns.split("\n")
        .mapNotNull { dns -> dns.trim().takeIf { it.isNotBlank() && !it.startsWith("#") } }
    val directDNS = DataStore.directDns.split("\n")
        .mapNotNull { dns -> dns.trim().takeIf { it.isNotBlank() && !it.startsWith("#") } }
    val mDNSInterfaces by lazy {
        DataStore.mDNS.blankAsNull()?.listByLineOrComma()?.takeIf { it.isNotEmpty() }
    }
    val localDNSPort = DataStore.localDNSPort.takeIf { it > 0 }
    val useFakeDns by lazy { !forTest && DataStore.enableFakeDns }
    val fakeDNSForAll by lazy { useFakeDns && DataStore.fakeDNSForAll }
    val dnsHosts by lazy {
        DataStore.dnsHosts.blankAsNull()?.lineSequence()
            ?.mapNotNullTo(mutableListOf()) { line ->
                val trimmed = line.trim()
                // Promote the compatibility.
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNullTo null
                val tokens = trimmed.split("\\s+".toRegex()) // Handle direct copy from host file.
                if (tokens.size < 2) return@mapNotNullTo null
                val host = tokens[0]
                val ips = tokens.drop(1).toMutableList()
                host to ips
            }
            ?.toMap()
            ?.takeIf { it.isNotEmpty() }
    }
    val externalIndexMap = ArrayList<IndexEntity>()
    val networkStrategy = DataStore.networkStrategy
    val networkInterfaceStrategy = DataStore.networkInterfaceType
    val networkPreferredInterfaces = DataStore.networkPreferredInterfaces.toList()
    val defaultStrategy = DataStore.networkStrategy.blankAsNull()
    val serverDomainStrategy = serverAddressDomainStrategy()
    val disableTcpKeepAlive = DataStore.disableTcpKeepAlive
    val tcpKeepAliveIdle = DataStore.tcpKeepAliveIdle.blankAsNull()
    val tcpKeepAliveInterval = DataStore.tcpKeepAliveInterval.blankAsNull()
    lateinit var mainTag: String

    val readableNames = mutableSetOf(TAG_DIRECT, TAG_BLOCK)

    // server+port:tags
    // This structure may reduce rules when multiple rules share the same server+port.
    val mappingOverride: LinkedHashMap<Pair<String, Int>, MutableList<String>> =
        LinkedHashMap()

    return MyOptions().apply {
        `$schema` = CONFIG_SCHEMA_URL
        if (!forTest) experimental = ExperimentalOptions().apply {
            if (!forExport) {
                if (DataStore.isExpert) DataStore.debugListen.blankAsNull()?.let {
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

            if (!server!!.isIpAddress()) {
                domainListDNSDirectForce.add(server!!)
            }
        }

        dns = MyDNSOptions().apply {
            servers = mutableListOf()
            rules = mutableListOf()
            if (!forTest) {
                DataStore.dnsOptimisticCache.blankAsNull()?.let { it ->
                    optimistic = OptimisticDNSOptions().apply {
                        enabled = true
                        timeout = it
                    }
                }
            }
        }

        inbounds = mutableListOf()

        if (!forTest && PlatformInfo.isAndroid && DataStore.allowAccess) {
            DataStore.anchorSSID.blankAsNull()?.let { allowedSSIDs ->
                services = mutableListOf(
                    SingBoxOptions.Service_AnchorOptions().apply {
                        type = TAG_SERVICE_ANCHOR
                        tag = TAG_SERVICE_ANCHOR
                        listen = bind
                        listen_port = ANCHOR_PORT
                        dns_port = localDNSPort ?: 0
                        device_name = anchorDeviceName
                        socks_port = DataStore.mixedPort
                        allowed_ssids = allowedSSIDs.lines().toMutableList()
                    },
                )
            }
        }

        if (!forTest) {
            if (isVPN) inbounds!!.add(
                Inbound_TunOptions().apply {
                    type = SingBoxOptions.TYPE_TUN
                    tag = TAG_TUN
                    stack = when (DataStore.tunImplementation) {
                        TunImplementation.GVISOR -> "gvisor"
                        TunImplementation.SYSTEM -> "system"
                        else -> "mixed"
                    }
                    mtu = DataStore.mtu
                    // TUN_DNS_MODE_HIJACK will not search the process,
                    // so we should use custom rule to hijack DNS instead of auto hijack from Tun.
                    dns_mode = SingBoxOptions.TUN_DNS_MODE_NATIVE
                    applyPlatformConfig()
                    when (networkStrategy) {
                        SingBoxOptions.STRATEGY_IPV4_ONLY -> {
                            dns_address = mutableListOf(VpnConstants.PRIVATE_VLAN4_ROUTER)
                            address = mutableListOf(VpnConstants.PRIVATE_VLAN4_CLIENT + "/28")
                        }

                        SingBoxOptions.STRATEGY_IPV6_ONLY -> {
                            dns_address = mutableListOf(VpnConstants.PRIVATE_VLAN6_ROUTER)
                            address = mutableListOf(VpnConstants.PRIVATE_VLAN6_CLIENT + "/126")
                        }

                        else -> {
                            dns_address = mutableListOf(
                                VpnConstants.PRIVATE_VLAN4_ROUTER,
                                VpnConstants.PRIVATE_VLAN6_ROUTER,
                            )
                            address = mutableListOf(
                                VpnConstants.PRIVATE_VLAN4_CLIENT + "/28",
                                VpnConstants.PRIVATE_VLAN6_CLIENT + "/126",
                            )
                        }
                    }
                },
            )
            inbounds!!.add(
                Inbound_HTTPMixedOptions().apply {
                    type = SingBoxOptions.TYPE_MIXED
                    tag = TAG_MIXED
                    listen = bind
                    listen_port = DataStore.mixedPort
                    if (!PlatformInfo.isAndroid) {
                        if (DataStore.appendHttpProxy) {
                            set_system_proxy = true
                        }
                    }
                    if (DataStore.inboundUsername.isNotBlank() || DataStore.inboundPassword.isNotBlank()) {
                        users = mutableListOf(
                            User().apply {
                                username = DataStore.inboundUsername
                                password = DataStore.inboundPassword
                            },
                        )
                    }
                },
            )
        }

        outbounds = mutableListOf()

        // init routing object
        route = MyRouteOptions().apply {
            auto_detect_interface = true
            rules = mutableListOf()
            rule_set = mutableListOf()
            // Forced
            // https://github.com/SagerNet/sing-box/commit/4b1b00a4f6729a027a653f417cda0701c6a32934#diff-d66a2caeeac5651dd693f6c05456599c9896a5def2619a159e05a76786bb16c7
            // if (!forTest && DataStore.forcedSearchProcess) find_process = true
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
            var pastEntity: ProxyEntity? = null
            var pastChainEntity: ProxyEntity? = null
            val externalChainMap = LinkedHashMap<Int, ProxyEntity>()
            externalIndexMap.add(IndexEntity(externalChainMap))

            // chainTagOut: v2ray outbound tag for this chain
            var chainTagOut = ""
            val chainTag = "c-$chainId"

            val isProxySet = entity.type == ProxyEntity.TYPE_PROXY_SET
            val outboundsByTag = HashMap<String, JSONMap>()
            val mappingInboundTags = HashMap<Long, String>()

            fun ProxyEntity.resolveProxySetMembers(): List<ProxyEntity> {
                if (type != ProxyEntity.TYPE_PROXY_SET) return emptyList()
                val chain = resolveChainInternal()
                return if (chain.isEmpty()) emptyList() else chain.dropLast(1)
            }

            fun addReadableName(name: String): String {
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

            val reservedTags = HashMap<Long, String>()

            fun reserveTag(proxyEntity: ProxyEntity): String {
                reservedTags[proxyEntity.id]?.let { return it }
                val tag = addReadableName(proxyEntity.displayName())
                reservedTags[proxyEntity.id] = tag
                return tag
            }

            val proxySetMemberIds = LinkedHashSet<Long>().apply {
                for (proxyEntity in profileList) {
                    if (proxyEntity.requireBean() is ProxySetBean) {
                        for (member in proxyEntity.resolveProxySetMembers()) {
                            add(member.id)
                        }
                    }
                }
            }

            fun JSONMap.detourTo(tag: String) {
                this["detour"] = tag
                remove("domain_resolver")
            }

            fun connectChainNode(previousEntity: ProxyEntity, currentTag: String) {
                if (previousEntity.requireBean() is ProxySetBean) {
                    for (member in previousEntity.resolveProxySetMembers()) {
                        val memberTag = checkNotNull(reservedTags[member.id])
                        outboundsByTag[memberTag]?.detourTo(currentTag)
                    }
                    return
                }
                if (previousEntity.needExternal()) {
                    route!!.rules!!.add(
                        Rule_Default().apply {
                            inbound = mutableListOf(
                                checkNotNull(mappingInboundTags[previousEntity.id]),
                            )
                            outbound = currentTag
                        }.asKxsMap(),
                    )
                } else {
                    val previousTag = checkNotNull(reservedTags[previousEntity.id])
                    outboundsByTag[previousTag]?.detourTo(currentTag)
                }
            }

            val lastChainIndex = profileList.indexOfLast { proxyEntity ->
                val bean = proxyEntity.requireBean()
                !proxySetMemberIds.contains(proxyEntity.id) || bean is ProxySetBean
            }

            profileList.forEachIndexed { index, proxyEntity ->
                val bean = proxyEntity.requireBean()
                val isProxySetMember =
                    proxySetMemberIds.contains(proxyEntity.id) && bean !is ProxySetBean
                val isChainNode = !isProxySetMember

                // first profile set as global
                if (index == profileList.lastIndex) {
                    bypassDNSBeans.add(proxyEntity.requireBean())
                }

                val tagOut = reserveTag(proxyEntity)

                // chain rules
                if (!isProxySet) {
                    if (isChainNode) {
                        if (pastChainEntity != null) {
                            connectChainNode(pastChainEntity, tagOut)
                        } else {
                            // index == 0 means last profile in chain / not chain
                            chainTagOut = tagOut
                            if (chainId == 0L) {
                                mainTag = tagOut
                            }
                        }
                    }
                } else {
                    if (index == profileList.lastIndex) {
                        chainTagOut = tagOut
                        if (chainId == 0L) {
                            mainTag = tagOut
                        }
                    }
                }

                if (proxyEntity.needExternal()) { // external outbound
                    val localPort = mkPort()
                    externalChainMap[localPort] = proxyEntity
                    currentOutbound = Outbound_SOCKSOptions().apply {
                        type = SingBoxOptions.TYPE_SOCKS
                        server = LOCALHOST4
                        server_port = localPort
                    }.asKxsMap()
                } else { // internal outbound
                    currentOutbound = when (bean) {
                        is ConfigBean -> bean.config.toJsonMapKxs()

                        is ShadowTLSBean -> // before StandardV2RayBean
                            buildSingBoxOutboundShadowTLSBean(bean).asKxsMap()

                        is StandardV2RayBean -> // http/trojan/vmess/vless
                            buildSingBoxOutboundStandardV2RayBean(bean).asKxsMap()

                        is HysteriaBean -> buildSingBoxOutboundHysteriaBean(bean).asKxsMap()

                        is TuicBean -> buildSingBoxOutboundTuicBean(bean).asKxsMap()

                        is SOCKSBean -> buildSingBoxOutboundSocksBean(bean).asKxsMap()

                        is ShadowsocksBean -> buildSingBoxOutboundShadowsocksBean(bean).asKxsMap()

                        is SnellBean -> buildSingBoxOutboundSnellBean(bean).asKxsMap()

                        is WireGuardBean -> buildSingBoxEndpointWireGuardBean(bean).asKxsMap()

                        is OpenConnectBean -> buildSingBoxEndpointOpenConnectBean(bean).asKxsMap()

                        is OpenVPNBean -> buildSingBoxEndpointOpenVPNBean(bean).asKxsMap()

                        is SSHBean -> buildSingBoxOutboundSSHBean(bean).asKxsMap()

                        is DirectBean -> buildSingBoxOutboundDirectBean(bean).asKxsMap()

                        is AnyTLSBean -> buildSingBoxOutboundAnyTLSBean(bean).asKxsMap()

                        is JuicityBean -> buildSingBoxOutboundJuicityBean(bean).asKxsMap()

                        is NaiveBean -> buildSingBoxOutboundNaiveBean(bean).asKxsMap()

                        is TrustTunnelBean -> buildSingBoxOutboundTrustTunnelBean(bean).asKxsMap()

                        is ProxySetBean -> {
                            val memberTags = LinkedHashSet<String>()
                            for (member in proxyEntity.resolveProxySetMembers()) {
                                memberTags.add(reserveTag(member))
                            }
                            val tags = memberTags.toList().filterNot { it == tagOut }
                            buildSingBoxOutboundProxySetBean(bean, tags).asKxsMap()
                        }

                        else -> throw IllegalStateException("can't reach")
                    }

                    currentOutbound.apply {
                        if (!forTest && bean !is ProxySetBean) {
                            if (disableTcpKeepAlive) {
                                this["disable_tcp_keep_alive"] = true
                            } else {
                                tcpKeepAliveIdle?.let {
                                    this["tcp_keep_alive"] = it
                                }
                                tcpKeepAliveInterval?.let {
                                    this["tcp_keep_alive_interval"] = it
                                }
                            }
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

                        if (pastBean is ShadowQUICBean && pastBean.subProtocol == ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC) {
                            pastBean.extraPaths.lines().forEach {
                                val address = it.substringBeforeLast(":", "").blankAsNull()
                                    ?: return@forEach
                                if (!address.isIpAddress()) {
                                    domainListDNSDirectForce.add(address)
                                }
                            }
                        }
                    }

                    // Set uot here so that naive socks can apply it.
                    // And it is not necessarily to enable it when enabling multiplex.
                    if (bean.needUDPOverTCP && this["multiplex"] == null) {
                        this["udp_over_tcp"] = true
                    }

                    if (bean !is ProxySetBean && (!forTest || serverDomainStrategy != null)) {
                        this["domain_resolver"] = DomainResolveOptions().apply {
                            server = if (forTest) {
                                TAG_DNS_LOCAL
                            } else {
                                TAG_DNS_DIRECT
                            }
                            strategy = serverDomainStrategy
                        }.asKxsMap()
                    }

                    // custom JSON merge
                    bean.customOutboundJson.blankAsNull()?.toJsonMapKxs()?.let {
                        mergeJson(it, currentOutbound)
                    }
                    if (this["detour"] != null) {
                        remove("domain_resolver")
                    }
                }

                currentOutbound["tag"] = tagOut
                tagMap[proxyEntity.id] = tagOut
                outboundsByTag[tagOut] = currentOutbound

                // External proxy need a direct inbound to forward the traffic
                // For external proxy software, their traffic must goes to sing-box to use protected fd.
                bean.finalAddress = bean.serverAddress
                bean.finalPort = bean.serverPort
                var currentInboundTag: String? = null
                if (bean.canMapping && proxyEntity.needExternal()) {
                    // no chain rule and not outbound, so need to set to direct
                    val needDirectRoute = if (isProxySet) {
                        index == profileList.lastIndex
                    } else {
                        !isChainNode || index == lastChainIndex
                    }
                    // mieru protects all its dialers via MIERU_PROTECT_PATH since v3.21.0
                    // (enfein/mieru@666beec), so when it is the first hop it can connect to
                    // the server by itself. Desktop TUN has no protect mechanism and the test
                    // instance relies on the mapping for isolation, keep the mapping there.
                    val canDialDirect = bean is MieruBean &&
                            needDirectRoute &&
                            !forTest &&
                            (PlatformInfo.isAndroid || !isVPN)
                    if (!canDialDirect) {
                        val mappingPort = mkPort()
                        bean.finalAddress = LOCALHOST4
                        bean.finalPort = mappingPort

                        inbounds!!.add(
                            Inbound_DirectOptions().apply {
                                type = SingBoxOptions.TYPE_DIRECT
                                listen = LOCALHOST4
                                listen_port = mappingPort
                                tag = "$chainTag-mapping-${proxyEntity.id}"

                                val pair = Pair(bean.serverAddress, bean.serverPort)
                                mappingOverride.getOrPut(pair) { mutableListOf() }.add(tag!!)

                                currentInboundTag = tag

                                if (needDirectRoute) {
                                    route!!.rules!!.add(
                                        Rule_Default().apply {
                                            inbound = mutableListOf(tag!!)
                                            outbound = TAG_DIRECT
                                        }.asKxsMap(),
                                    )
                                }
                            },
                        )
                    }
                }

                outbounds!!.add(currentOutbound)
                currentInboundTag?.let {
                    mappingInboundTags[proxyEntity.id] = it
                }
                pastEntity = proxyEntity
                if (!isProxySet && isChainNode) {
                    pastChainEntity = proxyEntity
                }
            }

            val trafficEntities = chainTrafficSet.toMutableList()
            if (isProxySet) {
                val chainNodes = profileList.filter { proxyEntity ->
                    val bean = proxyEntity.requireBean()
                    !proxySetMemberIds.contains(proxyEntity.id) || bean is ProxySetBean
                }
                val firstChainEntity = chainNodes.first()
                if (chainId == 0L) {
                    mainTag = checkNotNull(reservedTags[firstChainEntity.id])
                }
                for (nodeIndex in 1 until chainNodes.size) {
                    val currentTag = checkNotNull(reservedTags[chainNodes[nodeIndex].id])
                    connectChainNode(chainNodes[nodeIndex - 1], currentTag)
                }

                val proxySetTag = checkNotNull(reservedTags[entity.id])
                chainTagOut = proxySetTag

                // Keep selector above its children.
                val chunkStart = outbounds!!.size - profileList.size
                val proxySetIndex = outbounds!!.indexOfLast { it["tag"] == proxySetTag }
                if (proxySetIndex in chunkStart..outbounds!!.lastIndex) {
                    outbounds!!.add(chunkStart, outbounds!!.removeAt(proxySetIndex))
                }

                val mainFlowId = firstChainEntity.id
                val mainIndex = trafficEntities.indexOfFirst { it.id == mainFlowId }
                if (mainIndex >= 0 && mainIndex != trafficEntities.lastIndex) {
                    trafficEntities.add(trafficEntities.removeAt(mainIndex))
                }
            }

            trafficMap[chainTagOut] = trafficEntities
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
            val (packageNames, processRules) = parseRuleProcessRules(
                rule.packages,
                defaultToPackage = PlatformInfo.isAndroid,
            )

            val ruleObj = Rule_Default().apply {
                action = SingBoxOptions.ACTION_ROUTE
                if (rule.invert) {
                    invert = true
                }
                if (packageNames.isNotEmpty()) {
                    package_name = packageNames.toMutableList()
                }
                rule.packageNameRegex.blankAsNull()?.let {
                    // Do not use listByLineOrComma for regex
                    package_name_regex = it.split("\n").toMutableList()
                }
                if (processRules.isNotEmpty()) {
                    makeProcessRule(processRules)
                }
                var domainList: List<RuleItem> = listOf()
                var ipList: List<RuleItem> = listOf()
                if (rule.domains.isNotBlank()) {
                    domainList = RuleItem.parseRules(rule.domains.listByLineOrComma(), true)
                    makeCommonRule(domainList, false)
                }
                if (rule.ip.isNotBlank()) {
                    ipList = RuleItem.parseRules(rule.ip.listByLineOrComma(), false)
                    makeCommonRule(ipList, true)
                }
                if (rule.port.isNotBlank()) {
                    port = mutableListOf()
                    port_range = mutableListOf()
                    rule.port.listByLineOrComma().map {
                        if (it.contains(":")) {
                            port_range!!.add(it)
                        } else {
                            it.toIntOrNull()?.apply { port!!.add(this) }
                        }
                    }
                }
                if (rule.sourcePort.isNotBlank()) {
                    source_port = mutableListOf()
                    source_port_range = mutableListOf()
                    rule.sourcePort.listByLineOrComma().map {
                        if (it.contains(":")) {
                            source_port_range!!.add(it)
                        } else {
                            it.toIntOrNull()?.apply { source_port!!.add(this) }
                        }
                    }
                }
                if (rule.network.isNotEmpty()) {
                    network = rule.network.toMutableList()
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
                    protocol = rule.protocol.toMutableList()
                }
                if (rule.clientType.isNotBlank()) {
                    client = rule.clientType.listByLineOrComma().toMutableList()
                }
                if (rule.ssid.isNotBlank()) {
                    wifi_ssid = rule.ssid.listByLineOrComma().toMutableList()
                }
                if (rule.bssid.isNotBlank()) {
                    wifi_bssid = rule.bssid.listByLineOrComma().toMutableList()
                }
                if (rule.clashMode.isNotBlank()) {
                    clash_mode = rule.clashMode
                }
                if (rule.networkType.isNotEmpty()) {
                    network_type = rule.networkType.toMutableList()
                }
                if (rule.networkIsExpensive) {
                    network_is_expensive = true
                }
                if (rule.networkInterfaceAddress.isNotEmpty()) {
                    network_interface_address = rule.networkInterfaceAddress
                        .mapValuesTo(mutableMapOf()) { (_, addresses) ->
                            addresses.listByLineOrComma().toMutableList()
                        }
                }

                fun RuleItem.isResponseOnlyRule(): Boolean {
                    return content == RuleItem.CONTENT_ANY || content == RuleItem.CONTENT_PRIVATE
                }

                val requestDNSRules = domainList.filter { it.dns && !it.isResponseOnlyRule() }
                val responseDNSRules = buildList {
                    addAll(domainList.filter { it.dns && it.isResponseOnlyRule() })
                    addAll(ipList.filter { it.dns })
                }

                fun DNSRule_Default.applyDnsBase(
                    useFakeQueryScope: Boolean = false,
                ): DNSRule_Default {
                    if (rule.invert) {
                        invert = true
                    }
                    if (packageNames.isNotEmpty()) package_name = packageNames.toMutableList()
                    rule.packageNameRegex.blankAsNull()?.let {
                        package_name_regex = mutableListOf(it)
                    }
                    if (processRules.isNotEmpty()) {
                        makeProcessRule(processRules)
                    }
                    if (requestDNSRules.isNotEmpty()) {
                        makeCommonRule(requestDNSRules)
                    }
                    if (useFakeQueryScope) {
                        inbound = mutableListOf(TAG_TUN)
                        query_type = FAKE_DNS_QUERY_TYPE.toMutableList()
                    }
                    return this
                }

                fun buildDnsRules(
                    action: String? = null,
                    server: String? = null,
                    useFakeQueryScope: Boolean = false,
                ): MutableList<JSONMap>? {
                    val hasResponseRule = DNSRule_Default().apply {
                        makeResponseRule(responseDNSRules)
                    }.let { !it.checkEmpty() }
                    val terminalAction = if (
                        hasResponseRule &&
                        action == SingBoxOptions.ACTION_ROUTE &&
                        server == TAG_DNS_REMOTE
                    ) {
                        SingBoxOptions.ACTION_RESPOND
                    } else {
                        action
                    }
                    val terminalRule = DNSRule_Default().applyDnsBase(useFakeQueryScope).apply {
                        if (hasResponseRule) {
                            match_response = JsonPrimitive(true)
                            makeResponseRule(responseDNSRules)
                        }
                        this.action = terminalAction
                        this.server = if (terminalAction == SingBoxOptions.ACTION_RESPOND) {
                            null
                        } else {
                            server
                        }
                    }
                    if (!hasResponseRule) {
                        return terminalRule.takeIf { !it.checkEmpty() }
                            ?.let { mutableListOf(it.asKxsMap()) }
                    }
                    val evaluateRule = DNSRule_Default().applyDnsBase(useFakeQueryScope).apply {
                        this.action = SingBoxOptions.ACTION_EVALUATE
                        this.server = TAG_DNS_REMOTE
                    }
                    return mutableListOf(
                        evaluateRule.asKxsMap(),
                        terminalRule.asKxsMap(),
                    )
                }

                var dnsRuleList: MutableList<JSONMap>? = null
                when (val ruleAction = rule.action) {
                    "", SingBoxOptions.ACTION_ROUTE -> {
                        action = SingBoxOptions.ACTION_ROUTE

                        when (val outID = rule.outbound) {
                            RuleEntity.OUTBOUND_DIRECT -> {
                                if (dnsRuleList == null) {
                                    dnsRuleList = buildDnsRules(
                                        action = SingBoxOptions.ACTION_ROUTE,
                                        server = if (fakeDNSForAll) {
                                            TAG_DNS_FAKE
                                        } else {
                                            TAG_DNS_DIRECT
                                        },
                                    )
                                }
                                outbound = TAG_DIRECT
                            }

                            RuleEntity.OUTBOUND_PROXY -> {
                                if (dnsRuleList == null) {
                                    dnsRuleList = buildDnsRules(
                                        action = SingBoxOptions.ACTION_ROUTE,
                                        server = if (useFakeDns) {
                                            TAG_DNS_FAKE
                                        } else {
                                            TAG_DNS_REMOTE
                                        },
                                        useFakeQueryScope = useFakeDns,
                                    )
                                }
                                outbound = mainTag
                            }

                            RuleEntity.OUTBOUND_BLOCK -> {
                                if (dnsRuleList == null) {
                                    dnsRuleList = buildDnsRules(
                                        action = SingBoxOptions.ACTION_REJECT,
                                    )
                                }
                                outbound = TAG_BLOCK
                            }

                            RuleEntity.OUTBOUND_BRIDGE -> {
                                outbound = TAG_BRIDGE
                            }

                            else -> outbound = if (outID == proxy.id) {
                                mainTag
                            } else {
                                tagMap[outID] ?: ""
                            }
                        }
                    }

                    SingBoxOptions.ACTION_BYPASS -> {
                        action = ruleAction
                        outbound = when (val outID = rule.outbound) {
                            RuleEntity.OUTBOUND_PROXY -> mainTag
                            RuleEntity.OUTBOUND_DIRECT -> TAG_DIRECT
                            RuleEntity.OUTBOUND_BLOCK -> TAG_BLOCK
                            RuleEntity.OUTBOUND_BRIDGE -> TAG_BRIDGE
                            else -> if (outID == proxy.id) {
                                mainTag
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
                        tls_spoof = rule.tlsSpoof.blankAsNull()
                        tls_spoof_method = rule.tlsSpoofMethod.blankAsNull()
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
                        sniffer = rule.sniffers.takeIf { it.isNotEmpty() }?.toMutableList()
                    }

                    SingBoxOptions.ACTION_HIJACK_DNS -> {
                        action = ruleAction
                    }

                    SingBoxOptions.ACTION_REJECT -> {
                        if (dnsRuleList == null) {
                            dnsRuleList = buildDnsRules(
                                action = SingBoxOptions.ACTION_REJECT,
                            )
                        }
                        action = ruleAction
                    }

                    else -> error("unsupported action: $ruleAction")
                }

                rule.customDnsConfig.blankAsNull()?.toJsonMapKxs()?.let { customDns ->
                    if (dnsRuleList == null) {
                        dnsRuleList = mutableListOf(customDns)
                    } else {
                        mergeJson(customDns, dnsRuleList.last())
                    }
                }
                dnsRuleList?.let {
                    userDNSRuleList.addAll(it)
                }

            }

            fun addRule() {
                val ruleMap = ruleObj.asKxsMap()
                rule.customConfig.blankAsNull()?.let {
                    mergeJson(it.toJsonMapKxs(), ruleMap)
                }
                route!!.rules!!.add(ruleMap)
            }
            if (!rule.dnsOnly) {
                if (!ruleObj.checkEmpty()) {
                    // Empty or "route"
                    val needOutbound = when (ruleObj.action) {
                        null, "", SingBoxOptions.ACTION_ROUTE -> true
                        else -> false
                    }
                    if (needOutbound && ruleObj.outbound.isNullOrBlank()) {
                        showToast(
                            "Warning: " + rule.displayName() + ": A non-existent outbound was specified.",
                            long = true,
                        )
                    } else {
                        addRule()
                    }
                } else if (ruleObj.action != SingBoxOptions.ACTION_ROUTE) {
                    addRule()
                } else if (rule.domains.isBlank() && rule.ip.isBlank()) {
                    addRule()
                }
            }
        }

        outbounds!!.add(
            Outbound_DirectOptions().apply {
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
                    if (disableTcpKeepAlive) {
                        disable_tcp_keep_alive = false
                    } else {
                        tcp_keep_alive = tcpKeepAliveIdle
                        tcp_keep_alive_interval = tcpKeepAliveInterval
                    }
                    if (networkPreferredInterfaces.isNotEmpty()) {
                        network_type = networkPreferredInterfaces.toMutableList()
                        network_strategy = mapNetworkInterfaceStrategy(networkInterfaceStrategy)
                    }
                }
            }.asKxsMap(),
        )
        outbounds!!.add(
            Outbound().apply {
                tag = TAG_BLOCK
                type = SingBoxOptions.TYPE_BLOCK
            }.asKxsMap(),
        )
        if (!PlatformInfo.isAndroid && route!!.rules!!.any { it["outbound"] == TAG_BRIDGE }) outbounds!!.add(
            Outbound().apply {
                tag = TAG_BRIDGE
                type = SingBoxOptions.TYPE_BRIDGE
            }.asKxsMap(),
        )

        if (!forTest) localDNSPort?.let {
            inbounds!!.add(
                0,
                Inbound_DirectOptions().apply {
                    type = SingBoxOptions.TYPE_DIRECT
                    tag = TAG_DNS_IN
                    listen = bind
                    listen_port = it
                    override_address = "8.8.8.8"
                    override_port = 53
                },
            )
        }

        // Bypass Lookup for the first profile
        bypassDNSBeans.forEach {
            var serverAddr = it.serverAddress

            if (it is ConfigBean) {
                val config = it.config.toJsonMapKxs()
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
            dns!!.servers!!.add(
                buildDNSServer(
                    it,
                    mainTag,
                    TAG_DNS_REMOTE,
                    DomainResolveOptions().apply {
                        server = TAG_DNS_DIRECT
                    },
                ),
            )
        } ?: error("missing remote DNS")

        // add directDNS objects here
        directDNS.firstOrNull()?.let {
            dns!!.servers!!.add(
                buildDNSServer(
                    it,
                    null,
                    TAG_DNS_DIRECT,
                    DomainResolveOptions().apply {
                        server = TAG_DNS_LOCAL
                    },
                ),
            )
        } ?: error("missing direct DNS")

        // underlyingDns
        dns!!.servers!!.add(
            NewDNSServerOptions_LocalDNSServerOptions().apply {
                tag = TAG_DNS_LOCAL
                type = SingBoxOptions.DNS_TYPE_LOCAL
            },
        )

        // dns object user rules
        dns!!.rules!!.addAll(userDNSRuleList)

        if (forTest) {
            // Always use system DNS for urlTest
            dns!!.servers = mutableListOf(
                NewDNSServerOptions_LocalDNSServerOptions().apply {
                    tag = TAG_DNS_LOCAL
                    type = SingBoxOptions.DNS_TYPE_LOCAL
                },
            )
            dns!!.rules = mutableListOf()
        } else {
            // clash mode
            route!!.rules!!.add(
                0,
                Rule_Default().apply {
                    clash_mode = RuleEntity.MODE_GLOBAL
                    outbound = mainTag
                }.asKxsMap(),
            )
            route!!.rules!!.add(
                0,
                Rule_Default().apply {
                    clash_mode = RuleEntity.MODE_DIRECT
                    outbound = TAG_DIRECT
                }.asKxsMap(),
            )
            route!!.rules!!.add(
                0,
                Rule_Default().apply {
                    clash_mode = RuleEntity.MODE_BLOCK
                    action = SingBoxOptions.ACTION_REJECT
                }.asKxsMap(),
            )

            // built-in DNS rules
            val builtInDNSRule = localDNSPort?.let {
                Rule_Logical().also {
                    it.type = SingBoxOptions.TYPE_LOGICAL
                    it.mode = SingBoxOptions.LOGICAL_OR
                    it.rules = mutableListOf(
                        Rule_Default().apply {
                            inbound = mutableListOf(TAG_DNS_IN)
                        }.toJsonObjectKxs(),
                        Rule_Default().apply {
                            ip_cidr = mutableListOf(
                                VpnConstants.PRIVATE_VLAN4_ROUTER,
                                VpnConstants.PRIVATE_VLAN6_ROUTER,
                            )
                        }.toJsonObjectKxs(),
                    )
                    it.action = SingBoxOptions.ACTION_HIJACK_DNS
                }.asKxsMap()
            } ?: Rule_Default().apply {
                ip_cidr = mutableListOf(
                    VpnConstants.PRIVATE_VLAN4_ROUTER,
                    VpnConstants.PRIVATE_VLAN6_ROUTER,
                )
                action = SingBoxOptions.ACTION_HIJACK_DNS
            }.asKxsMap()
            route!!.rules!!.add(0, builtInDNSRule)

            // FakeDNS obj
            if (useFakeDns) {
                val fakeRange4 = if (networkStrategy == SingBoxOptions.STRATEGY_IPV6_ONLY) {
                    null
                } else {
                    DataStore.fakeDNSRange4.blankAsNull()
                }
                val fakeRange6 = if (networkStrategy == SingBoxOptions.STRATEGY_IPV4_ONLY) {
                    null
                } else {
                    DataStore.fakeDNSRange6.blankAsNull()
                }
                dns!!.servers!!.add(
                    NewDNSServerOptions_FakeIPDNSServerOptions().apply {
                        type = SingBoxOptions.DNS_TYPE_FAKEIP
                        tag = TAG_DNS_FAKE
                        inet4_range = fakeRange4
                        inet6_range = fakeRange6
                    },
                )
                dns!!.rules!!.add(
                    DNSRule_Default().apply {
                        inbound = mutableListOf(TAG_TUN)
                        server = TAG_DNS_FAKE
                        disable_cache = true
                        query_type = FAKE_DNS_QUERY_TYPE.toMutableList()
                    }.asKxsMap(),
                )
            }

            // Pre-filter:
            // Hosts [->mDNS] -> local

            fun addPreferredDNSRule(tag: String) {
                dns!!.rules!!.add(
                    0,
                    DNSRule_Default().apply {
                        preferred_by = mutableListOf(tag)
                        server = tag
                    }.asKxsMap(),
                )
            }

            if (localDNSSupportRaw) {
                addPreferredDNSRule(TAG_DNS_LOCAL)
            }

            // mDNS
            // Make sure mDNS rule before local, because local includes mDNS
            val resolveMDNSByLocal = localDNSSupportRaw && mDNSInterfaces == null
            if (!resolveMDNSByLocal) {
                dns!!.servers!!.add(
                    NewDNSServerOptions_MDNSDNSServerOptions().apply {
                        type = SingBoxOptions.DNS_TYPE_MDNS
                        tag = TAG_DNS_MDNS
                        `interface` = mDNSInterfaces?.toMutableList()
                    },
                )
                addPreferredDNSRule(TAG_DNS_MDNS)
            }

            dnsHosts?.let {
                dns!!.servers!!.add(
                    NewDNSServerOptions_HostsDNSServerOptions().apply {
                        type = SingBoxOptions.DNS_TYPE_HOSTS
                        tag = TAG_DNS_HOSTS
                        predefined = it.toMutableMap()
                    },
                )
                addPreferredDNSRule(TAG_DNS_HOSTS)
            }

            // clash mode
            dns!!.rules!!.add(
                0,
                DNSRule_Default().apply {
                    clash_mode = RuleEntity.MODE_GLOBAL
                    server = TAG_DNS_REMOTE
                }.asKxsMap(),
            )
            dns!!.rules!!.add(
                0,
                DNSRule_Default().apply {
                    clash_mode = RuleEntity.MODE_DIRECT
                    server = TAG_DNS_DIRECT
                }.asKxsMap(),
            )
            dns!!.rules!!.add(
                0,
                DNSRule_Default().apply {
                    clash_mode = RuleEntity.MODE_BLOCK
                    action = SingBoxOptions.ACTION_REJECT
                }.asKxsMap(),
            )

            if (domainListDNSDirectForce.isNotEmpty()) {
                dns!!.rules!!.add(
                    0,
                    DNSRule_Default().apply {
                        domain = domainListDNSDirectForce.distinct().toMutableList()
                        server = TAG_DNS_DIRECT
                    }.asKxsMap(),
                )
            }

        }
        route!!.final_ = mainTag
        if (!forTest) dns!!.final_ = TAG_DNS_REMOTE

        // mapping for plugin
        for ((serverInfo, inboundTags) in mappingOverride) {
            route!!.rules!!.add(
                0,
                Rule_Default().apply {
                    action = SingBoxOptions.ACTION_ROUTE_OPTIONS
                    inbound = inboundTags
                    override_address = serverInfo.first
                    override_port = serverInfo.second
                }.asKxsMap(),
            )
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
            ruleSetResource = repository.externalAssetsDir.resolve("geo").invariantPathString()
        }
        buildRuleSets(geoipLink, geositeLink, ruleSetResource)
        partitionEndpoints()
    }.let {
        val optionsMap = it.toKxs().asKxsMap().apply {
            optionsToMerge.blankAsNull()?.toJsonMapKxs()?.let { jsonMap ->
                mergeJson(jsonMap, this)
            }
        }
        ConfigBuildResult(
            mainTag,
            kxs.encodeToString(optionsMap.toJsonElementKxs()),
            externalIndexMap,
            trafficMap,
            tagMap.reverse(),
        )
    }

}

/**
 * Partition outbounds and endpoints.
 */
fun MyOptions.partitionEndpoints() {
    val pair = outbounds!!.partition { isEndpoint(it["type"].toString()) }
    endpoints = pair.first.toMutableList()
    outbounds = pair.second.toMutableList()
}

fun mapNetworkInterfaceStrategy(strategy: Int): String = when (strategy) {
    NetworkInterfaceStrategy.DEFAULT -> SingBoxOptions.STRATEGY_DEFAULT
    NetworkInterfaceStrategy.HYBRID -> SingBoxOptions.STRATEGY_HYBRID
    NetworkInterfaceStrategy.FALLBACK -> SingBoxOptions.STRATEGY_FALLBACK
    else -> throw IllegalStateException()
}
