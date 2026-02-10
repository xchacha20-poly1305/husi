package fr.husi.fmt.anytls

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.parseBoxOutbound
import fr.husi.fmt.parseBoxTLS
import fr.husi.ktx.JSONMap
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.listByLineOrComma
import fr.husi.libcore.Libcore

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

    password.blankAsNull()?.let { url.username = it }
    serverName.blankAsNull()?.let { url.addQueryParameter("sni", it) }
    if (allowInsecure) {
        url.addQueryParameter("insecure", "1")
    }

    return url.string
}

fun buildSingBoxOutboundAnyTLSBean(bean: AnyTLSBean): SingBoxOptions.Outbound_AnyTLSOptions {
    return SingBoxOptions.Outbound_AnyTLSOptions().apply {
        type = SingBoxOptions.TYPE_ANYTLS
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
            alpn = bean.alpn.blankAsNull()?.listByLineOrComma()?.toMutableList()
            certificate = bean.certificates.blankAsNull()?.lines()?.toMutableList()
            client_certificate = bean.clientCert.blankAsNull()?.lines()?.toMutableList()
            client_key = bean.clientKey.blankAsNull()?.lines()?.toMutableList()
            certificate_public_key_sha256 =
                bean.certPublicKeySha256.blankAsNull()?.lines()?.toMutableList()
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
                ech = SingBoxOptions.OutboundECHOptions().apply {
                    enabled = true
                    config = bean.echConfig.blankAsNull()?.lines()?.toMutableList()
                    query_server_name = bean.echQueryServerName.blankAsNull()
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
            "min_idle_session" -> value.toString().toIntOrNull()?.let {
                minIdleSession = it
            }

            "tls" -> {
                val tlsField = value as? JSONMap ?: return@parseBoxOutbound
                val tls = parseBoxTLS(tlsField)
                serverName = tls.server_name.orEmpty()
                allowInsecure = tls.insecure == true
                disableSNI = tls.disable_sni == true
                alpn = tls.alpn?.joinToString(",").orEmpty()
                certificates = tls.certificate?.joinToString("\n").orEmpty()
                clientCert = tls.client_certificate?.joinToString("\n").orEmpty()
                clientKey = tls.client_key?.joinToString("\n").orEmpty()
                certPublicKeySha256 =
                    tls.certificate_public_key_sha256?.joinToString("\n").orEmpty()
                utlsFingerprint = tls.utls?.fingerprint.orEmpty()
                tlsFragment = tls.fragment == true
                tlsFragmentFallbackDelay = tls.fragment_fallback_delay.orEmpty()
                tlsRecordFragment = tls.record_fragment == true
                tls.ech?.let {
                    ech = it.enabled == true
                    it.config?.joinToString("\n")?.let {
                        echConfig = it
                    }
                    echQueryServerName = it.query_server_name.orEmpty()
                }
            }
        }
    }
}