@file:Suppress("UNCHECKED_CAST")

package fr.husi.fmt

import fr.husi.MuxStrategy
import fr.husi.MuxType
import fr.husi.database.DataStore
import fr.husi.fmt.SingBoxOptions.BrutalOptions
import fr.husi.fmt.SingBoxOptions.OutboundECHOptions
import fr.husi.fmt.SingBoxOptions.OutboundMultiplexOptions
import fr.husi.fmt.SingBoxOptions.OutboundRealityOptions
import fr.husi.fmt.SingBoxOptions.OutboundTLSOptions
import fr.husi.fmt.SingBoxOptions.OutboundUTLSOptions
import fr.husi.fmt.SingBoxOptions.TYPE_ANYTLS
import fr.husi.fmt.SingBoxOptions.TYPE_HTTP
import fr.husi.fmt.SingBoxOptions.TYPE_HYSTERIA
import fr.husi.fmt.SingBoxOptions.TYPE_HYSTERIA2
import fr.husi.fmt.SingBoxOptions.TYPE_NAIVE
import fr.husi.fmt.SingBoxOptions.TYPE_SHADOWSOCKS
import fr.husi.fmt.SingBoxOptions.TYPE_SOCKS
import fr.husi.fmt.SingBoxOptions.TYPE_SSH
import fr.husi.fmt.SingBoxOptions.TYPE_TROJAN
import fr.husi.fmt.SingBoxOptions.TYPE_TUIC
import fr.husi.fmt.SingBoxOptions.TYPE_VLESS
import fr.husi.fmt.SingBoxOptions.TYPE_VMESS
import fr.husi.fmt.SingBoxOptions.TYPE_WIREGUARD
import fr.husi.fmt.anytls.AnyTLSBean
import fr.husi.fmt.anytls.buildSingBoxOutboundAnyTLSBean
import fr.husi.fmt.anytls.parseAnyTLSOutbound
import fr.husi.fmt.config.ConfigBean
import fr.husi.fmt.direct.DirectBean
import fr.husi.fmt.direct.buildSingBoxOutboundDirectBean
import fr.husi.fmt.http.HttpBean
import fr.husi.fmt.http.parseHttpOutbound
import fr.husi.fmt.hysteria.HysteriaBean
import fr.husi.fmt.hysteria.buildSingBoxOutboundHysteriaBean
import fr.husi.fmt.hysteria.parseHysteria1Outbound
import fr.husi.fmt.hysteria.parseHysteria2Outbound
import fr.husi.fmt.internal.ChainBean
import fr.husi.fmt.internal.ProxySetBean
import fr.husi.fmt.juicity.JuicityBean
import fr.husi.fmt.mieru.MieruBean
import fr.husi.fmt.naive.NaiveBean
import fr.husi.fmt.naive.buildSingBoxOutboundNaiveBean
import fr.husi.fmt.naive.parseNaiveOutbound
import fr.husi.fmt.shadowquic.ShadowQUICBean
import fr.husi.fmt.shadowsocks.ShadowsocksBean
import fr.husi.fmt.shadowsocks.buildSingBoxOutboundShadowsocksBean
import fr.husi.fmt.shadowsocks.parseShadowsocksOutbound
import fr.husi.fmt.shadowtls.ShadowTLSBean
import fr.husi.fmt.socks.SOCKSBean
import fr.husi.fmt.socks.buildSingBoxOutboundSocksBean
import fr.husi.fmt.socks.parseSocksOutbound
import fr.husi.fmt.ssh.SSHBean
import fr.husi.fmt.ssh.buildSingBoxOutboundSSHBean
import fr.husi.fmt.ssh.parseSSHOutbound
import fr.husi.fmt.trojan.TrojanBean
import fr.husi.fmt.trusttunnel.TrustTunnelBean
import fr.husi.fmt.trusttunnel.buildSingBoxOutboundTrustTunnelBean
import fr.husi.fmt.tuic.TuicBean
import fr.husi.fmt.tuic.buildSingBoxOutboundTuicBean
import fr.husi.fmt.tuic.parseTuicOutbound
import fr.husi.fmt.v2ray.StandardV2RayBean
import fr.husi.fmt.v2ray.VLESSBean
import fr.husi.fmt.v2ray.VMessBean
import fr.husi.fmt.v2ray.buildSingBoxOutboundStandardV2RayBean
import fr.husi.fmt.v2ray.parseStandardV2RayOutbound
import fr.husi.fmt.wireguard.WireGuardBean
import fr.husi.fmt.wireguard.buildSingBoxEndpointWireGuardBean
import fr.husi.fmt.wireguard.parseWireGuardEndpoint
import fr.husi.ktx.JSONMap
import fr.husi.ktx.getBool
import fr.husi.ktx.getIntOrNull
import fr.husi.ktx.getObject
import fr.husi.ktx.getStr
import fr.husi.ktx.kxs

