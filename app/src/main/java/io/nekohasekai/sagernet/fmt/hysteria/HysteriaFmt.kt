package io.nekohasekai.sagernet.fmt.hysteria

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST4
import io.nekohasekai.sagernet.ktx.*
import libcore.Libcore
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.SingBoxOptions.OutboundECHOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import org.json.JSONObject
import java.io.File

// hysteria://host:port?auth=123456&peer=sni.domain&insecure=1|0&upmbps=100&downmbps=100&alpn=hysteria&obfs=xplus&obfsParam=123456#remarks
fun parseHysteria1(link: String): HysteriaBean {
    val url = Libcore.parseURL(link)
    return HysteriaBean().apply {
        protocolVersion = 1
        serverAddress = url.host
        serverPorts = url.ports
        name = url.fragment

        sni = url.queryParameterNotBlank("peer")
        url.queryParameterNotBlank("auth").takeIf { it.isNotBlank() }.also {
            authPayloadType = HysteriaBean.TYPE_STRING
            authPayload = it
        }
        url.queryParameterNotBlank("insecure").also {
            allowInsecure = it == "1" || it == "true"
        }
        alpn = url.queryParameterNotBlank("alpn")
        obfuscation = url.queryParameterNotBlank("obfsParam")
        protocol = when (url.queryParameterNotBlank("protocol")) {
            "faketcp" -> HysteriaBean.PROTOCOL_FAKETCP

            "wechat-video" -> HysteriaBean.PROTOCOL_WECHAT_VIDEO

            else -> HysteriaBean.PROTOCOL_UDP
        }
    }
}

// hysteria2://[auth@]hostname[:port]/?[key=value]&[key=value]...
fun parseHysteria2(link: String): HysteriaBean {
    val url = Libcore.parseURL(link)
    return HysteriaBean().apply {
        protocolVersion = 2
        serverAddress = url.host
        serverPorts = url.ports

        var ps: String? = null
        try {
            ps = url.password
        } catch (_: Exception) {
        }
        authPayload = if (ps.isNullOrBlank()) {
            url.username
        } else {
            url.username + ":" + url.password
        }

        name = url.fragment

        sni = url.queryParameterNotBlank("sni")
        url.queryParameterNotBlank("insecure").also {
            allowInsecure = it == "1" || it == "true"
        }
        obfuscation = url.queryParameterNotBlank("obfs-password")
        url.queryParameterNotBlank("pinSHA256").also {
            // TODO your box do not support it
        }
    }
}

fun HysteriaBean.toUri(): String {
    //
    val url = Libcore.newURL(
        when (protocolVersion) {
            2 -> "hysteria2"
            else -> "hysteria"
        }
    ).apply {
        host = serverAddress
        ports = serverPorts
        username = authPayload
    }

    if (name.isNotBlank()) {
        url.fragment = name
    }
    if (allowInsecure) {
        url.addQueryParameter("insecure", "1")
    }
    if (protocolVersion == 1) {
        if (sni.isNotBlank()) {
            url.addQueryParameter("peer", sni)
        }
        if (authPayload.isNotBlank()) {
            url.addQueryParameter("auth", authPayload)
        }
        if (alpn.isNotBlank()) {
            url.addQueryParameter("alpn", alpn)
        }
        if (obfuscation.isNotBlank()) {
            url.addQueryParameter("obfs", "xplus")
            url.addQueryParameter("obfsParam", obfuscation)
        }
        when (protocol) {
            HysteriaBean.PROTOCOL_FAKETCP -> {
                url.addQueryParameter("protocol", "faketcp")
            }

            HysteriaBean.PROTOCOL_WECHAT_VIDEO -> {
                url.addQueryParameter("protocol", "wechat-video")
            }
        }
    } else {
        if (sni.isNotBlank()) {
            url.addQueryParameter("sni", sni)
        }
        if (obfuscation.isNotBlank()) {
            url.addQueryParameter("obfs", "salamander")
            url.addQueryParameter("obfs-password", obfuscation)
        }
        if (caText.isNotBlank()) {
            url.addQueryParameter("pinSHA256", Libcore.sha256Hex(caText.toByteArray()))
        }
    }

    return url.string
}

