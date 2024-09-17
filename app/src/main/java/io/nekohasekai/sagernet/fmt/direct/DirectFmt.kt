package io.nekohasekai.sagernet.fmt.direct

import moe.matsuri.nb4a.SingBoxOptions

fun buildSingBoxOutboundDirectBean(bean: DirectBean): SingBoxOptions.Outbound_DirectOptions {
    return SingBoxOptions.Outbound_DirectOptions().apply {
        type = bean.outboundType()
        if (!bean.serverAddress.isNullOrBlank()) override_address = bean.serverAddress
        if (bean.serverPort > 0) override_port = bean.serverPort
    }
}