fun AbstractBean.toJsonStringKxs(): String = when (this) {
    is AnyTLSBean -> kxs.encodeToString(this)
    is ConfigBean -> kxs.encodeToString(this)
    is DirectBean -> kxs.encodeToString(this)
    is HttpBean -> kxs.encodeToString(this)
    is HysteriaBean -> kxs.encodeToString(this)
    is ChainBean -> kxs.encodeToString(this)
    is ProxySetBean -> kxs.encodeToString(this)
    is JuicityBean -> kxs.encodeToString(this)
    is MieruBean -> kxs.encodeToString(this)
    is NaiveBean -> kxs.encodeToString(this)
    is ShadowQUICBean -> kxs.encodeToString(this)
    is ShadowsocksBean -> kxs.encodeToString(this)
    is ShadowTLSBean -> kxs.encodeToString(this)
    is SOCKSBean -> kxs.encodeToString(this)
    is SSHBean -> kxs.encodeToString(this)
    is TrojanBean -> kxs.encodeToString(this)
    is TrustTunnelBean -> kxs.encodeToString(this)
    is TuicBean -> kxs.encodeToString(this)
    is VLESSBean -> kxs.encodeToString(this)
    is VMessBean -> kxs.encodeToString(this)
    is WireGuardBean -> kxs.encodeToString(this)
    else -> error("impossible bean type: ${this.javaClass.simpleName}")
}

fun buildSingBoxOutbound(bean: AbstractBean): String = when (bean) {
    is ConfigBean -> bean.config // What if full config?
    is DirectBean -> kxs.encodeToString(buildSingBoxOutboundDirectBean(bean).apply { tag = bean.name })
    is StandardV2RayBean ->
        kxs.encodeToString(buildSingBoxOutboundStandardV2RayBean(bean).apply { tag = bean.name })
    is HysteriaBean ->
        kxs.encodeToString(buildSingBoxOutboundHysteriaBean(bean).apply { tag = bean.name })
    is ShadowsocksBean ->
        kxs.encodeToString(buildSingBoxOutboundShadowsocksBean(bean).apply { tag = bean.name })
    is SOCKSBean -> kxs.encodeToString(buildSingBoxOutboundSocksBean(bean).apply { tag = bean.name })
    is SSHBean -> kxs.encodeToString(buildSingBoxOutboundSSHBean(bean).apply { tag = bean.name })
    is TuicBean -> kxs.encodeToString(buildSingBoxOutboundTuicBean(bean).apply { tag = bean.name })
    is WireGuardBean ->
        kxs.encodeToString(buildSingBoxEndpointWireGuardBean(bean).apply { tag = bean.name })
    is AnyTLSBean ->
        kxs.encodeToString(buildSingBoxOutboundAnyTLSBean(bean).apply { tag = bean.name })
    is NaiveBean ->
        kxs.encodeToString(buildSingBoxOutboundNaiveBean(bean).apply { tag = bean.name })
    is TrustTunnelBean ->
        kxs.encodeToString(buildSingBoxOutboundTrustTunnelBean(bean).apply { tag = bean.name })
    else -> error("invalid bean: ${bean.javaClass.simpleName}")
}

