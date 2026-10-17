package fr.husi.ui.profile

import fr.husi.database.ProxyEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.internal.ChainBean
import fr.husi.fmt.internal.ProxySetBean
import fr.husi.fmt.internal.resolveMembers

private suspend fun ProxyEntity.directProfileReferences(): List<ProxyEntity> =
    when (val bean = requireBean()) {
        is ChainBean -> SagerDatabase.proxyDao.getEntities(bean.proxies)
        is ProxySetBean -> bean.resolveMembers(id)

        else -> emptyList()
    }

private suspend fun collectProfileReferenceIds(
    roots: Iterable<ProxyEntity>,
): Set<Long> {
    val references = LinkedHashSet<Long>()

    suspend fun visit(entity: ProxyEntity) {
        if (!references.add(entity.id)) return
        for (referencedProfile in entity.directProfileReferences()) {
            visit(referencedProfile)
        }
    }

    for (root in roots) {
        visit(root)
    }
    return references
}

internal suspend fun ProxyEntity.profileReferenceIds(): Set<Long> {
    return collectProfileReferenceIds(listOf(this))
}

internal suspend fun ProxyEntity.containsProfileReference(targetId: Long): Boolean {
    return targetId in profileReferenceIds()
}

internal suspend fun proxySetsExpanding(proxySetId: Long): Set<Long> {
    val expandersByTarget = HashMap<Long, MutableList<Long>>()
    for (entity in SagerDatabase.proxyDao.getByType(ProxyEntity.TYPE_PROXY_SET)) {
        val bean = entity.proxySetBean ?: continue
        for (provider in bean.providers) {
            if (provider is ProxySetBean.Provider.Members) {
                expandersByTarget.getOrPut(provider.proxySetID) { mutableListOf() }.add(entity.id)
            }
        }
    }

    val expanders = LinkedHashSet<Long>()
    val pending = ArrayDeque(listOf(proxySetId))
    while (pending.isNotEmpty()) {
        for (expander in expandersByTarget[pending.removeFirst()].orEmpty()) {
            if (expander != proxySetId && expanders.add(expander)) {
                pending.add(expander)
            }
        }
    }
    return expanders
}
