package fr.husi.fmt.tuic

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.SingBoxOptions.OutboundECHOptions
import fr.husi.fmt.parseBoxOutbound
import fr.husi.fmt.parseBoxTLS
import fr.husi.ktx.JSONMap
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.listByLineOrComma
import fr.husi.ktx.parseBoolean
import fr.husi.libcore.Libcore

// https://github.com/daeuniverse/dae/discussions/182
fun parseTuic(link: String): TuicBean {
    val url = Libcore.parseURL(link)
    return TuicBean().apply {
        name = url.fragment
        uuid = url.username
        token = url.password
        serverAddress = url.host
        serverPort = url.ports.toIntOrNull() ?: 443

        sni = url.queryParameter("sni")
        congestionController = url.queryParameter("congestion_control")
        udpRelayMode = url.queryParameter("udp_relay_mode")
        alpn = url.queryParameter("alpn")
        allowInsecure = url.parseBoolean("allow_insecure")
        disableSNI = url.parseBoolean("disable_sni")
    }
}

fun TuicBean.toUri(): String {
    val builder = Libcore.newURL("tuic").apply {
        username = uuid
        password = token
        host = serverAddress
        ports = serverPort.toString()
    }

    builder.addQueryParameter("congestion_control", congestionController)
    var udpMode = udpRelayMode
    if (udpMode == "UDP over Stream") udpMode = "native"
    builder.addQueryParameter("udp_relay_mode", udpMode)

    if (sni.isNotBlank()) builder.addQueryParameter("sni", sni)
    if (alpn.isNotBlank()) builder.addQueryParameter("alpn", alpn)
    if (allowInsecure) builder.addQueryParameter("allow_insecure", "1")
    if (disableSNI) builder.addQueryParameter("disable_sni", "1")
    if (name.isNotBlank()) builder.fragment = name

    return builder.string
}

fun buildSingBoxOutboundTuicBean(bean: TuicBean): SingBoxOptions.Outbound_TUICOptions {
    return SingBoxOptions.Outbound_TUICOptions().apply {
        type = SingBoxOptions.TYPE_TUIC
        server = bean.serverAddress
        server_port = bean.serverPort
        uuid = bean.uuid
        password = bean.token
        congestion_control = bean.congestionController
        if (bean.udpRelayMode == "UDP over Stream") {
            udp_over_stream = true
        } else {
            udp_relay_mode = bean.udpRelayMode
            udp_over_stream = false
        }
        zero_rtt_handshake = bean.zeroRTT
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
            client_certificate = bean.clientCert.blankAsNull()?.lines()?.toMutableList()
            client_key = bean.clientKey.blankAsNull()?.lines()?.toMutableList()
            if (bean.ech) {
                ech = OutboundECHOptions().apply {
                    enabled = true
                    config = bean.echConfig.blankAsNull()?.lines()?.toMutableList()
                    query_server_name = bean.echQueryServerName.blankAsNull()
                }
            }
            disable_sni = bean.disableSNI
            insecure = bean.allowInsecure
            enabled = true
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun parseTuicOutbound(json: JSONMap): TuicBean = TuicBean().apply {
    parseBoxOutbound(json) { key, value ->
        when (key) {
            "uuid" -> uuid = value.toString()
            "password" -> token = value.toString()
            "congestion_control" -> congestionController = value.toString()
            "udp_relay_mode" -> udpRelayMode = value.toString()
            "zero_rtt_handshake" -> zeroRTT = value.toString().toBoolean()

            "udp_over_stream" -> if (value.toString().toBoolean()) {
                udpRelayMode = "UDP over Stream"
            }

            "tls" -> {
                val tlsField = value as? JSONMap ?: return@parseBoxOutbound
                val tls = parseBoxTLS(tlsField)

                sni = tls.server_name.orEmpty()
                allowInsecure = tls.insecure == true
                disableSNI = tls.disable_sni == true
                certificates = tls.certificate?.joinToString("\n").orEmpty()
                certPublicKeySha256 =
                    tls.certificate_public_key_sha256?.joinToString("\n").orEmpty()
                clientCert = tls.client_certificate?.joinToString("\n").orEmpty()
                clientKey = tls.client_key?.joinToString("\n").orEmpty()
                alpn = tls.alpn?.joinToString("\n").orEmpty()
                tls.ech?.let {
                    ech = it.enabled == true
                    echConfig = it.config?.joinToString("\n").orEmpty()
                    echQueryServerName = it.query_server_name.orEmpty()
                }
            }
        }
    }
}