fun buildSingBoxMux(bean: AbstractBean): OutboundMultiplexOptions? {
    if (!bean.serverMux) return null

    return OutboundMultiplexOptions().apply {
        enabled = true
        padding = bean.serverMuxPadding
        protocol = when (bean.serverMuxType) {
            MuxType.H2MUX -> "h2mux"
            MuxType.SMUX -> "smux"
            MuxType.YAMUX -> "yamux"
            else -> throw IllegalArgumentException("unknown mux type: ${bean.serverMuxType}")
        }

        if (bean.serverBrutal) {
            max_connections = 1
            brutal = BrutalOptions().apply {
                enabled = true
                up_mbps = -1 // need kernel module
                down_mbps = DataStore.downloadSpeed
            }
        } else when (bean.serverMuxStrategy) {
            MuxStrategy.MAX_CONNECTIONS -> max_connections = bean.serverMuxNumber
            MuxStrategy.MIN_STREAMS -> min_streams = bean.serverMuxNumber
            MuxStrategy.MAX_STREAMS -> max_streams = bean.serverMuxNumber
            else -> throw IllegalStateException("unknown mux strategy: ${bean.serverMuxStrategy}")
        }
    }
}

fun buildHeader(raw: String): Map<String, MutableList<String>> {
    return raw.lines().mapNotNull { line ->
        val pair = line.split(":", limit = 2)
        if (pair.size == 2) {
            pair[0].trim() to mutableListOf(pair[1].trim())
        } else {
            null
        }
    }.toMap()
}

fun parseOutbound(json: JSONMap): AbstractBean? = when (json["type"].toString()) {
    TYPE_SOCKS -> parseSocksOutbound(json)

    TYPE_HTTP -> parseHttpOutbound(json)

    TYPE_SHADOWSOCKS -> parseShadowsocksOutbound(json)

    TYPE_VMESS, TYPE_VLESS, TYPE_TROJAN -> parseStandardV2RayOutbound(json)

    TYPE_WIREGUARD -> parseWireGuardEndpoint(json)

    TYPE_HYSTERIA -> parseHysteria1Outbound(json)

    TYPE_HYSTERIA2 -> parseHysteria2Outbound(json)

    TYPE_TUIC -> parseTuicOutbound(json)

    TYPE_SSH -> parseSSHOutbound(json)

    TYPE_ANYTLS -> parseAnyTLSOutbound(json)

    TYPE_NAIVE -> parseNaiveOutbound(json)

    else -> null
}

/**
 * Parses a JSON map and updates the properties of the AbstractBean.
 *
 * This function iterates over the entries in the provided JSON map and updates the properties of the AbstractBean
 * based on the keys and values found. If a key does not match any known property, the unmatched callback is invoked.
 *
 * @param json The JSON map to parse.
 * @param unmatched A callback function to handle entries that do not match any known property.
 */
fun AbstractBean.parseBoxOutbound(json: JSONMap, unmatched: (key: String, value: Any) -> Unit) {
    // Note:
    // Use .toString().to*() instead of as.
    // because all integer will turn to Long.

    for (entry in json) {
        val value = entry.value ?: continue

        when (val key = entry.key) {
            "tag" -> name = value.toString()
            "server" -> serverAddress = value.toString()
            "server_port" -> serverPort = value.toString().toIntOrNull() ?: 443

            "multiplex" -> {
                val mux = (value as JSONMap)
                if (mux.getBool("enabled") != true) continue

                serverMux = true
                serverMuxPadding = mux.getBool("padding") == true
                serverMuxType = when (mux.getStr("protocol")) {
                    "smux" -> MuxType.SMUX
                    "yamux" -> MuxType.YAMUX
                    else -> MuxType.H2MUX
                }

                serverBrutal = mux.getObject("brutal")?.getBool("enabled") == true

                mux.getIntOrNull("max_connections")?.takeIf { it > 0 }?.let {
                    serverMuxStrategy = MuxStrategy.MAX_CONNECTIONS
                    serverMuxNumber = it
                    continue
                }
                mux.getIntOrNull("min_streams")?.takeIf { it > 0 }?.let {
                    serverMuxStrategy = MuxStrategy.MIN_STREAMS
                    serverMuxNumber = it
                    continue
                }
                mux.getIntOrNull("max_streams")?.takeIf { it > 0 }?.let {
                    serverMuxStrategy = MuxStrategy.MAX_STREAMS
                    serverMuxNumber = it
                }
            }

            else -> unmatched(key, value)
        }
    }
}

