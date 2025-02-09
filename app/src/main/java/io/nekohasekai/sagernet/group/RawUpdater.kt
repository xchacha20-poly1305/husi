package io.nekohasekai.sagernet.group

import android.annotation.SuppressLint
import android.net.Uri
import io.nekohasekai.sagernet.MuxStrategy
import io.nekohasekai.sagernet.MuxType
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.config.ConfigBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.hysteria.parseHysteria1Json
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.isTLS
import io.nekohasekai.sagernet.fmt.v2ray.setTLS
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.decodeBase64UrlSafe
import io.nekohasekai.sagernet.ktx.forEach
import io.nekohasekai.sagernet.ktx.generateUserAgent
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.isJsonObjectValid
import io.nekohasekai.sagernet.ktx.mapX
import io.nekohasekai.sagernet.ktx.parseProxies
import io.nekohasekai.sagernet.ktx.toStringPretty
import libcore.Libcore
import moe.matsuri.nb4a.utils.JavaUtil.gson
import org.ini4j.Ini
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.StringReader

@Suppress("EXPERIMENTAL_API_USAGE")
object RawUpdater : GroupUpdater() {

    @SuppressLint("Recycle")
    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        byUser: Boolean,
    ) {

        var proxies: List<AbstractBean>
        if (subscription.link.startsWith("content://")) {
            val contentText = app.contentResolver.openInputStream(Uri.parse(subscription.link))
                ?.bufferedReader()
                ?.readText()

            proxies = contentText?.let { parseRaw(contentText) }
                ?: error(app.getString(R.string.no_proxies_found_in_subscription))
        } else {

            val response = Libcore.newHttpClient().apply {
                trySocks5(DataStore.mixedPort, DataStore.inboundUsername, DataStore.inboundPassword)
                when (DataStore.appTLSVersion) {
                    "1.3" -> restrictedTLS()
                }
            }.newRequest().apply {
                setURL(subscription.link)
                setUserAgent(generateUserAgent(subscription.customUserAgent))
            }.execute()
            proxies = parseRaw(response.contentString.value)
                ?: error(app.getString(R.string.no_proxies_found))

            // https://github.com/crossutility/Quantumult/blob/master/extra-subscription-feature.md
            // Subscription-Userinfo: upload=2375927198; download=12983696043; total=1099511627776; expire=1862111613
            // Be careful that some value may be empty.
            val userInfo = response.getHeader("Subscription-Userinfo")
            if (userInfo.isNotBlank()) {
                var used = 0L
                var total = 0L
                var expired = 0L
                for (info in userInfo.split("; ")) {
                    info.split("=", limit = 2).let {
                        if (it.size != 2) return@let
                        when (it[0]) {
                            "upload", "download" -> used += it[1].toLongOrNull() ?: 0
                            "total" -> total = it[1].toLongOrNull() ?: 0
                            "expire" -> expired = it[1].toLongOrNull() ?: 0
                        }
                    }
                }
                subscription.apply {
                    bytesUsed = used
                    bytesRemaining = total - used
                    expiryDate = expired
                }
            }
        }

        tidyProxies(proxies, subscription, proxyGroup, userInterface, byUser)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun parseRaw(text: String, fileName: String = ""): List<AbstractBean>? {

        val proxies = mutableListOf<AbstractBean>()

        if (text.contains("outbounds")) {

            // sing-box

            // Note: use .toString()[.to*()] instead of `as`.
            // Example: this map will parse server_port as Long.

            try {

                val json = gson.fromJson(text, Map::class.java)

                var proxyList = json["outbounds"] as? List<Map<String, Any>>
                    ?: error(app.getString(R.string.no_proxies_found_in_file))
                (json["endpoints"] as? List<Map<String, Any>>)?.let {
                    proxyList += it
                }
                for (proxy in proxyList) when (proxy["type"].toString()) {
                    "socks" -> proxies.add(SOCKSBean().apply {
                        applyFromMap(proxy) { opt ->
                            when (opt.key) {
                                "username" -> username = opt.value.toString()
                                "password" -> password = opt.value.toString()
                                "version" -> protocol = when (opt.value.toString()) {
                                    "4" -> SOCKSBean.PROTOCOL_SOCKS4
                                    "4a" -> SOCKSBean.PROTOCOL_SOCKS4A
                                    else -> SOCKSBean.PROTOCOL_SOCKS5
                                }

                                "udp_over_tcp" -> udpOverTcp = parseUot(opt.value)
                            }
                        }
                    })

                    "http" -> proxies.add(HttpBean().apply {
                        applyFromMap(proxy) { opt ->
                            when (opt.key) {
                                "username" -> username = opt.value.toString()
                                "password" -> password = opt.value.toString()
                                "tls" -> for (tlsOpt in opt.value as Map<String, Any>) {
                                    when (tlsOpt.key) {
                                        "enabled" -> setTLS(tlsOpt.value.toString().toBoolean())

                                        "server_name" -> sni = tlsOpt.value.toString()
                                        "insecure" -> allowInsecure =
                                            tlsOpt.value.toString().toBoolean()

                                        "alpn" -> alpn = listable<String>(tlsOpt.value)
                                            ?.joinToString("\n")

                                        "certificate" -> certificates =
                                            listable<String>(tlsOpt.value)?.joinToString("\n")

                                        "ech" -> for (echOpt in (tlsOpt.value as Map<String, Any>)) {
                                            when (echOpt.key) {
                                                "enabled" -> ech =
                                                    echOpt.value.toString().toBoolean()

                                                "config" -> echConfig =
                                                    listable<String>(echOpt.value)?.joinToString("\n")
                                            }
                                        }

                                        "utls" -> for (utlsOpt in (tlsOpt.value as Map<String, Any>)) {
                                            when (utlsOpt.key) {
                                                "fingerprint" -> utlsFingerprint =
                                                    utlsOpt.value.toString()
                                            }
                                        }

                                        "reality" -> for (realityOpt in (tlsOpt.value as Map<String, Any>)) {
                                            when (realityOpt.key) {
                                                "public_key" -> realityPublicKey =
                                                    realityOpt.value.toString()

                                                "short_id" -> realityShortID =
                                                    realityOpt.value.toString()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    })

                    "shadowsocks" -> proxies.add(ShadowsocksBean().apply {
                        var pluginName = ""
                        var pluginOpt = ""
                        applyFromMap(proxy) { opt ->
                            when (opt.key) {
                                "password" -> password = opt.value.toString()
                                "method" -> method = opt.value.toString()
                                "plugin" -> pluginName = opt.value.toString()
                                "plugin_opts" -> pluginOpt = opt.value.toString()
                                "udp_over_tcp" -> udpOverTcp = parseUot(opt.value)
                            }
                        }
                        if (pluginName.isNotBlank()) plugin = "$pluginName;$pluginOpt"
                    })

                    "vmess", "vless", "trojan" -> {
                        val protocol = when (proxy["type"]?.toString()) {
                            "vless" -> 0
                            "vmess" -> 1
                            "trojan" -> 2
                            else -> 0
                        }
                        val bean = when (protocol) {
                            0, 1 -> VMessBean().apply {
                                alterId = when (protocol) {
                                    0 -> -1
                                    else -> proxy["alter_id"]?.toString()?.toInt()
                                }
                                packetEncoding = when (proxy["packet_encoding"]?.toString()) {
                                    "packetaddr" -> 1
                                    "xudp" -> 2
                                    else -> if (protocol == 1) 2 else 0 // VLESS use XUDP
                                }
                            }

                            else -> TrojanBean()
                        }

                        bean.applyFromMap(proxy) { opt ->
                            when (opt.key) {
                                "uuid" -> if (protocol != 2) bean.uuid = opt.value.toString()
                                "password" -> (bean as? TrojanBean)?.password = opt.value.toString()
                                "flow" -> if (protocol == 0) bean.encryption = opt.value.toString()

                                "security" -> if (protocol == 1) bean.encryption =
                                    opt.value.toString()

                                "transport" -> for (transportOpt in (opt.value as Map<String, Any>)) {
                                    when (transportOpt.key) {
                                        "type" -> bean.v2rayTransport = transportOpt.value.toString()
                                        "host" -> listable<String>(transportOpt.value)?.firstOrNull()
                                        "path", "service_name" -> bean.path =
                                            transportOpt.value.toString()

                                        "max_early_data" -> bean.wsMaxEarlyData =
                                            transportOpt.value.toString().toInt()

                                        "early_data_header_name" -> bean.earlyDataHeaderName =
                                            transportOpt.value.toString()
                                    }
                                }

                                "tls" -> for (tlsOpt in (opt.value as Map<String, Any>)) {
                                    when (tlsOpt.key) {
                                        "enabled" -> bean.setTLS(
                                            tlsOpt.value.toString().toBoolean()
                                        )

                                        "server_name" -> bean.sni = tlsOpt.value.toString()
                                        "insecure" -> bean.allowInsecure =
                                            tlsOpt.value.toString().toBoolean()

                                        "alpn" -> bean.alpn =
                                            listable<String>(tlsOpt.value)?.joinToString("\n")

                                        "certificate" -> bean.certificates =
                                            listable<String>(tlsOpt.value)?.joinToString("\n")

                                        "ech" -> for (echOpt in (tlsOpt.value as Map<String, Any>)) {
                                            when (echOpt.key) {
                                                "enabled" -> bean.ech =
                                                    echOpt.value.toString().toBoolean()

                                                "config" -> bean.echConfig =
                                                    listable<String>(tlsOpt.value)
                                                        ?.joinToString("\n")
                                            }
                                        }

                                        "utls" -> for (utlsOpt in (tlsOpt.value as Map<String, Any>)) {
                                            when (utlsOpt.key) {
                                                "fingerprint" -> bean.utlsFingerprint =
                                                    utlsOpt.value.toString()
                                            }
                                        }

                                        "reality" -> for (realityOpt in (tlsOpt.value as Map<String, Any>)) {
                                            when (realityOpt.key) {
                                                "public_key" -> bean.realityPublicKey =
                                                    realityOpt.value.toString()

                                                "short_id" -> bean.realityShortID =
                                                    realityOpt.value.toString()
                                            }
                                        }
                                    }
                                }

                            }

                        }
                        proxies.add(bean)
                    }

                    "wireguard" -> {
                        val peer = (proxy["peers"] as? List<Map<String, Any?>>)
                            ?.firstOrNull() ?: continue
                        proxies.add(WireGuardBean().apply {
                            name = proxy["tag"].toString()
                            mtu = proxy["mtu"]?.toString()?.toIntOrNull()
                            localAddress =
                                listable<String>(proxy["address"])?.joinToString("\n")
                            listenPort = proxy["listen_port"]?.toString()?.toIntOrNull()
                            privateKey = proxy["private_key"]?.toString()

                            for (opt in peer) {
                                if (opt.value == null) continue
                                when (opt.key) {
                                    "address" -> serverAddress = opt.value.toString()
                                    "port" -> serverPort = opt.value.toString().toInt()
                                    "public_key" -> publicKey = opt.value.toString()
                                    "pre_shared_key" -> preSharedKey = opt.value.toString()
                                    "reserved" -> reserved = when (val v = opt.value) {
                                        is String? -> v

                                        is List<*>? -> v?.mapX {
                                            it.toString()
                                        }?.joinToString(",")

                                        else -> null
                                    }
                                }
                            }
                        })
                    }

                    "hysteria" -> proxies.add(HysteriaBean().apply {
                        protocolVersion = HysteriaBean.PROTOCOL_VERSION_1
                        for (opt in proxy) {
                            if (opt.value == null) continue
                            when (opt.key) {
                                "tag" -> name = opt.value.toString()
                                "server" -> serverAddress = opt.value.toString()
                                "server_port" -> serverPorts = opt.value.toString()
                                "obfs" -> obfuscation = opt.value.toString()

                                "auth" -> {
                                    authPayloadType = HysteriaBean.TYPE_BASE64
                                    authPayload = opt.value.toString()
                                }

                                "auth_str" -> {
                                    authPayloadType = HysteriaBean.TYPE_STRING
                                    authPayload = opt.value.toString()
                                }

                                "tls" -> for (tlsOpt in (proxy["tls"] as Map<String, Any>)) {
                                    when (tlsOpt.key) {
                                        "server_name" -> sni = tlsOpt.value.toString()
                                        "insecure" -> allowInsecure =
                                            tlsOpt.value.toString().toBoolean()

                                        "alpn" -> alpn =
                                            listable<String>(tlsOpt.value)?.joinToString("\n")

                                        "certificate" -> certificates =
                                            listable<String>(tlsOpt.value)?.joinToString("\n")

                                        "ech" -> for (echOpt in (tlsOpt.value as Map<String, Any>)) {
                                            when (echOpt.key) {
                                                "enabled" -> ech =
                                                    echOpt.value.toString().toBoolean()

                                                "config" -> echConfig =
                                                    listable<String>(echOpt.value)?.joinToString("\n")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    })

                    "hysteria2" -> proxies.add(HysteriaBean().apply {
                        protocolVersion = HysteriaBean.PROTOCOL_VERSION_2
                        for (opt in proxy) {
                            if (opt.value == null) continue
                            when (opt.key) {
                                "tag" -> name = opt.value.toString()
                                "server" -> serverAddress = opt.value.toString()
                                "server_port" -> serverPorts = opt.value.toString()
                                "server_ports" -> listable<String>(opt.value)?.joinToString(",")
                                "hop_interval" -> hopInterval = opt.value.toString()
                                "obfs" -> for (obfsOpt in (opt.value as Map<String, Any>)) {
                                    when (obfsOpt.key) {
                                        "password" -> obfuscation = obfsOpt.value.toString()
                                    }
                                }

                                "password" -> authPayload = opt.value.toString()

                                "tls" -> for (tlsOpt in (opt.value as Map<String, Any>)) {
                                    when (tlsOpt.key) {
                                        "server_name" -> sni = tlsOpt.value.toString()
                                        "insecure" -> allowInsecure =
                                            tlsOpt.value.toString().toBoolean()

                                        "alpn" -> alpn = listable<String>(tlsOpt.value)
                                            ?.joinToString("\n")

                                        "certificate" -> certificates = listable<String>(tlsOpt.value)
                                            ?.joinToString("\n")

                                        "ech" -> for (echOpt in (tlsOpt.value as Map<String, Any>)) {
                                            when (echOpt.key) {
                                                "enabled" -> ech =
                                                    echOpt.value.toString().toBoolean()

                                                "config" -> echConfig =
                                                    listable<String>(echOpt.value)
                                                        ?.joinToString("\n")
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    })

                    "tuic" -> proxies.add(TuicBean().apply {
                        applyFromMap(proxy) { opt ->
                            when (opt.key) {
                                "uuid" -> uuid = opt.value.toString()
                                "password" -> token = opt.value.toString()
                                "zero_rtt_handshake" -> {
                                    reduceRTT = opt.value.toString().toBoolean()
                                }

                                "congestion_control" -> {
                                    congestionController = opt.value.toString()
                                }

                                "udp_relay_mode" -> udpRelayMode = opt.value.toString()
                                "udp_over_stream" -> if (opt.value.toString().toBoolean()) {
                                    udpRelayMode = "UDP over Stream"
                                }

                                "tls" -> for (tlsOpt in (opt.value as Map<String, Any>)) {
                                    when (tlsOpt.key) {
                                        "server_name" -> sni = tlsOpt.value.toString()
                                        "disable_sni" -> disableSNI =
                                            tlsOpt.value.toString().toBoolean()

                                        "insecure" -> allowInsecure =
                                            tlsOpt.value.toString().toBoolean()

                                        "alpn" -> alpn = listable<String>(tlsOpt.value)
                                            ?.joinToString("\n")

                                        "certificate" -> certificates = listable<String>(tlsOpt.value)
                                            ?.joinToString("\n")

                                        "ech" -> for (echOpt in (tlsOpt.value as Map<String, Any>)) {
                                            when (echOpt.key) {
                                                "enabled" -> ech =
                                                    echOpt.value.toString().toBoolean()

                                                "config" -> echConfig =
                                                    listable<String>(echOpt.value)?.joinToString("\n")
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    })

                    "ssh" -> proxies.add(SSHBean().apply {
                        applyFromMap(proxy) { opt ->
                            when (opt.key) {
                                "user" -> username = opt.value.toString()
                                "password" -> password = opt.value.toString()
                                "private_key" -> privateKey = opt.value.toString()
                                "private_key_passphrase" -> {
                                    privateKeyPassphrase = opt.value.toString()
                                }

                                "host_key" -> listable<String>(opt.value)?.firstOrNull()
                            }
                        }
                    })
                }

                // Fix ent
                proxies.forEach {
                    it.initializeDefaultValues()
                    if (it is StandardV2RayBean) {
                        // 1. SNI
                        if (it.isTLS() && it.sni.isNullOrBlank() && !it.host.isNullOrBlank() && !it.host.isIpAddress()) {
                            it.sni = it.host
                        }
                    }
                }
                return proxies
            } catch (e: Exception) {
                Logs.w(e)
            }
        } else if (text.contains("[Interface]")) {
            // wireguard
            try {
                proxies.addAll(parseWireGuard(text).mapX {
                    if (fileName.isNotBlank()) it.name = fileName.removeSuffix(".conf")
                    it
                })
                return proxies
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        try {
            val json = JSONTokener(text).nextValue()
            return parseJSON(json)
        } catch (_: Exception) {
        }

        try {
            return parseProxies(text.decodeBase64UrlSafe()).takeIf { it.isNotEmpty() }
                ?: error("Not found")
        } catch (e: Exception) {
            Logs.w(e)
        }

        try {
            return parseProxies(text).takeIf { it.isNotEmpty() } ?: error("Not found")
        } catch (e: SubscriptionFoundException) {
            throw e
        } catch (_: Exception) {
        }

        return null
    }

    fun parseUot(conf: Any?): Boolean {
        if (conf as? Boolean == true) return true
        val uotStruct = (conf as? Map<String, Any>) ?: return false
        return (uotStruct["enabled"] as? Boolean) == true
    }

    private fun AbstractBean.applyFromMap(
        opts: Map<String, Any>,
        block: (Map.Entry<String, Any>) -> Unit,
    ) {
        for (opt in opts) {
            if (opt.value == null) continue // could be null, do not delete it.
            when (opt.key) {
                "tag" -> name = opt.value.toString()
                "server" -> serverAddress = opt.value.toString()
                "server_port" -> serverPort = opt.value.toString().toInt()
                "multiplex" -> {
                    val muxMap = opt.value as? Map<String, Any?> ?: continue
                    if (muxMap["enabled"] != true) continue

                    serverMux = true
                    serverMuxPadding = muxMap["padding"] as? Boolean ?: false
                    muxMap["protocol"]?.let {
                        serverMuxType = when (it) {
                            "smux" -> MuxType.SMUX
                            "yamux" -> MuxType.YAMUX
                            else -> MuxType.H2MUX
                        }
                    }

                    muxMap["max_connections"]?.toString()?.toIntOrNull()?.let {
                        serverMuxStrategy = MuxStrategy.MAX_CONNECTIONS
                        serverMuxNumber = it
                    }
                    muxMap["min_streams"]?.toString()?.toIntOrNull()?.let {
                        serverMuxStrategy = MuxStrategy.MIN_STREAMS
                        serverMuxNumber = it
                    }
                    muxMap["max_streams"]?.toString()?.toIntOrNull()?.let {
                        serverMuxStrategy = MuxStrategy.MAX_STREAMS
                        serverMuxNumber = it
                    }

                    if ((muxMap["brutal"] as? Map<String, Any>)?.get("enabled") as? Boolean == true) {
                        serverBrutal = true
                    }
                }

                else -> block(opt)
            }
        }
    }

    fun parseWireGuard(conf: String): List<WireGuardBean> {
        val ini = Ini(StringReader(conf))
        val iface = ini["Interface"] ?: error("Missing 'Interface' selection")
        val bean = WireGuardBean().applyDefaultValues()
        val localAddresses = iface.getAll("Address")
        if (localAddresses.isNullOrEmpty()) error("Empty address in 'Interface' selection")
        bean.localAddress = localAddresses.flatMap { it.split(",") }.joinToString("\n")
        bean.privateKey = iface["PrivateKey"]
        bean.mtu = iface["MTU"]?.toIntOrNull() ?: 1408
        bean.listenPort = iface["ListenPort"]?.toInt()
        val peers = ini.getAll("Peer")
        if (peers.isNullOrEmpty()) error("Missing 'Peer' selections")
        val beans = mutableListOf<WireGuardBean>()
        loopPeer@ for (peer in peers) {
            val peerBean = bean.clone()
            for ((keyName, keyValue) in peer) {
                when (keyName.lowercase()) {
                    "endpoint" -> {
                        peerBean.serverPort =
                            keyValue.substringAfterLast(":").toIntOrNull() ?: continue@loopPeer
                        peerBean.serverAddress = keyValue.substringBeforeLast(":")
                    }

                    "publickey" -> peerBean.publicKey = keyValue ?: continue@loopPeer
                    "presharedkey" -> peerBean.preSharedKey = keyValue
                }
            }
            beans.add(peerBean.applyDefaultValues())
        }
        if (beans.isEmpty()) error("Empty available peer list")
        return beans
    }

    fun parseJSON(json: Any): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()

        if (json is JSONObject) {
            when {
                json.has("server") && (json.has("up") || json.has("up_mbps")) -> {
                    return listOf(json.parseHysteria1Json())
                }

                json.has("method") -> {
                    return listOf(json.parseShadowsocks())
                }

                json.has("outbounds") -> {
                    return listOf(ConfigBean().applyDefaultValues().apply {
                        config = json.toStringPretty()
                    })
                }

                json.has("server") && json.has("server_port") -> {
                    return listOf(ConfigBean().applyDefaultValues().apply {
                        type = ConfigBean.TYPE_OUTBOUND
                        config = json.toStringPretty()
                    })
                }

                json.has("version") && json.has("servers") -> {
                    // try to parse SIP008
                    json.getJSONArray("servers").forEach { _, it ->
                        if (it is JSONObject) {
                            proxies.add(it.parseShadowsocks())
                        }
                    }
                }
            }
        } else {
            json as JSONArray
            json.forEach { _, it ->
                if (isJsonObjectValid(it)) {
                    proxies.addAll(parseJSON(it))
                }
            }
        }

        proxies.forEach { it.initializeDefaultValues() }
        return proxies
    }

    private inline fun <reified T : Any> listable(value: Any?): MutableList<T>? = when (value) {
        null -> null
        is List<*> -> value.mapNotNull { it as? T }.toMutableList()
        is T -> mutableListOf(value)
        else -> (value as? T)?.let { mutableListOf(it) }
    }

}
