package fr.husi.fmt.internal

import fr.husi.database.ProxyEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.blankAsNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * @param strict fails on a missing reference or a circular [ProxySetBean.Provider.Members] expansion;
 * otherwise those are skipped, which suits validating a profile while it is being edited.
 */
suspend fun ProxySetBean.resolveMembers(
    selfId: Long,
    strict: Boolean = false,
): List<ProxyEntity> = collectMembers(listOf(selfId), strict).distinctBy { it.id }

/**
 * @param expansionPath lists the proxy sets being expanded, ending with this one.
 * Collected groups leave this set out, and expanded sets leave out every set on the path,
 * so `A = Members(B)` works while `B` itself contains `A`.
 */
private suspend fun ProxySetBean.collectMembers(
    expansionPath: List<Long>,
    strict: Boolean,
): List<ProxyEntity> {
    val selfId = expansionPath.last()
    val members = mutableListOf<ProxyEntity>()
    for (provider in providers) {
        when (provider) {
            is ProxySetBean.Provider.Single -> {
                val entities = withContext(Dispatchers.IO) {
                    SagerDatabase.proxyDao.getEntities(listOf(provider.id))
                }
                if (strict && entities.isEmpty()) {
                    error("Missing proxy reference in proxy set $selfId: ${provider.id}")
                }
                members.addAll(entities)
            }

            is ProxySetBean.Provider.Group -> {
                members.addAll(provider.collect().filter { it.id != selfId })
            }

            is ProxySetBean.Provider.Members -> {
                members.addAll(provider.expand(expansionPath, strict))
            }
        }
    }
    return members
}

private suspend fun ProxySetBean.Provider.Group.collect(): List<ProxyEntity> {
    val filter = filterNotRegex.blankAsNull()?.toRegex()
    val entities = withContext(Dispatchers.IO) {
        SagerDatabase.proxyDao.getByGroup(groupID).first()
    }
    if (filter == null) return entities
    return entities.filter { filter.containsMatchIn(it.displayName()) }
}

private suspend fun ProxySetBean.Provider.Members.expand(
    expansionPath: List<Long>,
    strict: Boolean,
): List<ProxyEntity> {
    if (proxySetID in expansionPath) {
        if (!strict) return emptyList()
        val cycle = (expansionPath.dropWhile { it != proxySetID } + proxySetID)
            .joinToString(" -> ")
        error("Circular proxy set expansion: $cycle")
    }
    val target = withContext(Dispatchers.IO) {
        SagerDatabase.proxyDao.getById(proxySetID)
    }?.proxySetBean
    if (target == null) {
        if (strict) {
            error("Missing proxy set reference in proxy set ${expansionPath.last()}: $proxySetID")
        }
        return emptyList()
    }
    return target.collectMembers(expansionPath + proxySetID, strict)
        .filter { it.id !in expansionPath }
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
            interval = bean.interval
            tolerance = bean.testTolerance
            idle_timeout = bean.testIdleTimeout
            interrupt_exist_connections = bean.interruptExistConnections
        }

        ProxySetBean.MANAGEMENT_BALANCER -> SingBoxOptions.Outbound_BalancerOptions().apply {
            type = SingBoxOptions.TYPE_BALANCER
            this.outbounds = outbounds.toMutableList()
            interval = bean.interval.blankAsNull()
        }

        else -> throw IllegalStateException()
    }
}
