package io.nekohasekai.sagernet.fmt.hysteria

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST4
import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.fmt.listable
import io.nekohasekai.sagernet.fmt.parseBoxTLS
import io.nekohasekai.sagernet.ktx.JSONMap
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.getBool
import io.nekohasekai.sagernet.ktx.getIntOrNull
import io.nekohasekai.sagernet.ktx.getStr
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.linkBoolean
import io.nekohasekai.sagernet.ktx.map
import io.nekohasekai.sagernet.ktx.mapX
import io.nekohasekai.sagernet.ktx.toStringPretty
import io.nekohasekai.sagernet.ktx.wrapIPV6Host
import libcore.Libcore
import moe.matsuri.nb4a.utils.listByLineOrComma
import org.json.JSONObject
import java.io.File

// hysteria://host:port?auth=123456&peer=sni.domain&insecure=1|0&upmbps=100&downmbps=100&alpn=hysteria&obfs=xplus&obfsParam=123456#remarks
fun parseHysteria1(link: String): HysteriaBean {
    val url = Libcore.parseURL(link)
    return HysteriaBean().apply {
        protocolVersion = HysteriaBean.PROTOCOL_VERSION_1
        serverAddress = url.host
        serverPorts = url.ports
        name = url.fragment

        sni = url.queryParameterNotBlank("peer")
        url.queryParameterNotBlank("auth").takeIf { it.isNotBlank() }.also {
            authPayloadType = HysteriaBean.TYPE_STRING
            authPayload = it
        }
        allowInsecure = url.queryParameterNotBlank("insecure").linkBoolean()
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
        protocolVersion = HysteriaBean.PROTOCOL_VERSION_2
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
        allowInsecure = url.queryParameterNotBlank("insecure").linkBoolean()
        obfuscation = url.queryParameterNotBlank("obfs-password")
        /*url.queryParameterNotBlank("pinSHA256").also {
            // TODO your box do not support it
        }*/
    }
}

fun HysteriaBean.toUri(): String {
    val url = Libcore.newURL(
        when (protocolVersion) {
            HysteriaBean.PROTOCOL_VERSION_2 -> "hysteria2"
            else -> "hysteria"
        }
    ).apply {
        host = serverAddress
        ports = when (val ports = HopPort.from(serverPorts)) {
            is HopPort.Single -> serverPorts
            // URL just support Hysteria style.
            is HopPort.Ports -> ports.hyStyle().joinToString(HopPort.SPLIT_FLAG)
        }
        username = authPayload
    }

    if (name.isNotBlank()) {
        url.fragment = name
    }
    if (allowInsecure) {
        url.addQueryParameter("insecure", "1")
    }
    if (protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
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
        if (certificates.isNotBlank()) {
            url.addQueryParameter("pinSHA256", Libcore.sha256Hex(certificates.toByteArray()))
        }
    }

    return url.string
}

