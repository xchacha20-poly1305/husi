package fr.husi.fmt.trusttunnel

import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.emptyAsNull
import fr.husi.ktx.listByLineOrComma
import fr.husi.libcore.Libcore
import fr.husi.libcore.TrustTunnelURL

fun parseTrustTunnel(link: String): TrustTunnelBean {
    val url = Libcore.parseTrustTunnelLink(link)
    return TrustTunnelBean().apply {
        serverAddress = url.host
        serverPort = url.port
        serverName = url.serverName
        username = url.username
        password = url.password
        allowInsecure = url.skipVerification
        certificates = url.certificate
        quic = url.quic
        name = url.name
    }
}

fun TrustTunnelBean.toUri(): String {
    return TrustTunnelURL().apply {
        host = serverAddress
        port = serverPort
        serverName = serverName
        username = username
        password = password
        skipVerification = allowInsecure
        certificate = certificates
        quic = quic
        name = this@toUri.name
    }.build()
}

fun buildSingBoxOutboundTrustTunnelBean(bean: TrustTunnelBean): SingBoxOptions.Outbound_TrustTunnelOptions {
    return SingBoxOptions.Outbound_TrustTunnelOptions().apply {
        type = SingBoxOptions.TYPE_TRUST_TUNNEL
        server = bean.serverAddress
        server_port = bean.serverPort
        username = bean.username
        password = bean.password
        if (bean.healthCheck) health_check = true
        if (bean.quic) {
            quic = true
            quic_congestion_control = bean.quicCongestionControl.emptyAsNull()
        }

        tls = SingBoxOptions.OutboundTLSOptions().apply {
            enabled = true
            server_name = bean.serverName.blankAsNull()
            if (bean.allowInsecure) insecure = true
            alpn = bean.alpn.blankAsNull()?.listByLineOrComma()?.toMutableList()
            certificate = bean.certificates.blankAsNull()?.lines()?.toMutableList()
            certificate_public_key_sha256 = bean.certPublicKeySha256
                .blankAsNull()
                ?.lines()
                ?.toMutableList()
            client_certificate = bean.clientCert.blankAsNull()?.listByLineOrComma()?.toMutableList()
            client_key = bean.clientKey.blankAsNull()?.listByLineOrComma()?.toMutableList()
            if (bean.tlsFragment) {
                fragment = true
                fragment_fallback_delay = bean.tlsFragmentFallbackDelay.blankAsNull()
            } else if (bean.tlsRecordFragment) {
                record_fragment = true
            }
            bean.utlsFingerprint.blankAsNull()?.let {
                utls = SingBoxOptions.OutboundUTLSOptions().apply {
                    enabled = true
                    fingerprint = it
                }
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