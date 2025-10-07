package io.nekohasekai.sagernet.fmt.v2ray

import com.google.gson.Gson
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Outbound
import io.nekohasekai.sagernet.fmt.SingBoxOptions.OutboundECHOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.OutboundRealityOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.OutboundTLSOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.OutboundUTLSOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Outbound_HTTPOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Outbound_TrojanOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Outbound_VLESSOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.Outbound_VMessOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.TRANSPORT_GRPC
import io.nekohasekai.sagernet.fmt.SingBoxOptions.TRANSPORT_HTTP
import io.nekohasekai.sagernet.fmt.SingBoxOptions.TRANSPORT_HTTPUPGRADE
import io.nekohasekai.sagernet.fmt.SingBoxOptions.TRANSPORT_QUIC
import io.nekohasekai.sagernet.fmt.SingBoxOptions.TRANSPORT_WS
import io.nekohasekai.sagernet.fmt.SingBoxOptions.TYPE_TROJAN
import io.nekohasekai.sagernet.fmt.SingBoxOptions.TYPE_VLESS
import io.nekohasekai.sagernet.fmt.SingBoxOptions.TYPE_VMESS
import io.nekohasekai.sagernet.fmt.SingBoxOptions.V2RayTransportOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.V2RayTransportOptions_V2RayGRPCOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.V2RayTransportOptions_V2RayHTTPOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.V2RayTransportOptions_V2RayHTTPUpgradeOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.V2RayTransportOptions_V2RayQUICOptions
import io.nekohasekai.sagernet.fmt.SingBoxOptions.V2RayTransportOptions_V2RayWebsocketOptions
import io.nekohasekai.sagernet.fmt.buildSingBoxMux
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.listable
import io.nekohasekai.sagernet.fmt.parseBoxOutbound
import io.nekohasekai.sagernet.fmt.parseBoxTLS
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.ktx.JSONMap
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.b64Decode
import io.nekohasekai.sagernet.ktx.b64DecodeToString
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.forEach
import io.nekohasekai.sagernet.ktx.listByLineOrComma
import io.nekohasekai.sagernet.ktx.map
import io.nekohasekai.sagernet.ktx.queryParameterNotBlank
import io.nekohasekai.sagernet.ktx.queryParameterUnescapeNotBlank
import io.nekohasekai.sagernet.ktx.readableMessage
import libcore.Libcore
import libcore.URL
import org.json.JSONArray
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

fun StandardV2RayBean.shouldMux(): Boolean = serverMux && when (v2rayTransport) {
    "http" -> isTLS()
    "quic", "grpc" -> false
    else -> true
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

    val url = Libcore.parseURL(rawUrl)
    val bean = if (url.scheme == "vmess") {
        VMessBean()
    } else {
        VLESSBean()
    }
    bean.parseDuckSoft(url)

    return bean
}

const val BEGIN_ECH = "-----BEGIN ECH CONFIG-----"
const val END_ECH = "-----END ECH CONFIG-----"

