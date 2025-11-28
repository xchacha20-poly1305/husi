package io.nekohasekai.sagernet.database

import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.bg.SubscriptionUpdater
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object GroupManager {

    interface Interface {
        suspend fun confirm(message: String): Boolean
        suspend fun alert(message: String)
        suspend fun onUpdateSuccess(
            group: ProxyGroup,
            changed: Int,
            added: List<String>,
            updated: Map<String, String>,
            deleted: List<String>,
            duplicate: List<String>,
            byUser: Boolean,
        )

        suspend fun onUpdateFailure(group: ProxyGroup, message: String)
    }

    var userInterface: Interface? = null

    suspend fun clearGroup(groupId: Long) {
        DataStore.selectedProxy = 0L
        SagerDatabase.proxyDao.deleteAll(groupId)
    }

    suspend fun rearrange(groupId: Long) {
        val entities = onIoDispatcher {
            SagerDatabase.proxyDao.getByGroup(groupId).first()
        }
        for (index in entities.indices) {
            entities[index].userOrder = (index + 1).toLong()
        }
        onIoDispatcher {
            SagerDatabase.proxyDao.updateProxy(entities)
        }
    }

    suspend fun createGroup(group: ProxyGroup): ProxyGroup {
        group.userOrder = SagerDatabase.groupDao.nextOrder() ?: 1
        group.id = SagerDatabase.groupDao.createGroup(group.applyDefaultValues())
        if (group.type == GroupType.SUBSCRIPTION) {
            SubscriptionUpdater.reconfigureUpdater()
        }
        return group
    }

    suspend fun updateGroup(group: ProxyGroup) {
        SagerDatabase.groupDao.updateGroup(group)
        if (group.type == GroupType.SUBSCRIPTION) {
            SubscriptionUpdater.reconfigureUpdater()
        }
    }

    suspend fun deleteGroup(groupId: Long) {
        SagerDatabase.groupDao.deleteById(groupId)
        SagerDatabase.proxyDao.deleteByGroup(groupId)
        SubscriptionUpdater.reconfigureUpdater()
    }

    suspend fun deleteGroup(group: List<ProxyGroup>) {
        SagerDatabase.groupDao.deleteGroup(group)
        SagerDatabase.proxyDao.deleteByGroup(LongArray(group.size) { group[it].id })
        SubscriptionUpdater.reconfigureUpdater()
    }

}