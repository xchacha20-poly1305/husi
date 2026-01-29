package io.nekohasekai.sagernet.fmt.trusttunnel

import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.emptyAsNull
import io.nekohasekai.sagernet.ktx.listByLineOrComma

fun buildSingBoxOutboundTrustTunnelBean(bean: TrustTunnelBean): SingBoxOptions.Outbound_TrustTunnelOptions {
    return SingBoxOptions.Outbound_TrustTunnelOptions().apply {
        type = bean.outboundType()
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
            alpn = bean.alpn.blankAsNull()?.listByLineOrComma()
            certificate = bean.certificates.blankAsNull()?.lines()
            client_certificate = bean.clientCert.blankAsNull()?.listByLineOrComma()
            client_key = bean.clientKey.blankAsNull()?.listByLineOrComma()
            if (bean.ech) {
                ech = SingBoxOptions.OutboundECHOptions().apply {
                    enabled = true
                    config = bean.echConfig.blankAsNull()?.lines()
                    query_server_name = bean.echQueryServerName.blankAsNull()
                }
            }
        }
    }
}