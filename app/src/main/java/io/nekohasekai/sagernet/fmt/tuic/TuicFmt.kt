package io.nekohasekai.sagernet.fmt.tuic

import libcore.Libcore
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.SingBoxOptions.OutboundECHOptions
import moe.matsuri.nb4a.utils.listByLineOrComma

// https://github.com/daeuniverse/dae/discussions/182
fun parseTuic(link: String): TuicBean {
    val url = Libcore.parseURL(link)
    return TuicBean().apply {
        name = url.fragment
        uuid = url.username
        try {
            token = url.password
        } catch (_: Exception) {
        }
        serverAddress = url.host
        serverPort = url.ports.toIntOrNull() ?: 443

        url.queryParameterNotBlank("sni").let {
            sni = it
        }
        url.queryParameterNotBlank("congestion_control").let {
            congestionController = it
        }
        url.queryParameterNotBlank("udp_relay_mode").let {
            udpRelayMode = it
        }
        url.queryParameterNotBlank("alpn").let {
            alpn = it
        }
        url.queryParameterNotBlank("allow_insecure").let {
            if (it == "1") allowInsecure = true
        }
        url.queryParameterNotBlank("disable_sni").let {
            if (it == "1") disableSNI = true
        }
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
        type = bean.outboundType()
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
        zero_rtt_handshake = bean.reduceRTT
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
            disable_sni = bean.disableSNI
            insecure = bean.allowInsecure
            enabled = true
        }
    }
}
