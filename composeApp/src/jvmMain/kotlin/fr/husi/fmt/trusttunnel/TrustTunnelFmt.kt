package fr.husi.fmt.trusttunnel

import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.emptyAsNull
import fr.husi.ktx.joinAddress
import fr.husi.ktx.splitAddress
import fr.husi.ktx.toPortOrNull
import fr.husi.ktx.listByLineOrComma
import okio.ByteString

fun parseTrustTunnel(link: String): TrustTunnelBean {
    val url = TrustTunnelLink.parse(link)
    val (host, port) = checkNotNull(splitAddress(url.addresses.first()))
    return TrustTunnelBean().apply {
        serverAddress = host
        serverPort = checkNotNull(port.toPortOrNull())
        serverName = url.customSni
        username = url.username
        password = url.password
        allowInsecure = url.skipVerification
        certificates = if (url.certificate.size > 0) certificateChainToPem(url.certificate) else ""
        quic = url.http3
        name = url.name
    }
}

fun TrustTunnelBean.toUri(): String {
    return TrustTunnelLink(
        hostname = serverName.ifEmpty { serverAddress },
        addresses = listOf(joinAddress(serverAddress, serverPort)),
        customSni = serverName,
        username = username,
        password = password,
        skipVerification = allowInsecure,
        certificate = certificates.blankAsNull()?.let(::pemToCertificateChain) ?: ByteString.EMPTY,
        http3 = quic,
        name = name,
    ).build()
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
            certificate_sha256 = bean.certificateSha256
                .blankAsNull()
                ?.lines()
                ?.toMutableList()
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
            if (!bean.quic) {
                bean.tlsSpoof.blankAsNull()?.let {
                    spoof = it
                    spoof_method = bean.tlsSpoofMethod.blankAsNull()
                }
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