package fr.husi.group

import fr.husi.database.DataStore
import fr.husi.database.ProxyGroup
import fr.husi.database.SubscriptionBean
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.hysteria.parseHysteria1Json
import fr.husi.fmt.openconnect.parseOpenConnectConfig
import fr.husi.fmt.openvpn.looksLikeOpenVPNConfig
import fr.husi.fmt.openvpn.parseOpenVPNConfig
import fr.husi.fmt.parseOutbound
import fr.husi.fmt.shadowsocks.parseShadowsocks
import fr.husi.fmt.v2ray.StandardV2RayBean
import fr.husi.fmt.wireguard.parseWireGuardConfig
import fr.husi.ktx.JSONMap
import fr.husi.ktx.Logs
import fr.husi.ktx.SubscriptionFoundException
import fr.husi.ktx.b64DecodeToString
import fr.husi.ktx.generateUserAgent
import fr.husi.ktx.isIpAddress
import fr.husi.ktx.kxs
import fr.husi.ktx.parseProxies
import fr.husi.ktx.toJsonMapKxs
import fr.husi.libcore.resolveHttpClientFactory
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.no_proxies_found
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Suppress("EXPERIMENTAL_API_USAGE", "UNCHECKED_CAST")
object RawUpdater : GroupUpdater() {

    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        byUser: Boolean,
        warnings: MutableList<GroupUpdateWarning>,
    ): GroupUpdateResult.Success {

        val contentText: String
        var userInfo = ""
        if (subscription.link.startsWith("content://")) {
            contentText = readContentUri(subscription.link) ?: errNotFound()
        } else {
            val response = resolveHttpClientFactory().newHttpClient().apply {
                if (DataStore.serviceState.connected) {
                    useSocks5(
                        DataStore.mixedPort,
                        DataStore.inboundUsername,
                        DataStore.inboundPassword,
                    )
                }
                if (subscription.ageIdentity.isNotBlank()) {
                    setAgeKey(subscription.ageIdentity)
                }
            }.newRequest().apply {
                setURL(subscription.link)
                setUserAgent(generateUserAgent(subscription.customUserAgent))
            }.execute()
            contentText = response.contentString
            userInfo = response.getHeader("Subscription-Userinfo")
        }

        val proxies = parseRaw(contentText) ?: errNotFound()

        if (!subscription.link.startsWith("content://")) {
            // https://github.com/crossutility/Quantumult/blob/master/extra-subscription-feature.md
            // Subscription-Userinfo: upload=2375927198; download=12983696043; total=1099511627776; expire=1862111613
            // Be careful that some value may be empty.
            if (userInfo.isNotBlank()) {
                var used = 0L
                var total = 0L
                var expired = 0L
                for (info in userInfo.split(";")) {
                    info.split("=", limit = 2).let {
                        if (it.size != 2) return@let
                        val key = it[0].trim()
                        val value = it[1].trim().toLongOrNull() ?: 0
                        when (key) {
                            "upload", "download" -> used += value
                            "total" -> total = value
                            "expire" -> expired = value
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

        return tidyProxies(proxies, subscription, proxyGroup, byUser, warnings)
    }
    @Suppress("UNCHECKED_CAST")
    suspend fun parseRaw(text: String, fileName: String = ""): List<AbstractBean>? {

        val proxies = mutableListOf<AbstractBean>()

        runCatching {
            parseOpenConnectConfig(text)
        }.onSuccess { bean ->
            if (fileName.isNotBlank()) bean.name = fileName.removeSuffix(".conf")
            return listOf(bean)
        }

        if (looksLikeOpenVPNConfig(text)) {
            parseOpenVPNConfig(text).let { bean ->
                if (fileName.isNotBlank()) bean.name = fileName.removeSuffix(".ovpn")
                return listOf(bean)
            }
        }

        if (text.contains("[Interface]")) {
            // wireguard
            try {
                val beans = parseWireGuardConfig(text)
                val hasFileName = fileName.isNotBlank()
                for ((i, bean) in beans.withIndex()) {
                    if (hasFileName) bean.name = bean.name.removeSuffix(".conf")
                    if (beans.size > 1) bean.name += i
                }
                proxies.addAll(beans)
                return proxies
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        val trimmed = text.trimStart()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val element = kxs.parseToJsonElement(text)
                if (element is JsonPrimitive) error("unexpected JSON primitive")
                parseJSON(element).takeIf { it.isNotEmpty() }?.let { return it }
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        if (!text.contains("://")) {
            try {
                parseProxies(text.b64DecodeToString()).takeIf { it.isNotEmpty() }?.let { return it }
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        try {
            parseProxies(text).takeIf { it.isNotEmpty() }?.let { return it }
        } catch (e: SubscriptionFoundException) {
            throw e
        } catch (e: Exception) {
            Logs.w(e)
        }

        return null
    }

    fun parseJSON(element: JsonElement): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()

        if (element is JsonObject) {
            val json = element.toJsonMapKxs()
            when {
                "outbounds" in json || "endpoints" in json -> {
                    val outbounds = json["outbounds"] as? List<*>
                    val endpoints = json["endpoints"] as? List<*>
                    var length = outbounds?.size ?: 0
                    endpoints?.size?.let { length += it }
                    if (length == 0) {
                        errNotFound<Unit>()
                    }

                    fun add(outbound: Any?) {
                        val map = outbound as? JSONMap ?: return
                        parseOutbound(map)?.let {
                            proxies.add(it)
                        }
                    }
                    outbounds?.forEach { outbound ->
                        try {
                            add(outbound)
                        } catch (e: Exception) {
                            Logs.w(e)
                        }
                    }
                    endpoints?.forEach { endpoint ->
                        try {
                            add(endpoint)
                        } catch (e: Exception) {
                            Logs.w(e)
                        }
                    }
                }

                "server" in json && ("server_port" in json || "server_ports" in json) -> {
                    return parseOutbound(json)?.let {
                        listOf(it)
                    } ?: errNotFound()
                }

                "peers" in json -> return parseOutbound(json)?.let {
                    listOf(it)
                } ?: errNotFound()

                "server" in json && ("up" in json || "up_mbps" in json) -> {
                    return listOf(json.parseHysteria1Json())
                }

                "method" in json -> {
                    return listOf(json.parseShadowsocks())
                }

                "version" in json && "servers" in json -> {
                    val servers = json["servers"] as? List<*>
                    servers?.forEach {
                        val server = it as? JSONMap ?: return@forEach
                        proxies.add(server.parseShadowsocks())
                    }
                }

                else -> {
                    errNotFound()
                }
            }
        } else if (element is JsonArray) {
            for (item in element) {
                if (item is JsonObject) {
                    proxies.addAll(parseJSON(item))
                }
            }
        }

        proxies.forEach {
            it.initializeDefaultValues()
            if (it is StandardV2RayBean) {
                if (it.isTLS && it.sni.isBlank() && it.host.isNotBlank() && !it.host.isIpAddress()) {
                    it.sni = it.host
                }
            }
        }
        return proxies
    }

    private inline fun <reified T> errNotFound(): T = runBlocking {
        error(resolveRepository().getString(Res.string.no_proxies_found))
    }
}
