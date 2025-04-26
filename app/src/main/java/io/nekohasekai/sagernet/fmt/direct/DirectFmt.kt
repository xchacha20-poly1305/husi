package io.nekohasekai.sagernet.fmt.direct

import io.nekohasekai.sagernet.fmt.SingBoxOptions

fun buildSingBoxOutboundDirectBean(bean: DirectBean): SingBoxOptions.Outbound_DirectOptions {
    return SingBoxOptions.Outbound_DirectOptions().apply {
        type = bean.outboundType()

        // This just a workaround to make direct not an "empty" outbound.
        reuse_addr = true
    }
}