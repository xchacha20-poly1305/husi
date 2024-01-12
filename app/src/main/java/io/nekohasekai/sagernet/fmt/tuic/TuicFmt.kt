package io.nekohasekai.sagernet.fmt.tuic

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.SingBoxOptions.OutboundECHOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseTuic(url: String): TuicBean {
    // https://github.com/daeuniverse/dae/discussions/182
    val link = url.replace("tuic://", "https://").toHttpUrlOrNull() ?: error(
        "invalid tuic link $url"
    )
    return TuicBean().apply {
        name = link.fragment
        uuid = link.username
        token = link.password
        serverAddress = link.host
        serverPort = link.port

        link.queryParameter("sni")?.let {
            sni = it
        }
        link.queryParameter("congestion_control")?.let {
            congestionController = it
        }
        link.queryParameter("udp_relay_mode")?.let {
            udpRelayMode = it
        }
        link.queryParameter("alpn")?.let {
            alpn = it
        }
        link.queryParameter("allow_insecure")?.let {
            if (it == "1") allowInsecure = true
        }
        link.queryParameter("disable_sni")?.let {
            if (it == "1") disableSNI = true
        }
    }
}

fun TuicBean.toUri(): String {
    val builder = linkBuilder().username(uuid).password(token).host(serverAddress).port(serverPort)

    builder.addQueryParameter("congestion_control", congestionController)
    var udpMode = udpRelayMode
    if (udpMode == "UDP over Stream") udpMode = "native"
    builder.addQueryParameter("udp_relay_mode", udpMode)

    if (sni.isNotBlank()) builder.addQueryParameter("sni", sni)
    if (alpn.isNotBlank()) builder.addQueryParameter("alpn", alpn)
    if (allowInsecure) builder.addQueryParameter("allow_insecure", "1")
    if (disableSNI) builder.addQueryParameter("disable_sni", "1")
    if (name.isNotBlank()) builder.encodedFragment(name.urlSafe())

    return builder.toLink("tuic")
}

fun buildSingBoxOutboundTuicBean(bean: TuicBean): SingBoxOptions.Outbound_TUICOptions {
    return SingBoxOptions.Outbound_TUICOptions().apply {
        type = "tuic"
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
                certificate = bean.caText
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
            insecure = bean.allowInsecure || DataStore.globalAllowInsecure
            enabled = true
        }
    }
}
