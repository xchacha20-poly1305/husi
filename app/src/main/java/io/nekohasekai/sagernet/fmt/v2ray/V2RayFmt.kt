package io.nekohasekai.sagernet.fmt.v2ray

import android.text.TextUtils
import com.google.gson.Gson
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.listable
import io.nekohasekai.sagernet.fmt.parseBoxOutbound
import io.nekohasekai.sagernet.fmt.parseBoxTLS
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.ktx.*
import libcore.Libcore
import libcore.URL
import moe.matsuri.nb4a.SingBoxOptions.*
import moe.matsuri.nb4a.utils.listByLineOrComma
import org.json.JSONObject

data class VmessQRCode(
    var v: String = "",
    var ps: String = "",
    var add: String = "",
    var port: String = "",
    var id: String = "",
    var aid: String = "0",
    var scy: String = "",
    var net: String = "",
    var packetEncoding: String = "",
    var type: String = "",
    var host: String = "",
    var path: String = "",
    var tls: String = "",
    var sni: String = "",
    var alpn: String = "",
    var fp: String = "",
)

fun StandardV2RayBean.isTLS(): Boolean {
    return security == "tls"
}

fun StandardV2RayBean.setTLS(boolean: Boolean) {
    security = if (boolean) "tls" else ""
}

fun parseV2Ray(rawUrl: String): StandardV2RayBean {
    // Try parse stupid formats first

    if (!rawUrl.contains("?")) {
        try {
            return parseV2RayN(rawUrl)
        } catch (e: Exception) {
            Logs.i("try v2rayN: " + e.readableMessage)
        }
    }

    // old V2Ray "std" format

    val bean = VMessBean().apply { if (rawUrl.startsWith("vless://")) alterId = -1 }
    val url = Libcore.parseURL(rawUrl)

    if (url.password.isNotBlank()) {
        // https://github.com/v2fly/v2fly-github-io/issues/26 (rarely use)
        bean.serverAddress = url.host
        bean.serverPort = url.ports.toIntOrNull()
        bean.name = url.fragment

        var protocol = url.username
        bean.v2rayTransport = protocol
        bean.alterId = url.password.substringAfterLast('-').toInt()
        bean.uuid = url.password.substringBeforeLast('-')

        if (protocol.endsWith("+tls")) {
            bean.security = "tls"
            protocol = protocol.substring(0, protocol.length - 4)

            url.queryParameterNotBlank("tlsServerName").let {
                if (it.isNotBlank()) {
                    bean.sni = it
                }
            }
        }

        when (protocol) {
//            "tcp" -> {
//                url.queryParameter("type")?.let { type ->
//                    if (type == "http") {
//                        bean.headerType = "http"
//                        url.queryParameter("host")?.let {
//                            bean.host = it
//                        }
//                    }
//                }
//            }
            "http" -> {
                bean.path = url.queryParameterNotBlank("path")
                bean.host = url.queryParameterNotBlank("host").split("|").joinToString(",")
            }

            "ws" -> {
                bean.path = url.queryParameterNotBlank("path")
                bean.host = url.queryParameterNotBlank("host")
            }

            "grpc" -> {
                bean.path = url.queryParameterNotBlank("serviceName")
            }

            "httpupgrade" -> {
                bean.path = url.queryParameterNotBlank("path")
                bean.host = url.queryParameterNotBlank("host")
            }
        }
    } else {
        bean.parseDuckSoft(url)
    }

    return bean
}

