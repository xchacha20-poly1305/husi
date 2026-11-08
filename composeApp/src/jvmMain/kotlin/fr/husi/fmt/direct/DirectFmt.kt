package fr.husi.fmt.direct

import fr.husi.fmt.SingBoxOptions

fun buildSingBoxOutboundDirectBean(bean: DirectBean): SingBoxOptions.Outbound_DirectOptions {
    return SingBoxOptions.Outbound_DirectOptions().apply {
        type = SingBoxOptions.TYPE_DIRECT

        // This just a workaround to make direct not an "empty" outbound.
        reuse_addr = true
    }
}