fun JSONObject.parseHysteria1Json(): HysteriaBean {
    // TODO parse HY2 JSON
    return HysteriaBean().apply {
        protocolVersion = 1
        serverAddress = optString("server").substringBeforeLast(":")
        serverPorts = optString("server").substringAfterLast(":")
        obfuscation = getStr("obfs")
        getStr("auth")?.also {
            authPayloadType = HysteriaBean.TYPE_BASE64
            authPayload = it
        }
        getStr("auth_str")?.also {
            authPayloadType = HysteriaBean.TYPE_STRING
            authPayload = it
        }
        getStr("protocol")?.also {
            when (it) {
                "faketcp" -> {
                    protocol = HysteriaBean.PROTOCOL_FAKETCP
                }

                "wechat-video" -> {
                    protocol = HysteriaBean.PROTOCOL_WECHAT_VIDEO
                }
            }
        }
        sni = getStr("server_name")
        alpn = getStr("alpn")
        allowInsecure = getBool("insecure")

        streamReceiveWindow = getIntOrNull("recv_window_conn")
        connectionReceiveWindow = getIntOrNull("recv_window")
        disableMtuDiscovery = getBool("disable_mtu_discovery")
    }
}

fun HysteriaBean.buildHysteriaConfig(port: Int, cacheFile: (() -> File)?): String {
    return when (protocolVersion) {
        1 -> JSONObject().apply {
            put("server", displayAddress())
            when (protocol) {
                HysteriaBean.PROTOCOL_FAKETCP -> {
                    put("protocol", "faketcp")
                }

                HysteriaBean.PROTOCOL_WECHAT_VIDEO -> {
                    put("protocol", "wechat-video")
                }
            }
            put(
                "socks5", JSONObject(
                    mapOf(
                        "listen" to "$LOCALHOST4:$port",
                    )
                )
            )
            put("retry", 5)
            put("fast_open", true)
            put("lazy_start", true)
            put("obfs", obfuscation)
            when (authPayloadType) {
                HysteriaBean.TYPE_BASE64 -> put("auth", authPayload)
                HysteriaBean.TYPE_STRING -> put("auth_str", authPayload)
            }
            if (sni.isBlank() && finalAddress == LOCALHOST4 && !serverAddress.isIpAddress()) {
                sni = serverAddress
            }
            if (sni.isNotBlank()) {
                put("server_name", sni)
            }
            if (alpn.isNotBlank()) put("alpn", alpn)
            if (caText.isNotBlank() && cacheFile != null) {
                val caFile = cacheFile()
                caFile.writeText(caText)
                put("ca", caFile.absolutePath)
            }

            if (allowInsecure) put("insecure", true)
            if (streamReceiveWindow > 0) put("recv_window_conn", streamReceiveWindow)
            if (connectionReceiveWindow > 0) put("recv_window", connectionReceiveWindow)
            if (disableMtuDiscovery) put("disable_mtu_discovery", true)

            // hy 1.2.0 （不兼容）
            put("resolver", "udp://127.0.0.1:" + DataStore.localDNSPort)

            put("hop_interval", hopInterval)

            // speed > 0 is enforced for Hysteria 1
            put("up_mbps", getUploadSpeed())
            put("down_mbps", getDownloadSpeed())
        }.toStringPretty()

        2 -> JSONObject().apply {
            put("server", displayAddress())
            put("auth", authPayload)
            put("fastOpen", true)
            put("lazy", true)

            if (obfuscation.isNotBlank()) {
                put("obfs", JSONObject().apply {
                    put("type", "salamander")
                    put("salamander", JSONObject().apply {
                        put("password", obfuscation)
                    })
                })
            }

            put("quic", JSONObject().apply {
                put("sockopts", JSONObject().apply {
                    put("fdControlUnixSocket", Libcore.ProtectPath)
                }
                )
            }
            )

            put(
                "socks5", JSONObject().apply {
                    put("listen", "$LOCALHOST4:$port")
                }
            )

            put(
                "tls", JSONObject().apply {
                    put("sni", sni)
                    put("insecure", allowInsecure)

                    if (caText.isNotBlank() && cacheFile != null) {
                        val caFile = cacheFile()
                        caFile.writeText(caText)
                        put("ca", caFile.absolutePath)
                    }
                }
            )

            put(
                "transport", JSONObject().apply {
                    put("type", "udp")
                    put("udp", JSONObject().apply {
                        if (hopInterval > 0) put("hopInterval", "${hopInterval}s")
                    })
                }
            )

            if (DataStore.uploadSpeed > 0 || DataStore.downloadSpeed > 0) {
                put("bandwidth", JSONObject().apply {
                    if (DataStore.uploadSpeed > 0) put("up", "${DataStore.uploadSpeed} mbps")
                    if (DataStore.downloadSpeed > 0) put("down", "${DataStore.downloadSpeed} mbps")
                })
            }

        }.toStringPretty()

        else -> throw unknownVersion()
    }
}