// https://github.com/XTLS/Xray-core/issues/91
fun StandardV2RayBean.parseDuckSoft(url: URL) {
    serverAddress = url.host
    serverPort = url.ports.toIntOrNull() ?: 443
    name = url.fragment

    if (this is TrojanBean) {
        password = url.username
    } else {
        uuid = url.username
    }

    v2rayTransport = url.queryParameterNotBlank("type")
    if (v2rayTransport.isNullOrBlank()) v2rayTransport = "tcp"
    if (v2rayTransport == "h2") v2rayTransport = "http"

    security = url.queryParameterNotBlank("security")
    if (security.isNullOrBlank()) {
        security = if (this is TrojanBean) "tls" else "none"
    }

    when (security) {
        "tls", "reality" -> {
            security = "tls"
            sni = url.queryParameterNotBlank("sni").ifBlank {
                url.queryParameterNotBlank("host")
            }
            alpn = url.queryParameterNotBlank("alpn")
            certificates = url.queryParameterNotBlank("cert")
            realityPublicKey = url.queryParameterNotBlank("pbk")
            realityShortID = url.queryParameterNotBlank("sid")
        }
    }

    when (v2rayTransport) {
        "tcp" -> {
            // v2rayNG
            if (url.queryParameterNotBlank("headerType") == "http") {
                url.queryParameterNotBlank("host").let {
                    v2rayTransport = "http"
                    host = it
                }
            }
        }

        "http" -> {
            host = url.queryParameterNotBlank("host")
            path = url.queryParameterNotBlank("path")
        }

        "ws" -> {
            host = url.queryParameterNotBlank("host")
            path = url.queryParameterNotBlank("path")
            url.queryParameterNotBlank("ed").let { ed ->
                if (ed.isNotBlank()) {
                    wsMaxEarlyData = ed.toIntOrNull() ?: 2048

                    url.queryParameterNotBlank("eh").let { eh ->
                        earlyDataHeaderName = eh.ifBlank {
                            "Sec-WebSocket-Protocol"
                        }
                    }
                }
            }
        }

        "grpc" -> {
            path = url.queryParameterNotBlank("serviceName")
        }

        "httpupgrade" -> {
            host = url.queryParameterNotBlank("host")
            path = url.queryParameterNotBlank("path")
        }
    }

    // maybe from Matsuri vmess export
    if (this is VMessBean) {
        when (url.queryParameterNotBlank("packetEncoding")) {
            "packetaddr" -> packetEncoding = 1
            "xudp" -> packetEncoding = 2
        }

        encryption = if (isVLESS) {
            url.queryParameterNotBlank("flow").removeSuffix("-udp443")
        } else {
            url.queryParameterNotBlank("encryption")
        }
    }

    utlsFingerprint = url.queryParameterNotBlank("fp")
}

// SagerNet's
// Do not support some format and then throw exception
fun parseV2RayN(link: String): VMessBean {
    val result = link.substringAfter("vmess://").decodeBase64UrlSafe()
    if (result.contains("= vmess")) {
        return parseCsvVMess(result)
    }
    val bean = VMessBean()
    val vmessQRCode = Gson().fromJson(result, VmessQRCode::class.java)

    // Although VmessQRCode fields are non null, looks like Gson may still create null fields
    if (TextUtils.isEmpty(vmessQRCode.add)
        || TextUtils.isEmpty(vmessQRCode.port)
        || TextUtils.isEmpty(vmessQRCode.id)
        || TextUtils.isEmpty(vmessQRCode.net)
    ) {
        throw Exception("invalid VmessQRCode")
    }

    bean.name = vmessQRCode.ps
    bean.serverAddress = vmessQRCode.add
    bean.serverPort = vmessQRCode.port.toIntOrNull() ?: 10086
    bean.encryption = vmessQRCode.scy
    bean.uuid = vmessQRCode.id
    bean.alterId = vmessQRCode.aid.toIntOrNull() ?: 0
    bean.v2rayTransport = vmessQRCode.net
    bean.host = vmessQRCode.host
    bean.path = vmessQRCode.path
    val headerType = vmessQRCode.type

    when (vmessQRCode.packetEncoding) {
        "packetaddr" -> {
            bean.packetEncoding = 1
        }

        "xudp" -> {
            bean.packetEncoding = 2
        }
    }

    when (bean.v2rayTransport) {
        "tcp" -> {
            if (headerType == "http") {
                bean.v2rayTransport = "http"
            }
        }
    }
    when (vmessQRCode.tls) {
        "tls", "reality" -> {
            bean.security = "tls"
            bean.sni = vmessQRCode.sni
            if (bean.sni.isNullOrBlank()) bean.sni = bean.host
            bean.alpn = vmessQRCode.alpn
            bean.utlsFingerprint = vmessQRCode.fp
        }
    }

    return bean
}

