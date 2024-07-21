package moe.matsuri.nb4a.proxy.shadowtls

import io.nekohasekai.sagernet.fmt.v2ray.buildSingBoxOutboundTLS
import moe.matsuri.nb4a.SingBoxOptions

fun buildSingBoxOutboundShadowTLSBean(bean: ShadowTLSBean): SingBoxOptions.Outbound_ShadowTLSOutboundOptions {
    return SingBoxOptions.Outbound_ShadowTLSOutboundOptions().apply {
        type = "shadowtls"
        server = bean.serverAddress
        server_port = bean.serverPort
        version = bean.version
        password = bean.password
        tls = buildSingBoxOutboundTLS(bean)
    }
}
