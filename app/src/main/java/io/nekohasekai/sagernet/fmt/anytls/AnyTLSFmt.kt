package io.nekohasekai.sagernet.fmt.anytls

import io.nekohasekai.sagernet.ktx.blankAsNull
import libcore.Libcore
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma

/** https://github.com/anytls/anytls-go/blob/main/docs/uri_scheme.md */
fun parseAnyTLS(link: String): AnyTLSBean = AnyTLSBean().apply {
    val url = Libcore.parseURL(link)

    serverAddress = url.host
    serverPort = url.ports.toIntOrNull() ?: 443
    password = url.username
    serverName = url.queryParameterNotBlank("sni")
    allowInsecure = url.queryParameterNotBlank("insecure") == "1"
}

fun AnyTLSBean.toUri(): String {
    val url = Libcore.newURL("anytls").apply {
        host = serverAddress
    }

    if (serverPort in 1..65535) {
        url.ports = serverPort.toString()
    }

    password?.blankAsNull()?.let { url.username = it }
    serverName?.blankAsNull()?.let { url.addQueryParameter("sni", it) }
    if (allowInsecure) {
        url.addQueryParameter("insecure", "1")
    }

    return url.string
}

fun buildSingBoxOutboundAnyTLSBean(bean: AnyTLSBean): SingBoxOptions.Outbound_AnyTLSOptions {
    return SingBoxOptions.Outbound_AnyTLSOptions().apply {
        type = bean.outboundType()
        server = bean.serverAddress
        server_port = bean.serverPort
        password = bean.password
        idle_session_check_interval = bean.idleSessionCheckInterval.blankAsNull()
        idle_session_timeout = bean.idleSessionTimeout.blankAsNull()
        min_idle_session = bean.minIdleSession.takeIf { it > 0 }

        tls = SingBoxOptions.OutboundTLSOptions().apply {
            enabled = true
            server_name = bean.serverName.blankAsNull()
            if (bean.allowInsecure) insecure = true
            alpn = bean.alpn.blankAsNull()?.listByLineOrComma()
            bean.certificates.blankAsNull()?.let {
                certificate = listOf(it)
            }
            bean.utlsFingerprint.blankAsNull()?.let {
                utls = SingBoxOptions.OutboundUTLSOptions().apply {
                    enabled = true
                    fingerprint = it
                }
            }
            bean.echConfig.blankAsNull()?.let {
                // In new version, some complex options will be deprecated, so we just do this.
                ech = SingBoxOptions.OutboundECHOptions().apply {
                    enabled = true
                    config = listOf(it)
                }
            }
        }
    }
}