private fun parseCsvVMess(csv: String): VMessBean {

    val args = csv.split(",")

    val bean = VMessBean()

    bean.serverAddress = args[1]
    bean.serverPort = args[2].toInt()
    bean.encryption = args[3]
    bean.uuid = args[4].replace("\"", "")

    args.subList(5, args.size).forEach {

        when {
            it == "over-tls=true" -> bean.security = "tls"
            it.startsWith("tls-host=") -> bean.host = it.substringAfter("=")
            it.startsWith("obfs=") -> bean.v2rayTransport = it.substringAfter("=")
            it.startsWith("obfs-path=") || it.contains("Host:") -> {
                runCatching {
                    bean.path = it.substringAfter("obfs-path=\"").substringBefore("\"obfs")
                }
                runCatching {
                    bean.host = it.substringAfter("Host:").substringBefore("[")
                }

            }

        }

    }

    return bean

}

fun StandardV2RayBean.toUriVMessVLESSTrojan(): String {

    var isTrojan = false
    val protocol = if (this is VMessBean) {
        if (isVLESS) "vless" else "vmess"
    } else {
        isTrojan = true
        "trojan"
    }

    // ducksoft fmt
    val builder = Libcore.newURL(protocol).apply {
        username = if (isTrojan) {
            (this@toUriVMessVLESSTrojan as TrojanBean).password
        } else {
            uuid
        }
        host = serverAddress
        ports = serverPort.toString()
        addQueryParameter("type", v2rayTransport)
    }

    if (!isTrojan) {
        if (isVLESS) {
            builder.addQueryParameter("flow", encryption)
        } else {
            builder.addQueryParameter("encryption", encryption)
        }
        when (packetEncoding) {
            1 -> {
                builder.addQueryParameter("packetEncoding", "packetaddr")
            }

            2 -> {
                builder.addQueryParameter("packetEncoding", "xudp")
            }
        }
    }

    when (v2rayTransport) {
        "tcp" -> {}
        "ws", "http", "httpupgrade" -> {
            if (host.isNotBlank()) {
                builder.addQueryParameter("host", host)
            }
            if (path.isNotBlank()) {
                builder.addQueryParameter("path", path)
            }
            if (v2rayTransport == "ws") {
                if (wsMaxEarlyData > 0) {
                    builder.addQueryParameter("ed", "$wsMaxEarlyData")
                    if (earlyDataHeaderName.isNotBlank()) {
                        builder.addQueryParameter("eh", earlyDataHeaderName)
                    }
                }
            } else if (v2rayTransport == "http" && !isTLS()) {
                builder.setQueryParameter("type", "tcp")
                builder.addQueryParameter("headerType", "http")
            }
        }

        "grpc" -> {
            if (path.isNotBlank()) {
                builder.setQueryParameter("serviceName", path)
            }
        }
    }

    if (security.isNotBlank() && security != "none") {
        builder.addQueryParameter("security", security)
        when (security) {
            "tls" -> {
                if (sni.isNotBlank()) {
                    builder.addQueryParameter("sni", sni)
                }
                if (alpn.isNotBlank()) {
                    builder.addQueryParameter("alpn", alpn.replace("\n", ","))
                }
                if (certificates.isNotBlank()) {
                    builder.addQueryParameter("cert", certificates)
                }
                if (allowInsecure) {
                    builder.addQueryParameter("allowInsecure", "1")
                }
                if (utlsFingerprint.isNotBlank()) {
                    builder.addQueryParameter("fp", utlsFingerprint)
                }
                if (realityPublicKey.isNotBlank()) {
                    builder.setQueryParameter("security", "reality")
                    builder.addQueryParameter("pbk", realityPublicKey)
                    builder.addQueryParameter("sid", realityShortID)
                }
            }
        }
    }

    if (name.isNotBlank()) {
        builder.fragment = name
    }

    return builder.string
}

