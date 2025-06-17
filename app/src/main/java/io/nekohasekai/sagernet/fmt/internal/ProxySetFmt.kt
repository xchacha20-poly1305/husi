package io.nekohasekai.sagernet.fmt.internal

import io.nekohasekai.sagernet.fmt.SingBoxOptions

fun buildSingBoxOutboundProxySetBean(
    bean: ProxySetBean,
    outbounds: List<String>,
): SingBoxOptions.Outbound {
    return when (bean.management) {
        ProxySetBean.MANAGEMENT_SELECTOR -> SingBoxOptions.Outbound_SelectorOptions().apply {
            type = SingBoxOptions.TYPE_SELECTOR
            this.outbounds = outbounds
            interrupt_exist_connections = bean.interruptExistConnections
        }

        ProxySetBean.MANAGEMENT_URLTEST -> SingBoxOptions.Outbound_URLTestOptions().apply {
            type = SingBoxOptions.TYPE_URLTEST
            this.outbounds = outbounds
            url = bean.testURL
            interval = bean.testInterval
            tolerance = bean.testTolerance
            idle_timeout = bean.testIdleTimeout
            interrupt_exist_connections = bean.interruptExistConnections
        }

        else -> throw IllegalStateException()
    }
}