/*
fun isMultiPort(hyAddr: String): Boolean {
    if (!hyAddr.contains(":")) return false
    val p = hyAddr.substringAfterLast(":")
    return p.contains("-") || p.contains(",")
}

fun getFirstPort(portStr: String): Int {
    return portStr.substringBefore(":").substringBefore(",").toIntOrNull() ?: 443
}
 */

fun HysteriaBean.canUseSingBox(): Boolean {
    return when {
        protocol != HysteriaBean.PROTOCOL_UDP -> false
        serverPorts.toIntOrNull() == null -> false
        else -> DataStore.providerHysteria2 == 0
    }
}

fun buildSingBoxOutboundHysteriaBean(bean: HysteriaBean): SingBoxOptions.Outbound {
    return when (bean.protocolVersion) {
        1 -> SingBoxOptions.Outbound_HysteriaOptions().apply {
            server = bean.serverAddress
            server_port = bean.serverPorts.toIntOrNull()
            up_mbps = bean.getUploadSpeed()
            down_mbps = bean.getDownloadSpeed()
            obfs = bean.obfuscation
            disable_mtu_discovery = bean.disableMtuDiscovery
            when (bean.authPayloadType) {
                HysteriaBean.TYPE_BASE64 -> auth = bean.authPayload
                HysteriaBean.TYPE_STRING -> auth_str = bean.authPayload
            }
            if (bean.streamReceiveWindow > 0) {
                recv_window_conn = bean.streamReceiveWindow.toLong()
            }
            if (bean.connectionReceiveWindow > 0) {
                recv_window_conn = bean.connectionReceiveWindow.toLong()
            }
            tls = SingBoxOptions.OutboundTLSOptions().apply {
                if (bean.sni.isNotBlank()) {
                    server_name = bean.sni
                }
                if (bean.alpn.isNotBlank()) {
                    alpn = bean.alpn.listByLineOrComma()
                }
                if (bean.caText.isNotBlank()) {
                    certificate = listOf(bean.caText)
                }
                if (bean.ech) {
                    val echList = bean.echCfg.split("\n")
                    ech = OutboundECHOptions().apply {
                        enabled = true
                        pq_signature_schemes_enabled = echList.size > 5
                        dynamic_record_sizing_disabled = true
                        config = echList
                    }
                }
                insecure = bean.allowInsecure || DataStore.globalAllowInsecure
                enabled = true
            }
        }

        2 -> SingBoxOptions.Outbound_Hysteria2Options().apply {
            server = bean.serverAddress
            server_port = bean.serverPorts.toIntOrNull()
            up_mbps = DataStore.uploadSpeed
            down_mbps = DataStore.downloadSpeed
            if (bean.obfuscation.isNotBlank()) {
                obfs = SingBoxOptions.Hysteria2Obfs().apply {
                    type = "salamander"
                    password = bean.obfuscation
                }
            }
//            disable_mtu_discovery = bean.disableMtuDiscovery
            password = bean.authPayload
//            if (bean.streamReceiveWindow > 0) {
//                recv_window_conn = bean.streamReceiveWindow.toLong()
//            }
//            if (bean.connectionReceiveWindow > 0) {
//                recv_window_conn = bean.connectionReceiveWindow.toLong()
//            }
            tls = SingBoxOptions.OutboundTLSOptions().apply {
                if (bean.sni.isNotBlank()) {
                    server_name = bean.sni
                }
                alpn = listOf("h3")
                if (bean.caText.isNotBlank()) {
                    certificate = listOf(bean.caText)
                }
                if (bean.ech) {
                    val echList = bean.echCfg.split("\n")
                    ech = OutboundECHOptions().apply {
                        enabled = true
                        pq_signature_schemes_enabled = echList.size > 5
                        dynamic_record_sizing_disabled = true
                        config = echList
                    }
                }
                insecure = bean.allowInsecure || DataStore.globalAllowInsecure
                enabled = true
            }
        }

        else -> throw bean.unknownVersion()
    }.apply {
        type = bean.outboundType()
    }
}


const val DEFAULT_SPEED = 10

// Just use for Hy1

fun HysteriaBean.getDownloadSpeed(): Int {
    return if (protocolVersion == 1) {
        if (DataStore.downloadSpeed <= 0) {
            DEFAULT_SPEED
        } else {
            DataStore.downloadSpeed
        }
    } else {
        DataStore.downloadSpeed
    }
}

fun HysteriaBean.getUploadSpeed(): Int {
    return if (protocolVersion == 1) {
        if (DataStore.uploadSpeed <= 0) {
            DEFAULT_SPEED
        } else {
            DataStore.uploadSpeed
        }
    } else {
        DataStore.uploadSpeed
    }
}