fun buildSingBoxOutboundStreamSettings(bean: StandardV2RayBean): V2RayTransportOptions? {
    when (bean.v2rayTransport) {
        "tcp" -> {
            return null
        }

        "ws" -> {
            return V2RayTransportOptions_V2RayWebsocketOptions().apply {
                type = TRANSPORT_WS
                headers = mutableMapOf()
                for (line in bean.headers.lines()) {
                    val pair = line.split(":", limit = 2)
                    if (pair.size == 2) {
                        headers[pair[0].trim()] = listOf(pair[1].trim())
                    }
                }

                if (bean.host.isNotBlank()) {
                    headers["Host"] = bean.host.listByLineOrComma()
                }

                if (bean.path.contains("?ed=")) {
                    path = bean.path.substringBefore("?ed=")
                    max_early_data = bean.path.substringAfter("?ed=").toIntOrNull() ?: 2048
                    early_data_header_name = "Sec-WebSocket-Protocol"
                } else {
                    path = bean.path.takeIf { it.isNotBlank() } ?: "/"
                }

                if (bean.wsMaxEarlyData > 0) {
                    max_early_data = bean.wsMaxEarlyData
                }

                if (bean.earlyDataHeaderName.isNotBlank()) {
                    early_data_header_name = bean.earlyDataHeaderName
                }
            }
        }

        "http" -> {
            return V2RayTransportOptions_V2RayHTTPOptions().apply {
                type = TRANSPORT_HTTP
                if (!bean.isTLS()) method = "GET" // v2ray tcp header
                if (bean.host.isNotBlank()) {
                    host = bean.host.listByLineOrComma()
                }
                path = bean.path.takeIf { it.isNotBlank() } ?: "/"

                headers = mutableMapOf()
                for (line in bean.headers.lines()) {
                    val pair = line.split(":", limit = 2)
                    if (pair.size == 2) {
                        headers[pair[0].trim()] = listOf(pair[1].trim())
                    }
                }
            }
        }

        "quic" -> {
            return V2RayTransportOptions().apply {
                type = TRANSPORT_QUIC
            }
        }

        "grpc" -> {
            return V2RayTransportOptions_V2RayGRPCOptions().apply {
                type = TRANSPORT_GRPC
                service_name = bean.path
            }
        }

        "httpupgrade" -> {
            return V2RayTransportOptions_V2RayHTTPUpgradeOptions().apply {
                type = TRANSPORT_HTTPUPGRADE
                host = bean.host.listByLineOrComma().firstOrNull()
                path = bean.path

                headers = mutableMapOf()
                for (line in bean.headers.lines()) {
                    val pair = line.split(":", limit = 2)
                    if (pair.size == 2) {
                        headers[pair[0].trim()] = listOf(pair[1].trim())
                    }
                }
            }
        }
    }

    return null
}

fun buildSingBoxOutboundTLS(bean: StandardV2RayBean): OutboundTLSOptions? {
    if (bean.security != "tls") return null
    return OutboundTLSOptions().apply {
        enabled = true
        insecure = bean.allowInsecure
        if (bean.sni.isNotBlank()) server_name = bean.sni
        if (bean.alpn.isNotBlank()) alpn = bean.alpn.listByLineOrComma()
        if (bean.certificates.isNotBlank()) certificate = listOf(bean.certificates)
        var fp = bean.utlsFingerprint
        if (bean.realityPublicKey.isNotBlank()) {
            reality = OutboundRealityOptions().apply {
                enabled = true
                public_key = bean.realityPublicKey
                short_id = bean.realityShortID
            }
            if (fp.isNullOrBlank()) fp = "chrome"
        }
        if (fp.isNotBlank()) {
            utls = OutboundUTLSOptions().apply {
                enabled = true
                fingerprint = fp
            }
        }
        if (bean.ech) {
            val echList = bean.echConfig.split("\n")
            ech = OutboundECHOptions().apply {
                enabled = true
                pq_signature_schemes_enabled = echList.size > 5
                dynamic_record_sizing_disabled = true
                config = echList

            }
        }
    }
}