fun parseHeader(header: Map<*, *>): Map<String, MutableList<String>> {
    val builder = LinkedHashMap<String, MutableList<String>>(header.size)
    for (entry in header) {
        // http headers are case-insensitive, so we lowercase the key.
        val key = entry.key.toString().lowercase()
        val value = when (val entryValue = entry.value) {
            is List<*> -> {
                entryValue.map { it.toString() }.toMutableList()
            }

            else -> {
                mutableListOf(entryValue.toString())
            }
        }
        builder[key] = value
    }
    return builder
}

/**
 * Converts a given value to a mutable list of a specified type.
 *
 * This function takes an input value and attempts to convert it to a mutable list of the specified type [T].
 * If the value is null, it returns null. If the value is already a list, it maps the elements to the specified type [T]
 * and returns a mutable list. If the value is of type [T], it returns a mutable list containing that single value.
 * If the value is not of type [T] or a list, it attempts to cast the value to [T] and returns a mutable list containing it.
 *
 * @param T The type of elements in the resulting list.
 * @param value The value to be converted to a mutable list.
 * @return A mutable list of type [T] or null if the input value is null.
 */
inline fun <reified T : Any> listable(value: Any?): MutableList<T>? = when (value) {
    null -> null
    is List<*> -> value.mapNotNull { it as? T }.toMutableList()
    is T -> mutableListOf(value)
    else -> (value as? T)?.let { mutableListOf(it) }
}

fun parseBoxUot(field: Any?): Boolean {
    if (field as? Boolean == true) return true
    return (field as? JSONMap)?.getBool("enabled") == true
}

fun parseBoxTLS(field: JSONMap): OutboundTLSOptions = OutboundTLSOptions().apply {
    for (entry in field) {
        val value = entry.value ?: continue

        when (entry.key) {
            "enabled" -> enabled = value.toString().toBoolean()
            "server_name" -> server_name = value.toString()
            "insecure" -> insecure = value.toString().toBoolean()
            "disable_sni" -> disable_sni = value.toString().toBoolean()

            "alpn" -> alpn = listable<String>(value)

            "certificate" -> certificate = listable<String>(value)
            "certificate_public_key_sha256" -> {
                certificate_public_key_sha256 = listable<String>(value)
            }

            "client_certificate" -> client_certificate = listable<String>(value)
            "client_key" -> client_key = listable<String>(value)

            "fragment" -> fragment = value.toString().toBoolean()
            "fragment_fallback_delay" -> fragment_fallback_delay = value.toString()
            "record_fragment" -> record_fragment = value.toString().toBoolean()

            "utls" -> {
                val utlsField = value as JSONMap
                utls = OutboundUTLSOptions().also {
                    it.enabled = utlsField.getBool("enabled")
                    it.fingerprint = utlsField.getStr("fingerprint")
                }
            }

            "ech" -> {
                val echField = value as JSONMap
                ech = OutboundECHOptions().also {
                    it.enabled = echField.getBool("enabled")
                    it.config = listable<String>(echField["config"])
                    it.query_server_name = echField.getStr("query_server_name")
                }
            }

            "reality" -> {
                val realityField = value as JSONMap
                reality = OutboundRealityOptions().also {
                    it.enabled = realityField.getBool("enabled")
                    it.public_key = realityField.getStr("public_key")
                    it.short_id = realityField.getStr("short_id")
                }
            }
        }
    }
}