// https://github.com/XTLS/Xray-core/discussions/716
fun StandardV2RayBean.parseDuckSoft(url: URL) {
    serverAddress = url.host
    serverPort = url.ports.toIntOrNull() ?: 443
    name = url.fragment

    if (this is TrojanBean) {
        password = url.username
    } else {
        uuid = url.username
    }

    v2rayTransport = url.queryParameter("type")
    if (v2rayTransport == "h2" || url.queryParameter("headerType") == "http") {
        v2rayTransport = "http"
    }

    security = url.queryParameter("security")
    when (security) {
        "tls", "reality" -> {
            security = "tls"
            sni = url.queryParameterNotBlank("sni") ?: url.queryParameterNotBlank("host")
            alpn = url.queryParameterNotBlank("alpn")
            certificates = url.queryParameterNotBlank("cert")
            realityPublicKey = url.queryParameterNotBlank("pbk")
            realityShortID = url.queryParameterNotBlank("sid")

            // Is DNS address: enable ECH and get config from DNS
            // Is base64: use it directly
            url.queryParameterUnescapeNotBlank("ech")?.let {
                ech = true

                val isEchConfig = try {
                    it.b64Decode().isNotEmpty()
                } catch (_: Exception) {
                    // Invalid or DNS address
                    false
                }
                if (isEchConfig) {
                    echConfig = "$BEGIN_ECH\n$it\n$END_ECH"
                }
            }
        }

        "" -> if (this is TrojanBean) {
            // The end users who use edge tunnel requires using VLESS without TLS.
            // Fortunately, they didn't pollute trojan's ecosystem.
            // And this standard force trojan's link to use TLS.
            // https://github.com/p4gefau1t/trojan-go/issues/132
            security = "tls"
        }
    }

    when (v2rayTransport) {
        "", "tcp" -> {}

        "http" -> {
            host = url.queryParameterNotBlank("host")
            path = url.queryParameterNotBlank("path")
        }

        "ws" -> {
            host = url.queryParameterNotBlank("host")
            path = url.queryParameterNotBlank("path")
            url.queryParameterNotBlank("ed")?.let { ed ->
                wsMaxEarlyData = ed.toIntOrNull() ?: 2048
                earlyDataHeaderName = url.queryParameterNotBlank("eh") ?: "Sec-WebSocket-Protocol"
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

    when (this) {
        is VMessBean -> {
            // maybe from Matsuri vmess export
            when (url.queryParameter("packetEncoding")) {
                "packetaddr" -> packetEncoding = 1
                "xudp" -> packetEncoding = 2
            }
            encryption = url.queryParameterNotBlank("encryption")
        }

        is VLESSBean -> {
            // maybe from Matsuri vmess export
            when (url.queryParameter("packetEncoding")) {
                "packetaddr" -> packetEncoding = 1
                "xudp" -> packetEncoding = 2
            }
            // TODO
            // encryption = url.queryParameterNotBlank("encryption")
            flow = url.queryParameterNotBlank("flow")?.removeSuffix("-udp443")
        }
    }

    utlsFingerprint = url.queryParameterNotBlank("fp")
}

// SagerNet's
// Do not support some format and then throw exception
fun parseV2RayN(link: String): VMessBean {
    val result = link.substringAfter("vmess://").b64DecodeToString()
    if (result.contains("= vmess")) {
        return parseCsvVMess(result)
    }
    val bean = VMessBean()
    val vmessQRCode = Gson().fromJson(result, VmessQRCode::class.java)

    // Although VmessQRCode fields are non null, looks like Gson may still create null fields
    @Suppress("UselessCallOnNotNull")
    if (vmessQRCode.add.isNullOrEmpty()
        || vmessQRCode.port.isNullOrBlank()
        || vmessQRCode.id.isNullOrBlank()
        || vmessQRCode.net.isNullOrBlank()
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
        "", "tcp" -> {
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
    var isVMess = false
    var isVLESS = false
    val protocol = when (this) {
        is TrojanBean -> {
            isTrojan = true
            "trojan"
        }

        is VMessBean -> {
            isVMess = true
            "vmess"
        }

        is VLESSBean -> {
            isVLESS = true
            "vless"
        }

        else -> error("impossible")
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
            this as VLESSBean
            builder.addQueryParameter("flow", flow)
            // builder.addQueryParameter("encryption", encryption)
        } else {
            this as VMessBean
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
        "", "tcp" -> {}
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
                // Xray requires a DNS server in ECH field, which is coupling.
                // We don't set a hard-coded DNS server here. 😅
                if (ech) echConfig.blankAsNull()?.let {
                    val config = it.removeSuffix("$BEGIN_ECH\n").removeSuffix("\n$END_ECH")
                    builder.setQueryParameter("ech", config)
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
        "", "tcp" -> {
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
        if (bean.allowInsecure) insecure = true
        if (bean.disableSNI) disable_sni = true
        if (bean.sni.isNotBlank()) server_name = bean.sni
        alpn = bean.alpn.blankAsNull()?.listByLineOrComma()
        certificate = bean.certificates.blankAsNull()?.split("\n")
        certificate_public_key_sha256 = bean.certPublicKeySha256.blankAsNull()?.split("\n")
        if (bean.fragment) {
            fragment = true
            fragment_fallback_delay = bean.fragmentFallbackDelay.blankAsNull()
        }
        if (bean.recordFragment) record_fragment = true
        var fingerprint = bean.utlsFingerprint
        if (bean.realityPublicKey.isNotBlank()) {
            reality = OutboundRealityOptions().apply {
                enabled = true
                public_key = bean.realityPublicKey
                short_id = bean.realityShortID
            }
            if (fingerprint.isNullOrBlank()) fingerprint = "chrome"
        }
        if (fingerprint.isNotBlank()) {
            utls = OutboundUTLSOptions().apply {
                enabled = true
                this.fingerprint = fingerprint
            }
        }
        if (bean.ech) {
            val echConfig = bean.echConfig.blankAsNull()?.split("\n")?.takeIf { it.isNotEmpty() }
            ech = OutboundECHOptions().apply {
                enabled = true
                config = echConfig
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

    is VMessBean -> Outbound_VMessOptions().apply {
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
        if (bean.shouldMux()) multiplex = buildSingBoxMux(bean)

        global_padding = true
        authenticated_length = bean.authenticatedLength
    }

    is VLESSBean -> Outbound_VLESSOptions().apply {
        type = bean.outboundType()
        server = bean.serverAddress
        server_port = bean.serverPort
        uuid = bean.uuid
        flow = bean.flow.blankAsNull()
        // encryption = bean.encryption.blankAsNull()
        packet_encoding = when (bean.packetEncoding) {
            1 -> "packetaddr"
            2 -> "xudp"
            else -> null
        }
        tls = buildSingBoxOutboundTLS(bean)
        transport = buildSingBoxOutboundStreamSettings(bean)
        if (flow == null && bean.shouldMux()) multiplex = buildSingBoxMux(bean)
    }

    is TrojanBean -> Outbound_TrojanOptions().apply {
        type = bean.outboundType()
        server = bean.serverAddress
        server_port = bean.serverPort
        password = bean.password
        tls = buildSingBoxOutboundTLS(bean)
        transport = buildSingBoxOutboundStreamSettings(bean)
        if (bean.shouldMux()) multiplex = buildSingBoxMux(bean)
    }

    else -> throw IllegalStateException()
}

fun parseStandardV2RayOutbound(json: JSONMap): StandardV2RayBean {
    var v2ray: StandardV2RayBean? = null
    var vmess: VMessBean? = null
    var vless: VLESSBean? = null
    var trojan: TrojanBean? = null
    val bean = when (json["type"]!!.toString()) {
        TYPE_VMESS -> VMessBean().also {
            v2ray = it
            vmess = it
        }

        TYPE_VLESS -> VLESSBean().also {
            v2ray = it
            vless = it
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
            "flow" -> vless?.flow = value.toString().removeSuffix("-udp443")
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
                        bean.host = transport.headers["host"]?.joinToString("\n")?.also {
                            transport.headers.remove("host")
                        } ?: ""
                        bean.headers = transport.headers.map { entry ->
                            entry.key + ":" + entry.value.joinToString(",")
                        }.joinToString("\n")
                        bean.path = transport.path
                        bean.wsMaxEarlyData = transport.max_early_data
                        bean.earlyDataHeaderName = transport.early_data_header_name
                    }

                    is V2RayTransportOptions_V2RayHTTPOptions -> {
                        bean.host = transport.host?.joinToString("\n")
                        bean.path = transport.path
                        bean.headers = transport.headers?.let {
                            parseHeader(it).map { entry ->
                                entry.key + ":" + entry.value.joinToString(",")
                            }.joinToString("\n")
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
                            parseHeader(it).map { entry ->
                                entry.key + ":" + entry.value.joinToString(",")
                            }.joinToString("\n")
                        }
                    }
                }
            }

            "tls" -> {
                val tls = parseBoxTLS((value as JSONObject).map)

                bean.setTLS(tls.enabled)
                bean.sni = tls.server_name
                bean.allowInsecure = tls.insecure
                bean.disableSNI = tls.disable_sni
                bean.alpn = tls.alpn?.joinToString(",")
                bean.certificates = tls.certificate?.joinToString("\n")
                bean.certPublicKeySha256 = tls.certificate_public_key_sha256?.joinToString("\n")
                bean.utlsFingerprint = tls.utls?.fingerprint
                bean.fragment = tls.fragment
                bean.fragmentFallbackDelay = tls.fragment_fallback_delay
                bean.recordFragment = tls.record_fragment
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

fun parseTransport(json: JSONMap): V2RayTransportOptions? = when (json["type"]?.toString()) {
    TRANSPORT_WS -> V2RayTransportOptions_V2RayWebsocketOptions().apply {
        type = TRANSPORT_WS
        headers = (json["headers"] as? JSONObject)?.map?.let {
            parseHeader(it)
        } ?: emptyMap()
        path = json["path"]?.toString()
        max_early_data = json["max_early_data"]?.toString()?.toIntOrNull()
        early_data_header_name = json["early_data_header_name"]?.toString()
    }

    TRANSPORT_HTTP -> V2RayTransportOptions_V2RayHTTPOptions().apply {
        type = TRANSPORT_HTTP
        host = listable<String>(json["host"])
        path = json["path"]?.toString()
        headers = (json["headers"] as? JSONObject)?.map?.let {
            parseHeader(it)
        } ?: emptyMap()
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
        headers = (json["headers"] as? JSONObject)?.map?.let {
            parseHeader(it)
        } ?: emptyMap()
    }

    else -> null
}

/** Aimed at parsing listable option. */
fun parseHeader(header: Map<*, *>): Map<String, List<String>> {
    val builder = LinkedHashMap<String, List<String>>(header.size)
    for (entry in header) {
        // http headers are case-insensitive, so we lowercase the key.
        val key = entry.key.toString().lowercase()
        val value = when (val entryValue = entry.value) {
            is List<*> -> {
                entryValue.map { it.toString() }
            }

            is JSONArray -> {
                val list = ArrayList<String>(entryValue.length())
                entryValue.forEach {
                    list.add(it.toString())
                }
                list
            }

            else -> {
                listOf(entryValue.toString())
            }
        }
        builder[key] = value
    }
    return builder
}