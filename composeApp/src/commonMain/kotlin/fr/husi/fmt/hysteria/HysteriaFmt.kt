@file:Suppress("UNCHECKED_CAST")

package fr.husi.fmt.hysteria

import fr.husi.ProtocolProvider
import fr.husi.database.DataStore
import fr.husi.fmt.LOCALHOST4
import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.listable
import fr.husi.fmt.parseBoxTLS
import fr.husi.fmt.toECHOneLine
import fr.husi.fmt.toECHPem
import fr.husi.ktx.JSONMap
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.emptyAsNull
import fr.husi.ktx.getBool
import fr.husi.ktx.getIntOrNull
import fr.husi.ktx.getStr
import fr.husi.ktx.isIpAddress
import fr.husi.ktx.listByLineOrComma
import fr.husi.ktx.parseBoolean
import fr.husi.ktx.queryParameterNotBlank
import fr.husi.ktx.sha256Hex
import fr.husi.ktx.toJsonStringKxs
import fr.husi.ktx.wrapIPV6Host
import fr.husi.libcore.Libcore
import java.io.File

// hysteria://host:port?auth=123456&peer=sni.domain&insecure=1|0&upmbps=100&downmbps=100&alpn=hysteria&obfs=xplus&obfsParam=123456#remarks
fun parseHysteria1(link: String): HysteriaBean {
    val url = Libcore.parseURL(link)
    return HysteriaBean().apply {
        protocolVersion = HysteriaBean.PROTOCOL_VERSION_1
        serverAddress = url.host
        serverPorts = url.ports
        name = url.fragment

        sni = url.queryParameter("peer")
        url.queryParameterNotBlank("auth")?.let {
            authPayloadType = HysteriaBean.TYPE_STRING
            authPayload = it
        }
        allowInsecure = url.parseBoolean("insecure")
        alpn = url.queryParameter("alpn")
        obfsPassword = url.queryParameter("obfsParam")
        protocol = when (url.queryParameterNotBlank("protocol")) {
            "faketcp" -> HysteriaBean.PROTOCOL_FAKETCP

            "wechat-video" -> HysteriaBean.PROTOCOL_WECHAT_VIDEO

            else -> HysteriaBean.PROTOCOL_UDP
        }
        url.queryParameterNotBlank("mport")?.let {
            serverPorts = it
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

        val pwd = url.password
        authPayload = if (pwd.isNullOrBlank()) {
            url.username
        } else {
            url.username + ":" + url.password
        }

        // name = url.fragment

        sni = url.queryParameter("sni")
        allowInsecure = url.parseBoolean("insecure")
        url.queryParameterNotBlank("ech")?.let {
            ech = true
            echConfig = it.toECHPem()
        }
        url.queryParameterNotBlank("obfs")?.let { obfsType = it }
        obfsPassword = url.queryParameter("obfs-password")
        /*url.queryParameterNotBlank("pinSHA256").also {
            // sing-box do not support it
        }*/
        // May invented by shadowrocket
        url.queryParameterNotBlank("mport")?.let {
            serverPorts = it
        }
    }
}

fun HysteriaBean.toUri(): String {
    val url = Libcore.newURL(
        when (protocolVersion) {
            HysteriaBean.PROTOCOL_VERSION_2 -> "hysteria2"
            else -> "hysteria"
        },
    ).apply {
        host = serverAddress
        ports = when (val ports = HopPort.from(serverPorts)) {
            is HopPort.Single -> serverPorts
            // URL just support Hysteria style.
            is HopPort.Ports -> ports.hyStyle().joinToString(HopPort.SPLIT_FLAG)
        }
        username = authPayload
    }

    if (allowInsecure) {
        url.addQueryParameter("insecure", "1")
    }
    if (protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
        name.blankAsNull()?.let {
            url.fragment = it
        }
        if (sni.isNotBlank()) {
            url.addQueryParameter("peer", sni)
        }
        if (authPayload.isNotBlank()) {
            url.addQueryParameter("auth", authPayload)
        }
        if (alpn.isNotBlank()) {
            url.addQueryParameter("alpn", alpn)
        }
        if (obfsPassword.isNotBlank()) {
            url.addQueryParameter("obfs", "xplus")
            url.addQueryParameter("obfsParam", obfsPassword)
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
        if (ech) {
            echConfig.blankAsNull()?.let {
                url.addQueryParameter("ech", it.toECHOneLine())
            }
        }
        obfsType.blankAsNull()?.let {
            url.addQueryParameter("obfs", it)
            url.addQueryParameter("obfs-password", obfsPassword)
        }
        if (certificates.isNotBlank()) {
            url.addQueryParameter("pinSHA256", certificates.sha256Hex())
        }
    }

    return url.string
}

fun JSONMap.parseHysteria1Json(): HysteriaBean {
    // TODO parse HY2 JSON
    return HysteriaBean().apply {
        protocolVersion = HysteriaBean.PROTOCOL_VERSION_1
        serverAddress = (this@parseHysteria1Json["server"] as? String)
            .orEmpty()
            .substringBeforeLast(":")
        serverPorts = (this@parseHysteria1Json["server"] as? String)
            .orEmpty()
            .substringAfterLast(":")
        getStr("hop_interval")?.also {
            hopInterval = it + "s"
        }
        obfsPassword = getStr("obfs").orEmpty()
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
        sni = getStr("server_name").orEmpty()
        alpn = getStr("alpn").orEmpty()
        allowInsecure = getBool("insecure") == true

        streamReceiveWindow = getIntOrNull("recv_window_conn") ?: 0
        connectionReceiveWindow = getIntOrNull("recv_window") ?: 0
        disableMtuDiscovery = getBool("disable_mtu_discovery") == true
    }
}

fun HysteriaBean.buildHysteriaConfig(
    port: Int,
    shouldProtect: Boolean,
    cacheFile: ((type: String) -> File)?,
): String {
    val address = when (val hopPort = HopPort.from(serverPorts)) {
        is HopPort.Single -> serverAddress.wrapIPV6Host() + ":" + hopPort.port
        is HopPort.Ports -> serverAddress.wrapIPV6Host() + ":" + hopPort.hyStyle().joinToString(",")
    }
    return when (protocolVersion) {
        HysteriaBean.PROTOCOL_VERSION_1 -> {
            if (sni.isBlank() && finalAddress == LOCALHOST4 && !serverAddress.isIpAddress()) {
                sni = serverAddress
            }
            val hopSeconds = try {
                (Libcore.parseDuration(hopInterval).toDouble() / 1000000000.0).toInt()
            } catch (_: Exception) {
                hopInterval.toInt()
            }
            buildMap<String, Any?> {
                put("server", address)
                when (protocol) {
                    HysteriaBean.PROTOCOL_FAKETCP -> put("protocol", "faketcp")
                    HysteriaBean.PROTOCOL_WECHAT_VIDEO -> put("protocol", "wechat-video")
                }
                put("socks5", buildMap { put("listen", "$LOCALHOST4:$port") })
                put("retry", 5)
                put("fast_open", true)
                put("lazy_start", true)
                obfsPassword.blankAsNull()?.let { put("obfs", it) }
                if (authPayloadType == HysteriaBean.TYPE_BASE64) put("auth", authPayload)
                if (authPayloadType == HysteriaBean.TYPE_STRING) put("auth_str", authPayload)
                sni.blankAsNull()?.let { put("server_name", it) }
                alpn.blankAsNull()?.let { put("alpn", it) }
                if (cacheFile != null) {
                    certificates.blankAsNull()?.let {
                        val caFile = cacheFile("ca")
                        caFile.writeText(certificates)
                        put("ca", caFile.absolutePath)
                    }
                }
                if (allowInsecure) put("insecure", true)
                if (streamReceiveWindow > 0) put("recv_window_conn", streamReceiveWindow)
                if (connectionReceiveWindow > 0) put("recv_window", connectionReceiveWindow)
                if (disableMtuDiscovery) put("disable_mtu_discovery", true)
                DataStore.localDNSPort.takeIf { it > 0 }?.let {
                    put("resolver", "udp://127.0.0.1:$it")
                }
                if (hopSeconds > 0) put("hop_interval", hopSeconds)
                put("up_mbps", generateUploadSpeed())
                put("down_mbps", generateDownloadSpeed())
            }.toJsonStringKxs()
        }

        HysteriaBean.PROTOCOL_VERSION_2 -> {
            val uploadSpeed = DataStore.uploadSpeed
            val downloadSpeed = DataStore.downloadSpeed
            var caPath: String? = null
            var certPath: String? = null
            var keyPath: String? = null
            if (cacheFile != null) {
                certificates.blankAsNull()?.let {
                    val caFile = cacheFile("ca")
                    caFile.writeText(certificates)
                    caPath = caFile.absolutePath
                }
                clientCert.blankAsNull()?.let {
                    if (clientKey.isBlank()) error("empty mtls key")
                    val certFile = cacheFile("mtls_cert")
                    certFile.writeText(it)
                    val keyFile = cacheFile("mtls_key")
                    keyFile.writeText(clientKey)
                    certPath = certFile.absolutePath
                    keyPath = keyFile.absolutePath
                }
            }
            buildMap<String, Any?> {
                put("server", address)
                put("auth", authPayload)
                put("fastOpen", true)
                put("lazy", true)
                if (obfsType.isNotBlank() && obfsPassword.isNotBlank()) {
                    put(
                        "obfs",
                        buildMap<String, Any?> {
                            put("type", obfsType)
                            put(
                                obfsType,
                                buildMap<String, Any?> {
                                    put("password", obfsPassword)
                                    if (obfsType == HysteriaBean.OBFS_TYPE_GECKO) {
                                        if (geckoMinPacketSize > 0) put(
                                            "minPacketSize",
                                            geckoMinPacketSize,
                                        )
                                        if (geckoMaxPacketSize > 0) put(
                                            "maxPacketSize",
                                            geckoMaxPacketSize,
                                        )
                                    }
                                },
                            )
                        },
                    )
                }
                val quic = buildMap<String, Any?> {
                    if (streamReceiveWindow > 0) put("initStreamReceiveWindow", streamReceiveWindow)
                    if (connectionReceiveWindow > 0) put("initConnReceiveWindow", connectionReceiveWindow)
                    idleTimeout.blankAsNull()?.let { put("maxIdleTimeout", it) }
                    keepAlivePeriod.blankAsNull()?.let { put("keepAlivePeriod", it) }
                    if (disableMtuDiscovery) put("disablePathMTUDiscovery", true)
                    if (disableChromeParrot) put("disableChromeParrot", true)
                    if (shouldProtect) {
                        put(
                            "sockopts",
                            buildMap { put("fdControlUnixSocket", Libcore.ProtectPath) },
                        )
                    }
                }
                if (quic.isNotEmpty()) put("quic", quic)
                put("socks5", buildMap { put("listen", "$LOCALHOST4:$port") })
                put(
                    "tls",
                    buildMap<String, Any?> {
                        put("sni", sni)
                        put("insecure", allowInsecure)
                        if (ech) echConfig.blankAsNull()?.let {
                            put("ech", it.toECHOneLine())
                        }
                        caPath?.let { put("ca", it) }
                        certPath?.let { put("clientCertificate", it) }
                        keyPath?.let { put("clientKey", it) }
                    },
                )
                put(
                    "transport",
                    buildMap<String, Any?> {
                        put("type", "udp")
                        put(
                            "udp",
                            buildMap<String, Any?> {
                                hopInterval.blankAsNull()?.let {
                                    when (val splitResult = SplitResult.splitDash(it)) {
                                        is SplitResult.Single -> put(
                                            "hopInterval",
                                            splitResult.value,
                                        )

                                        is SplitResult.Range -> {
                                            put("minHopInterval", splitResult.start)
                                            put("maxHopInterval", splitResult.end)
                                        }
                                    }
                                }
                            },
                        )
                    },
                )
                if (uploadSpeed > 0 || downloadSpeed > 0) {
                    put(
                        "bandwidth",
                        buildMap<String, Any?> {
                            if (uploadSpeed > 0) put("up", "$uploadSpeed mbps")
                            if (downloadSpeed > 0) put("down", "$downloadSpeed mbps")
                        },
                    )
                }
                put(
                    "congestion",
                    buildMap<String, Any?> {
                        put(
                            "type",
                            congestionControl.emptyAsNull() ?: HysteriaBean.CONGESTION_CONTROL_BBR,
                        )
                        when (congestionControl) {
                            "", HysteriaBean.CONGESTION_CONTROL_BBR -> {
                                put(
                                    "bbrProfile",
                                    when (bbrProfile) {
                                        HysteriaBean.BBR_PROFILE_CONSERVATIVE -> "conservative"
                                        HysteriaBean.BBR_PROFILE_STANDARD -> "standard"
                                        HysteriaBean.BBR_PROFILE_AGGRESSIVE -> "aggressive"
                                        else -> error("unreachable")
                                    },
                                )
                            }

                            else -> {}
                        }
                    },
                )
            }.toJsonStringKxs()
        }

        else -> throw error("unknown protocol version $protocolVersion")
    }
}

fun HysteriaBean.canUseSingBox(): Boolean {
    if (DataStore.providerHysteria2 != ProtocolProvider.CORE) return false // Force plugin
    if (protocolVersion == HysteriaBean.PROTOCOL_VERSION_1
        && protocol != HysteriaBean.PROTOCOL_UDP
    ) {
        return false // special mode
    }
    if (protocolVersion == HysteriaBean.PROTOCOL_VERSION_2 && disableChromeParrot) {
        return false // sing-box's Hysteria2 outbound has no Chrome parrot to disable
    }
    return true
}

fun buildSingBoxOutboundHysteriaBean(bean: HysteriaBean): SingBoxOptions.Outbound {
    return when (bean.protocolVersion) {
        HysteriaBean.PROTOCOL_VERSION_1 -> SingBoxOptions.Outbound_HysteriaOptions().apply {
            type = SingBoxOptions.TYPE_HYSTERIA
            server = bean.serverAddress
            when (val hopPort = HopPort.from(bean.serverPorts)) {
                is HopPort.Single -> server_port = hopPort.port
                is HopPort.Ports -> server_ports = hopPort.singStyle().toMutableList()
            }
            up_mbps = generateUploadSpeed()
            down_mbps = generateDownloadSpeed()
            obfs = bean.obfsPassword
            if (bean.disableMtuDiscovery) disable_path_mtu_discovery = true
            when (bean.authPayloadType) {
                HysteriaBean.TYPE_BASE64 -> auth = bean.authPayload
                HysteriaBean.TYPE_STRING -> auth_str = bean.authPayload
            }
            if (bean.streamReceiveWindow > 0) {
                stream_receive_window = bean.streamReceiveWindow
            }
            if (bean.connectionReceiveWindow > 0) {
                connection_receive_window = bean.connectionReceiveWindow
            }
            bean.idleTimeout.blankAsNull()?.let { idle_timeout = it }
            bean.keepAlivePeriod.blankAsNull()?.let { keep_alive_period = it }
            if (bean.maxConcurrentStreams > 0) {
                max_concurrent_streams = bean.maxConcurrentStreams
            }
            if (bean.initialPacketSize > 0) {
                initial_packet_size = bean.initialPacketSize
            }
            tls = SingBoxOptions.OutboundTLSOptions().apply {
                if (bean.sni.isNotBlank()) {
                    server_name = bean.sni
                }
                alpn = bean.alpn.blankAsNull()?.listByLineOrComma()?.toMutableList()
                certificate = bean.certificates.blankAsNull()?.lines()?.toMutableList()
                certificate_public_key_sha256 = bean.certPublicKeySha256
                    .blankAsNull()
                    ?.lines()
                    ?.toMutableList()
                if (bean.disableSNI) disable_sni = true
                if (bean.ech) {
                    ech = SingBoxOptions.OutboundECHOptions().apply {
                        enabled = true
                        config = bean.echConfig.blankAsNull()?.lines()?.toMutableList()
                        query_server_name = bean.echQueryServerName.blankAsNull()
                    }
                }
                insecure = bean.allowInsecure
                enabled = true
            }
        }

        HysteriaBean.PROTOCOL_VERSION_2 -> SingBoxOptions.Outbound_Hysteria2Options().apply {
            type = SingBoxOptions.TYPE_HYSTERIA2
            server = bean.serverAddress
            when (val hopPort = HopPort.from(bean.serverPorts)) {
                is HopPort.Single -> server_port = hopPort.port
                is HopPort.Ports -> {
                    bean.hopInterval.blankAsNull()?.let {
                        when (val splitResult = SplitResult.splitDash(it)) {
                            is SplitResult.Single -> hop_interval = splitResult.value
                            is SplitResult.Range -> {
                                hop_interval = splitResult.start
                                hop_interval_max = splitResult.end
                            }
                        }
                    }
                    server_ports = hopPort.singStyle().toMutableList()
                }
            }
            up_mbps = DataStore.uploadSpeed
            down_mbps = DataStore.downloadSpeed
            bean.obfsType.blankAsNull()?.let { obfsType ->
                obfs = SingBoxOptions.Hysteria2Obfs().apply {
                    type = obfsType
                    password = bean.obfsPassword
                    if (obfsType == HysteriaBean.OBFS_TYPE_GECKO) {
                        if (bean.geckoMinPacketSize > 0) min_packet_size = bean.geckoMinPacketSize
                        if (bean.geckoMaxPacketSize > 0) max_packet_size = bean.geckoMaxPacketSize
                    }
                }
            }
            password = bean.authPayload
            if (bean.disableMtuDiscovery) disable_path_mtu_discovery = true
            if (bean.streamReceiveWindow > 0) {
                stream_receive_window = bean.streamReceiveWindow
            }
            if (bean.connectionReceiveWindow > 0) {
                connection_receive_window = bean.connectionReceiveWindow
            }
            bean.idleTimeout.blankAsNull()?.let { idle_timeout = it }
            bean.keepAlivePeriod.blankAsNull()?.let { keep_alive_period = it }
            if (bean.maxConcurrentStreams > 0) {
                max_concurrent_streams = bean.maxConcurrentStreams
            }
            if (bean.initialPacketSize > 0) {
                initial_packet_size = bean.initialPacketSize
            }
            tls = SingBoxOptions.OutboundTLSOptions().apply {
                enabled = true
                if (bean.sni.isNotBlank()) {
                    server_name = bean.sni
                }
                alpn = mutableListOf("h3")
                certificate = bean.certificates.blankAsNull()?.lines()?.toMutableList()
                certificate_public_key_sha256 = bean.certPublicKeySha256
                    .blankAsNull()
                    ?.lines()
                    ?.toMutableList()
                if (bean.ech) ech = SingBoxOptions.OutboundECHOptions().apply {
                    enabled = true
                    config = bean.echConfig.blankAsNull()?.lines()?.toMutableList()
                    query_server_name = bean.echQueryServerName.blankAsNull()
                }
                if (bean.allowInsecure) insecure = true
                if (bean.disableSNI) disable_sni = true

                client_certificate = bean.clientCert.blankAsNull()?.lines()?.toMutableList()
                client_key = bean.clientKey.blankAsNull()?.lines()?.toMutableList()
            }
        }

        else -> throw error("unknown protocol version ${bean.protocolVersion}")
    }
}

private sealed interface SplitResult {
    data class Single(val value: String) : SplitResult
    data class Range(val start: String, val end: String) : SplitResult

    companion object {
        fun splitDash(input: String): SplitResult {
            val dashIndex = input.indexOf("-")
            return if (dashIndex >= 0) {
                Range(input.substring(0, dashIndex), input.substring(dashIndex + 1))
            } else {
                Single(input)
            }
        }
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
            if (ports.isBlank()) return Single(443)
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
        fun singStyle(): List<String> = raw.map {
            val basic = it.replace(HYSTERIA_RANGE, BOX_RANGE)
            if (basic.contains(BOX_RANGE)) {
                basic
            } else {
                basic + BOX_RANGE + basic
            }
        }

        fun hyStyle(): List<String> = raw.map { it.replace(BOX_RANGE, HYSTERIA_RANGE) }
    }
}

const val DEFAULT_SPEED = 10

// Just use for Hy1

private fun generateDownloadSpeed(): Int = DataStore.downloadSpeed.let {
    if (it <= 0) {
        DEFAULT_SPEED
    } else {
        it
    }
}

private fun generateUploadSpeed(): Int = DataStore.uploadSpeed.let {
    if (it <= 0) {
        DEFAULT_SPEED
    } else {
        it
    }
}

fun parseHysteria1Outbound(json: JSONMap): HysteriaBean = HysteriaBean().apply {
    protocolVersion = HysteriaBean.PROTOCOL_VERSION_1

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
            "obfs" -> obfsPassword = value.toString()

            "auth" -> {
                authPayloadType = HysteriaBean.TYPE_BASE64
                authPayload = value.toString()
            }

            "auth_str" -> {
                authPayloadType = HysteriaBean.TYPE_STRING
                authPayload = value.toString()
            }

            "tls" -> {
                loadTLS(parseBoxTLS(value as JSONMap))
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
                val obfsField = value as? JSONMap ?: continue
                obfsField.getStr("type")?.let { obfsType = it }
                obfsField.getStr("password")?.let { obfsPassword = it }
                obfsField.getIntOrNull("min_packet_size")?.let { geckoMinPacketSize = it }
                obfsField.getIntOrNull("max_packet_size")?.let { geckoMaxPacketSize = it }
            }

            "password" -> authPayload = value.toString()

            "tls" -> {
                loadTLS(parseBoxTLS(value as JSONMap))
            }

        }
    }

    serverPorts = tmpPorts.joinToString(HopPort.SPLIT_FLAG)
}

private fun HysteriaBean.loadTLS(tls: SingBoxOptions.OutboundTLSOptions) {
    sni = tls.server_name.orEmpty()
    allowInsecure = tls.insecure == true
    certificates = tls.certificate?.joinToString("\n").orEmpty()
    certPublicKeySha256 = tls.certificate_public_key_sha256?.joinToString("\n").orEmpty()
    alpn = tls.alpn?.joinToString("\n").orEmpty()
    tls.ech?.let {
        ech = it.enabled == true
        echConfig = it.config?.joinToString("\n").orEmpty()
        echQueryServerName = it.query_server_name.orEmpty()
    }
}
