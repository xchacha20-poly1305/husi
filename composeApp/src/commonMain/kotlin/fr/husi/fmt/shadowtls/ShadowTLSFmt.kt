package fr.husi.fmt.shadowtls

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.v2ray.buildSingBoxOutboundTLS

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