fun JSONObject.parseHysteria1Json(): HysteriaBean {
    // TODO parse HY2 JSON
    return HysteriaBean().apply {
        protocolVersion = HysteriaBean.PROTOCOL_VERSION_1
        serverAddress = optString("server").substringBeforeLast(":")
        serverPorts = optString("server").substringAfterLast(":")
        getStr("hop_interval")?.also {
            hopInterval = it + "s"
        }
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

fun HysteriaBean.buildHysteriaConfig(
    port: Int,
    shouldProtect: Boolean,
    cacheFile: (() -> File)?,
): String {
    val address = when (val hopPort = HopPort.from(serverPorts)) {
        is HopPort.Single -> serverAddress.wrapIPV6Host() + ":" + hopPort.port
        is HopPort.Ports -> serverAddress.wrapIPV6Host() + ":" + hopPort.hyStyle().joinToString(",")
    }
    return when (protocolVersion) {
        HysteriaBean.PROTOCOL_VERSION_1 -> JSONObject().apply {
            put("server", address)
            when (protocol) {
                HysteriaBean.PROTOCOL_FAKETCP -> {
                    put("protocol", "faketcp")
                }

                HysteriaBean.PROTOCOL_WECHAT_VIDEO -> {
                    put("protocol", "wechat-video")
                }
            }
            put("socks5", JSONObject().apply {
                put("listen", "$LOCALHOST4:$port")
            })
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
            if (certificates.isNotBlank() && cacheFile != null) {
                val caFile = cacheFile()
                caFile.writeText(certificates)
                put("ca", caFile.absolutePath)
            }

            if (allowInsecure) put("insecure", true)
            if (streamReceiveWindow > 0) put("recv_window_conn", streamReceiveWindow)
            if (connectionReceiveWindow > 0) put("recv_window", connectionReceiveWindow)
            if (disableMtuDiscovery) put("disable_mtu_discovery", true)

            // hy 1.2.0
            DataStore.localDNSPort.takeIf { it > 0 }?.let {
                put("resolver", "udp://127.0.0.1:$it")
            }

            val hopSeconds = try {
                // parseDuration returns a nanoseconds of time.Duration.
                (Libcore.parseDuration(hopInterval).toDouble() / 1000000000.0).toInt()
            } catch (_: Exception) {
                // Not Go style duration, try to fallback to pure int.
                hopInterval.toInt()
            }
            if (hopSeconds > 0) put("hop_interval", hopSeconds)

            // speed > 0 is enforced for Hysteria 1
            put("up_mbps", generateUploadSpeed())
            put("down_mbps", generateDownloadSpeed())
        }.toStringPretty()

        HysteriaBean.PROTOCOL_VERSION_2 -> JSONObject().apply {
            put("server", address)
            put("auth", authPayload)
            put("fastOpen", true)
            put("lazy", true)

            if (obfuscation.isNotBlank()) put("obfs", JSONObject().apply {
                put("type", "salamander")
                put("salamander", JSONObject().apply {
                    put("password", obfuscation)
                })
            })

            if (shouldProtect) put("quic", JSONObject().apply {
                put("sockopts", JSONObject().apply {
                    put("fdControlUnixSocket", Libcore.ProtectPath)
                })
            })

            put("socks5", JSONObject().apply {
                put("listen", "$LOCALHOST4:$port")
            })

            put("tls", JSONObject().apply {
                put("sni", sni)
                put("insecure", allowInsecure)

                if (certificates.isNotBlank() && cacheFile != null) {
                    val caFile = cacheFile()
                    caFile.writeText(certificates)
                    put("ca", caFile.absolutePath)
                }
            })

            put("transport", JSONObject().apply {
                put("type", "udp")
                put("udp", JSONObject().apply {
                    if (hopInterval.isNotBlank()) put("hopInterval", hopInterval)
                })
            })

            val uploadSpeed = DataStore.uploadSpeed
            val downloadSpeed = DataStore.downloadSpeed
            if (uploadSpeed > 0 || downloadSpeed > 0) {
                put("bandwidth", JSONObject().apply {
                    if (uploadSpeed > 0) put("up", "$uploadSpeed mbps")
                    if (downloadSpeed > 0) put("down", "$downloadSpeed mbps")
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
    if (DataStore.providerHysteria2 != 0) return false // Force plugin
    if (protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
        if (protocol != HysteriaBean.PROTOCOL_UDP) return false // special mode
    }
    return true
}

fun buildSingBoxOutboundHysteriaBean(bean: HysteriaBean): SingBoxOptions.Outbound {
    return when (bean.protocolVersion) {
        HysteriaBean.PROTOCOL_VERSION_1 -> SingBoxOptions.Outbound_HysteriaOptions().apply {
            server = bean.serverAddress
            when (val hopPort = HopPort.from(bean.serverPorts)) {
                is HopPort.Single -> server_port = hopPort.port
                is HopPort.Ports -> server_ports = hopPort.singStyle()
            }
            up_mbps = bean.generateUploadSpeed()
            down_mbps = bean.generateDownloadSpeed()
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
                if (bean.certificates.isNotBlank()) {
                    certificate = listOf(bean.certificates)
                }
                if (bean.ech) {
                    val echConfig =
                        bean.echConfig.blankAsNull()?.split("\n")?.takeIf { it.isNotEmpty() }
                    ech = SingBoxOptions.OutboundECHOptions().apply {
                        enabled = true
                        config = echConfig
                    }
                }
                insecure = bean.allowInsecure
                enabled = true
            }
        }

        HysteriaBean.PROTOCOL_VERSION_2 -> SingBoxOptions.Outbound_Hysteria2Options().apply {
            server = bean.serverAddress
            when (val hopPort = HopPort.from(bean.serverPorts)) {
                is HopPort.Single -> server_port = hopPort.port
                is HopPort.Ports -> {
                    hop_interval = bean.hopInterval
                    server_ports = hopPort.singStyle()
                }
            }
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
                if (bean.certificates.isNotBlank()) {
                    certificate = listOf(bean.certificates)
                }
                if (bean.ech) {
                    val echConfig =
                        bean.echConfig.blankAsNull()?.split("\n")?.takeIf { it.isNotEmpty() }
                    ech = SingBoxOptions.OutboundECHOptions().apply {
                        enabled = true
                        config = echConfig
                    }
                }
                insecure = bean.allowInsecure
                enabled = true
            }
        }

        else -> throw bean.unknownVersion()
    }.apply {
        type = bean.outboundType()
    }
}

/**
 * Designed for adapting Hy1 and Hy2 at the same time.
 */
sealed class HopPort {
    companion object {
        const val SPLIT_FLAG = ","
        const val BOX_RANGE = ":"
        const val HYSTERIA_RANGE = "-"

        fun from(ports: String): HopPort {
            val ranges = ports.split(SPLIT_FLAG)
            if (ranges.size == 1) {
                val first = ranges.first()
                if (!first.contains(BOX_RANGE) && !first.contains(HYSTERIA_RANGE)) {
                    return Single(first.toInt())
                }
            }
            return Ports(ranges)
        }
    }

    class Single(val port: Int) : HopPort()
    class Ports(val raw: List<String>) : HopPort() {
        fun singStyle(): List<String> = raw.mapX {
            val basic = it.replace(HYSTERIA_RANGE, BOX_RANGE)
            if (basic.contains(BOX_RANGE)) {
                basic
            } else {
                basic + BOX_RANGE + basic
            }
        }

        fun hyStyle(): List<String> = raw.mapX { it.replace(BOX_RANGE, HYSTERIA_RANGE) }
    }
}

const val DEFAULT_SPEED = 10

// Just use for Hy1

fun HysteriaBean.generateDownloadSpeed(): Int = DataStore.downloadSpeed.let {
    if (it <= 0) {
        DEFAULT_SPEED
    } else {
        it
    }
}

fun HysteriaBean.generateUploadSpeed(): Int = DataStore.uploadSpeed.let {
    if (it <= 0) {
        DEFAULT_SPEED
    } else {
        it
    }
}

fun parseHysteria1Outbound(json: JSONMap): HysteriaBean = HysteriaBean().apply {
    protocolVersion = HysteriaBean.PROTOCOL_VERSION_1

    var tmpPorts = mutableListOf<String>()
    for (entry in json) {
        val value = entry.value ?: continue

        when (entry.key) {
            "tag" -> name = value.toString()
            "server" -> serverAddress = value.toString()
            "server_port" -> tmpPorts.add(value.toString())
            "server_ports" -> listable<String>(value)?.let {
                tmpPorts.addAll(it)
            }

            "hop_interval" -> hopInterval = value.toString()
            "obfs" -> obfuscation = value.toString()

            "auth" -> {
                authPayloadType = HysteriaBean.TYPE_BASE64
                authPayload = value.toString()
            }

            "auth_str" -> {
                authPayloadType = HysteriaBean.TYPE_STRING
                authPayload = value.toString()
            }

            "tls" -> {
                loadTLS(parseBoxTLS((value as JSONObject).map))
            }
        }
    }

    serverPorts = tmpPorts.joinToString(HopPort.SPLIT_FLAG)
}

fun parseHysteria2Outbound(json: JSONMap): HysteriaBean = HysteriaBean().apply {
    protocolVersion = HysteriaBean.PROTOCOL_VERSION_2

    val tmpPorts = mutableListOf<String>()
    for (entry in json) {
        val value = entry.value ?: continue

        when (entry.key) {
            "tag" -> name = value.toString()
            "server" -> serverAddress = value.toString()
            "server_port" -> tmpPorts.add(value.toString())
            "server_ports" -> listable<String>(value)?.let {
                tmpPorts.addAll(it)
            }

            "hop_interval" -> hopInterval = value.toString()
            "obfs" -> {
                val obfsField = value as? JSONObject ?: continue
                obfsField.getStr("password")?.let {
                    obfuscation = it
                }
            }

            "password" -> authPayload = value.toString()

            "tls" -> {
                loadTLS(parseBoxTLS((value as JSONObject).map))
            }

        }
    }

    serverPorts = tmpPorts.joinToString(HopPort.SPLIT_FLAG)
}

private fun HysteriaBean.loadTLS(tls: SingBoxOptions.OutboundTLSOptions) {
    sni = tls.server_name
    allowInsecure = tls.insecure
    certificates = tls.certificate?.joinToString("\n")
    alpn = tls.alpn?.joinToString("\n")
    tls.ech?.let {
        ech = it.enabled
        echConfig = it.config?.joinToString("\n")
    }
}