fun buildSingBoxOutboundStandardV2RayBean(bean: StandardV2RayBean): Outbound = when (bean) {
    is HttpBean -> Outbound_HTTPOptions().apply {
        type = bean.outboundType()
        server = bean.serverAddress
        server_port = bean.serverPort
        username = bean.username
        password = bean.password
        path = bean.path
        tls = buildSingBoxOutboundTLS(bean)

        headers = mutableMapOf()
        for (line in bean.headers.lines()) {
            val pair = line.split(":", limit = 2)
            if (pair.size == 2) {
                headers[pair[0].trim()] = listOf(pair[1].trim())
            }
        }
    }

    is VMessBean -> if (bean.isVLESS) Outbound_VLESSOptions().apply {
        type = bean.outboundType()
        server = bean.serverAddress
        server_port = bean.serverPort
        uuid = bean.uuid
        if (bean.encryption.isNotBlank() && bean.encryption != "auto") {
            flow = bean.encryption
        }
        packet_encoding = when (bean.packetEncoding) {
            1 -> "packetaddr"
            2 -> "xudp"
            else -> null
        }
        tls = buildSingBoxOutboundTLS(bean)
        transport = buildSingBoxOutboundStreamSettings(bean)
    } else Outbound_VMessOptions().apply {
        type = bean.outboundType()
        server = bean.serverAddress
        server_port = bean.serverPort
        uuid = bean.uuid
        alter_id = bean.alterId
        security = bean.encryption.takeIf { it.isNotBlank() } ?: "auto"
        packet_encoding = when (bean.packetEncoding) {
            1 -> "packetaddr"
            2 -> "xudp"
            else -> null
        }
        tls = buildSingBoxOutboundTLS(bean)
        transport = buildSingBoxOutboundStreamSettings(bean)

        global_padding = true
        authenticated_length = bean.authenticatedLength
    }

    is TrojanBean -> Outbound_TrojanOptions().apply {
        type = bean.outboundType()
        server = bean.serverAddress
        server_port = bean.serverPort
        password = bean.password
        tls = buildSingBoxOutboundTLS(bean)
        transport = buildSingBoxOutboundStreamSettings(bean)
    }

    else -> throw IllegalStateException()
}

