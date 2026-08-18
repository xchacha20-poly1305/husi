package fr.husi.fmt.internal

import fr.husi.database.ProxyEntity
import fr.husi.fmt.SingBoxOptions

suspend fun ProxySetBean.resolveMembers(
    selfId: Long,
    failOnMissing: Boolean = false,
): List<ProxyEntity> {
    val members = mutableListOf<ProxyEntity>()
    for (provider in providers) {
        val entities = provider.entities()
        when (provider) {
            is ProxySetBean.Provider.Single -> {
                if (failOnMissing && entities.isEmpty()) {
                    error("Missing proxy reference in proxy set $selfId: ${provider.id}")
                }
                members.addAll(entities)
            }

            is ProxySetBean.Provider.Group -> {
                members.addAll(entities.filter { it.id != selfId })
            }
        }
    }
    return members.distinctBy { it.id }
}

fun buildSingBoxOutboundProxySetBean(
    bean: ProxySetBean,
    outbounds: List<String>,
): SingBoxOptions.Outbound {
    return when (bean.management) {
        ProxySetBean.MANAGEMENT_SELECTOR -> SingBoxOptions.Outbound_SelectorOptions().apply {
            type = SingBoxOptions.TYPE_SELECTOR
            this.outbounds = outbounds.toMutableList()
            interrupt_exist_connections = bean.interruptExistConnections
        }

        ProxySetBean.MANAGEMENT_URLTEST -> SingBoxOptions.Outbound_URLTestOptions().apply {
            type = SingBoxOptions.TYPE_URLTEST
            this.outbounds = outbounds.toMutableList()
            url = bean.testURL
            interval = bean.testInterval
            tolerance = bean.testTolerance
            idle_timeout = bean.testIdleTimeout
            interrupt_exist_connections = bean.interruptExistConnections
        }

        else -> throw IllegalStateException()
    }
}