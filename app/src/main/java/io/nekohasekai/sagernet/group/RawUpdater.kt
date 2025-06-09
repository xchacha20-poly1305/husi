package io.nekohasekai.sagernet.group

import android.annotation.SuppressLint
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.hysteria.parseHysteria1Json
import io.nekohasekai.sagernet.fmt.parseOutbound
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.isTLS
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.decodeBase64UrlSafe
import io.nekohasekai.sagernet.ktx.forEach
import io.nekohasekai.sagernet.ktx.generateUserAgent
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.isJsonObjectValid
import io.nekohasekai.sagernet.ktx.map
import io.nekohasekai.sagernet.ktx.mapX
import io.nekohasekai.sagernet.ktx.parseProxies
import libcore.Libcore
import org.ini4j.Ini
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.StringReader
import androidx.core.net.toUri
import io.nekohasekai.sagernet.SagerNet.Companion.app

@Suppress("EXPERIMENTAL_API_USAGE", "UNCHECKED_CAST")
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
            val contentText = app.contentResolver.openInputStream(subscription.link.toUri())
                ?.bufferedReader()
                ?.readText()

            proxies = contentText?.let { parseRaw(contentText) }
                ?: errNotFound()
        } else {

            val response = Libcore.newHttpClient().apply {
                if (DataStore.serviceState.started) {
                    useSocks5(DataStore.mixedPort, DataStore.inboundUsername, DataStore.inboundPassword)
                }
                when (DataStore.appTLSVersion) {
                    "1.3" -> restrictedTLS()
                }
            }.newRequest().apply {
                setURL(subscription.link)
                setUserAgent(generateUserAgent(subscription.customUserAgent))
            }.execute()
            proxies = parseRaw(response.contentString.value) ?: errNotFound()

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

        if (text.contains("[Interface]")) {
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
        } catch (e: Exception) {
            Logs.w(e)
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
        } catch (e: Exception) {
            Logs.w(e)
        }

        return null
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
        bean.listenPort = iface["ListenPort"]?.toIntOrNull() ?: 0
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
                json.has("outbounds") || json.has("endpoints") -> {
                    // sing-box

                    val outbounds = json.optJSONArray("outbounds")
                    val endpoints = json.optJSONArray("endpoints")
                    var length = outbounds?.length() ?: 0
                    endpoints?.length()?.let { length += it }
                    if (length == 0) {
                        errNotFound<Unit>()
                    }

                    fun add(outbound: Any) {
                        parseJSON(outbound as JSONObject).let {
                            proxies.addAll(it)
                        }
                    }
                    outbounds?.forEach { _, outbound ->
                        try {
                            add(outbound)
                        } catch (e: Exception) {
                            Logs.w(e)
                        }
                    }
                    endpoints?.forEach { _, endpoint ->
                        try {
                            add(endpoint)
                        } catch (e: Exception) {
                            Logs.w(e)
                        }
                    }
                }

                // server + server_port or server_ports -> outbound
                json.has("server") && (json.has("server_port") || json.has("server_ports")) -> {
                    // Single sing-box outbound
                    return parseOutbound(json.map)?.let {
                        listOf(it)
                    } ?: errNotFound()
                }

                // single endpoint
                json.has("peers") -> return parseOutbound(json.map)?.let {
                    listOf(it)
                } ?: errNotFound()

                json.has("server") && (json.has("up") || json.has("up_mbps")) -> {
                    return listOf(json.parseHysteria1Json())
                }

                json.has("method") -> {
                    return listOf(json.parseShadowsocks())
                }

                json.has("version") && json.has("servers") -> {
                    // try to parse SIP008
                    json.getJSONArray("servers").forEach { _, it ->
                        if (it is JSONObject) {
                            proxies.add(it.parseShadowsocks())
                        }
                    }
                }

                else -> {
                    errNotFound()
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

        proxies.forEach {
            it.initializeDefaultValues()
            if (it is StandardV2RayBean) {
                // 1. Fix SNI for end users.
                if (it.isTLS() && it.sni.isNullOrBlank() && !it.host.isNullOrBlank() && !it.host.isIpAddress()) {
                    it.sni = it.host
                }
            }
        }
        return proxies
    }

    private fun <T> errNotFound(): T {
        error(app.getString(R.string.no_proxies_found))
    }
}
