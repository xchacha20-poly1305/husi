package fr.husi.fmt.masque

import fr.husi.fmt.HttpVersion
import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.buildHeader
import fr.husi.fmt.parseBoxOutbound
import fr.husi.fmt.parseBoxTLS
import fr.husi.fmt.parseHeader
import fr.husi.ktx.JSONMap
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.listByLineOrComma

fun buildSingBoxEndpointMASQUEBean(bean: MASQUEBean): SingBoxOptions.Endpoint_MASQUEClientOptions {
    return SingBoxOptions.Endpoint_MASQUEClientOptions().apply {
        type = SingBoxOptions.TYPE_MASQUE_CLIENT
        server = bean.serverAddress
        server_port = bean.serverPort
        username = bean.username.blankAsNull()
        password = bean.password.blankAsNull()
        path = bean.path.blankAsNull()
        headers = bean.headers.blankAsNull()?.let { raw ->
            buildHeader(raw).toMutableMap().takeIf { it.isNotEmpty() }
        }
        version = bean.httpVersion
        disable_version_fallback = bean.disableVersionFallback
        mtu = bean.mtu
        if (bean.enableTLS) {
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
                bean.tlsSpoof.blankAsNull()?.let {
                    spoof = it
                    spoof_method = bean.tlsSpoofMethod.blankAsNull()
                }
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
}

@Suppress("UNCHECKED_CAST")
fun parseMASQUEEndpoint(json: JSONMap): MASQUEBean = MASQUEBean().apply {
    enableTLS = false
    parseBoxOutbound(json) { key, value ->
        when (key) {
            "username" -> username = value.toString()
            "password" -> password = value.toString()
            "path" -> path = value.toString()
            "headers" -> (value as? Map<*, *>)?.let {
                headers = parseHeader(it).map { entry ->
                    entry.key + ":" + entry.value.joinToString(",")
                }.joinToString("\n")
            }

            "version" -> httpVersion = masqueHttpVersion(value)
            "disable_version_fallback" -> disableVersionFallback = value.toString().toBoolean()
            "mtu" -> value.toString().toIntOrNull()?.let { mtu = it }

            "tls" -> {
                val tlsField = value as? JSONMap ?: return@parseBoxOutbound
                val tls = parseBoxTLS(tlsField)
                if (tls.enabled != true) return@parseBoxOutbound

                enableTLS = true
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
                tlsSpoof = tls.spoof.orEmpty()
                tlsSpoofMethod = tls.spoof_method.orEmpty()
                tls.ech?.let {
                    ech = it.enabled == true
                    it.config?.joinToString("\n")?.let { config ->
                        echConfig = config
                    }
                    echQueryServerName = it.query_server_name.orEmpty()
                }
            }
        }
    }
}

private fun masqueHttpVersion(value: Any): Int {
    val parsed = value.toString().toIntOrNull() ?: 0
    return if (HttpVersion.isValid(parsed)) parsed else HttpVersion.HTTP_3
}
