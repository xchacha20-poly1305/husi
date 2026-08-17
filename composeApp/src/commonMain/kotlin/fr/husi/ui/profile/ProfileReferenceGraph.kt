package fr.husi.ui.profile

import fr.husi.database.ProxyEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.internal.ChainBean
import fr.husi.fmt.internal.ProxySetBean
import fr.husi.ktx.blankAsNull
import kotlinx.coroutines.flow.first

private suspend fun ProxyEntity.directProfileReferences(): List<ProxyEntity> =
    when (val bean = requireBean()) {
        is ChainBean -> SagerDatabase.proxyDao.getEntities(bean.proxies)
        is ProxySetBean -> when (bean.type) {
            ProxySetBean.TYPE_LIST -> SagerDatabase.proxyDao.getEntities(bean.proxies)
            ProxySetBean.TYPE_GROUP -> {
                val filterRegex = bean.groupFilterNotRegex.blankAsNull()?.toRegex()
                SagerDatabase.proxyDao.getByGroup(bean.groupId).first().filter {
                    it.id != id && filterRegex?.containsMatchIn(it.displayName()) != false
                }
            }

            else -> emptyList()
        }

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

private suspend fun groupProxyReferenceIds(groupId: Long): Set<Long> {
    val group = SagerDatabase.groupDao.getById(groupId).first() ?: return emptySet()
    val wrapperIds = listOf(group.landingProxy, group.frontProxy)
        .filter { it > 0L }
        .distinct()
    return collectProfileReferenceIds(SagerDatabase.proxyDao.getEntities(wrapperIds))
}

internal suspend fun ProxyEntity.containsProfileReference(
    targetId: Long,
    includeGroupProxies: Boolean = true,
): Boolean {
    return targetId in collectProfileReferenceIds(listOf(this))
            || includeGroupProxies
            && targetId in groupProxyReferenceIds(groupId)
}

internal suspend fun groupProxiesOverlapProfileReferences(
    groupId: Long,
    rootProfileId: Long,
    memberProfiles: Iterable<ProxyEntity>,
): Boolean {
    val wrapperReferences = groupProxyReferenceIds(groupId)
    if (wrapperReferences.isEmpty()) return false

    val mainReferences = collectProfileReferenceIds(memberProfiles).toMutableSet()
    if (rootProfileId > 0L) {
        mainReferences.add(rootProfileId)
    }
    return mainReferences.any(wrapperReferences::contains)
}
