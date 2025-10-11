package io.nekohasekai.sagernet.fmt.anytls

import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.fmt.parseBoxOutbound
import io.nekohasekai.sagernet.fmt.parseBoxTLS
import io.nekohasekai.sagernet.ktx.JSONMap
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.listByLineOrComma
import io.nekohasekai.sagernet.ktx.map
import libcore.Libcore
import org.json.JSONObject

/** https://github.com/anytls/anytls-go/blob/main/docs/uri_scheme.md */
fun parseAnyTLS(link: String): AnyTLSBean = AnyTLSBean().apply {
    val url = Libcore.parseURL(link)

    serverAddress = url.host
    serverPort = url.ports.toIntOrNull() ?: 443
    password = url.username
    serverName = url.queryParameter("sni")
    allowInsecure = url.queryParameter("insecure") == "1"
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
            if (bean.disableSNI) disable_sni = true
            alpn = bean.alpn.blankAsNull()?.listByLineOrComma()
            bean.certificates.blankAsNull()?.let {
                certificate = listOf(it)
            }
            bean.clientCert.blankAsNull()?.lines()?.let {
                client_certificate = it
            }
            bean.clientKey.blankAsNull()?.lines()?.let {
                client_key = it
            }
            certificate_public_key_sha256 = bean.certPublicKeySha256.blankAsNull()?.split("\n")
            bean.utlsFingerprint.blankAsNull()?.let {
                utls = SingBoxOptions.OutboundUTLSOptions().apply {
                    enabled = true
                    fingerprint = it
                }
            }
            if (bean.tlsFragment) {
                fragment = true
                fragment_fallback_delay = bean.tlsFragmentFallbackDelay.blankAsNull()
            }
            if (bean.tlsRecordFragment) record_fragment = true
            if (bean.ech) {
                val echConfig =
                    bean.echConfig.blankAsNull()?.split("\n")?.takeIf { it.isNotEmpty() }
                ech = SingBoxOptions.OutboundECHOptions().apply {
                    enabled = true
                    config = echConfig
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun parseAnyTLSOutbound(json: JSONMap): AnyTLSBean = AnyTLSBean().apply {
    parseBoxOutbound(json) { key, value ->
        when (key) {
            "password" -> password = value.toString()
            "idle_session_check_interval" -> idleSessionCheckInterval = value.toString()
            "idle_session_timeout" -> idleSessionTimeout = value.toString()
            "min_idle_session" -> minIdleSession = value.toString().toIntOrNull()

            "tls" -> {
                val tlsField = (value as? JSONObject)?.map ?: return@parseBoxOutbound
                val tls = parseBoxTLS(tlsField)
                serverName = tls.server_name
                allowInsecure = tls.insecure
                disableSNI = tls.disable_sni
                alpn = tls.alpn?.joinToString(",")
                certificates = tls.certificate?.joinToString("\n")
                clientCert = tls.client_certificate?.joinToString("\n")
                clientKey = tls.client_key?.joinToString("\n")
                certPublicKeySha256 = tls.certificate_public_key_sha256?.joinToString("\n")
                utlsFingerprint = tls.utls?.fingerprint
                tlsFragment = tls.fragment
                tlsFragmentFallbackDelay = tls.fragment_fallback_delay
                tlsRecordFragment = tls.record_fragment
                tls.ech?.let {
                    // ech = it.enabled
                    echConfig = it.config.joinToString("\n")
                }
            }
        }
    }
}