@Suppress("UNCHECKED_CAST")
fun parseStandardV2RayOutbound(json: JSONMap): StandardV2RayBean {
    var v2ray: VMessBean? = null
    var vmess: VMessBean? = null
    var vless: VMessBean? = null
    var trojan: TrojanBean? = null
    val bean = when (json["type"]!!.toString()) {
        TYPE_VMESS -> VMessBean().also {
            v2ray = it
            vmess = it
        }

        TYPE_VLESS -> VMessBean().also {
            v2ray = it
            vless = it
            it.alterId = -1
        }

        TYPE_TROJAN -> TrojanBean().also {
            trojan = it
        }

        else -> throw IllegalStateException()
    }

    bean.parseBoxOutbound(json) { key, value ->
        when (key) {
            "alter_id" -> vmess?.alterId = value.toString().toIntOrNull()
            "authenticated_length" -> vmess?.authenticatedLength = value.toString().toBoolean()
            "uuid" -> v2ray?.uuid = value.toString()
            "flow" -> vless?.encryption = value.toString().removeSuffix("-udp443")
            "password" -> trojan?.password = value.toString()
            "security" -> vmess?.security = value.toString()
            "packet_encoding" -> v2ray?.packetEncoding = when (value.toString()) {
                "packetaddr" -> 1
                "xudp" -> 2
                else -> 0
            }

            "transport" -> {
                val transport = parseTransport((value as JSONObject).map) ?: return@parseBoxOutbound

                bean.v2rayTransport = transport.type
                when (transport) {
                    is V2RayTransportOptions_V2RayWebsocketOptions -> {
                        bean.host = transport.headers["Host"]?.joinToString("\n") ?: ""
                        bean.path = transport.path
                        bean.wsMaxEarlyData = transport.max_early_data
                        bean.earlyDataHeaderName = transport.early_data_header_name
                    }

                    is V2RayTransportOptions_V2RayHTTPOptions -> {
                        bean.host = transport.host?.joinToString("\n")
                        bean.path = transport.path
                        bean.headers = transport.headers?.let {
                            headerToString(it)
                        }
                    }

                    is V2RayTransportOptions_V2RayQUICOptions -> {}

                    is V2RayTransportOptions_V2RayGRPCOptions -> {
                        bean.path = transport.service_name
                    }

                    is V2RayTransportOptions_V2RayHTTPUpgradeOptions -> {
                        bean.host = transport.host
                        bean.path = transport.path
                        bean.headers = transport.headers?.let {
                            headerToString(it)
                        }
                    }
                }
            }

            "tls" -> {
                val tls = parseBoxTLS((value as JSONObject).map)

                bean.setTLS(tls.enabled)
                bean.sni = tls.server_name
                bean.allowInsecure = tls.insecure
                bean.alpn = tls.alpn?.joinToString(",")
                bean.certificates = tls.certificate?.joinToString("\n")
                bean.utlsFingerprint = tls.utls?.fingerprint
                tls.ech?.let {
                    bean.ech = it.enabled
                    bean.echConfig = it.config?.joinToString("\n")
                }
                tls.reality?.let {
                    bean.realityPublicKey = it.public_key
                    bean.realityShortID = it.short_id
                }
            }
        }
    }

    return bean
}

@Suppress("UNCHECKED_CAST")
fun parseTransport(json: JSONMap): V2RayTransportOptions? = when (json["type"]?.toString()) {
    TRANSPORT_WS -> V2RayTransportOptions_V2RayWebsocketOptions().apply {
        type = TRANSPORT_WS
        headers = json["headers"] as? Map<String, List<String>> ?: emptyMap()
        path = json["path"]?.toString()
        max_early_data = json["max_early_data"]?.toString()?.toIntOrNull()
        early_data_header_name = json["early_data_header_name"]?.toString()
    }

    TRANSPORT_HTTP -> V2RayTransportOptions_V2RayHTTPOptions().apply {
        type = TRANSPORT_HTTP
        host = listable<String>(json["host"])
        path = json["path"]?.toString()
        headers = json["headers"] as? Map<String, List<String>> ?: emptyMap()
    }

    TRANSPORT_QUIC -> V2RayTransportOptions_V2RayQUICOptions().apply {
        type = TRANSPORT_QUIC
    }

    TRANSPORT_GRPC -> V2RayTransportOptions_V2RayGRPCOptions().apply {
        type = TRANSPORT_GRPC
        service_name = json["service_name"]?.toString()
    }

    TRANSPORT_HTTPUPGRADE -> V2RayTransportOptions_V2RayHTTPUpgradeOptions().apply {
        type = TRANSPORT_HTTPUPGRADE
        host = json["host"]?.toString()
        path = json["path"]?.toString()
        headers = json["headers"] as? Map<String, List<String>> ?: emptyMap()
    }

    else -> null
}

fun headerToString(header: Map<*, *>): String {
    val builder = ArrayList<String>(header.size)
    for (entry in header) {
        builder.add(entry.key.toString() + ":" + entry.value.toString())
    }
    return builder.joinToString("\n")
}