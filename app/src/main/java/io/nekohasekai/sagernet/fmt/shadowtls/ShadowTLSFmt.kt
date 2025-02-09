package io.nekohasekai.sagernet.fmt.shadowtls

import io.nekohasekai.sagernet.fmt.v2ray.buildSingBoxOutboundTLS
import moe.matsuri.nb4a.SingBoxOptions

fun buildSingBoxOutboundShadowTLSBean(bean: ShadowTLSBean): SingBoxOptions.Outbound_ShadowTLSOptions {
    return SingBoxOptions.Outbound_ShadowTLSOptions().apply {
        type = SingBoxOptions.TYPE_SHADOWTLS
        server = bean.serverAddress
        server_port = bean.serverPort
        version = bean.protocolVersion
        password = bean.password
        tls = buildSingBoxOutboundTLS(bean)
    }
}
