package fr.husi.group

import fr.husi.Key
import fr.husi.SubscriptionType
import fr.husi.bg.resolveByDefaultNetwork
import fr.husi.database.DataStore
import fr.husi.database.ProxyEntity
import fr.husi.database.ProxyGroup
import fr.husi.database.SagerDatabase
import fr.husi.database.SubscriptionBean
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.Deduplication
import fr.husi.fmt.http.HttpBean
import fr.husi.fmt.hysteria.HysteriaBean
import fr.husi.fmt.v2ray.StandardV2RayBean
import fr.husi.ktx.Logs
import fr.husi.ktx.isIpAddress
import fr.husi.ktx.onIoDispatcher
import fr.husi.ktx.readableMessage
import fr.husi.ktx.selectByNetworkStrategy
import fr.husi.ktx.serverAddressDomainStrategy
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.force_resolve_error
import fr.husi.resources.update_subscription_warning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetAddress

data class GroupUpdateWarning(
    val group: String,
    val message: String,
)

data class GroupUpdateDiff(
    val changed: Int,
    val added: List<String>,
    val updated: Map<String, String>,
    val deleted: List<String>,
    val duplicate: List<String>,
)

sealed interface GroupUpdateResult {
    val group: ProxyGroup

    data class AlreadyUpdating(
        override val group: ProxyGroup,
    ) : GroupUpdateResult

    data class ConfirmRequired(
        override val group: ProxyGroup,
        val message: String,
    ) : GroupUpdateResult

    data class Success(
        override val group: ProxyGroup,
        val diff: GroupUpdateDiff,
        val warnings: List<GroupUpdateWarning>,
        val byUser: Boolean,
    ) : GroupUpdateResult

    data class Failure(
        override val group: ProxyGroup,
        val message: String,
        val warnings: List<GroupUpdateWarning>,
        val byUser: Boolean,
    ) : GroupUpdateResult
}

abstract class GroupUpdater {

    abstract suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        byUser: Boolean,
        warnings: MutableList<GroupUpdateWarning>,
    ): GroupUpdateResult.Success

    private suspend fun forceResolve(profiles: List<AbstractBean>) = coroutineScope {
        val networkStrategy = serverAddressDomainStrategy().orEmpty()
        val shouldResolveByDefaultNetwork = DataStore.enableFakeDns
                && DataStore.serviceState.started
                && DataStore.serviceMode == Key.MODE_VPN
        val lookupDispatcher = Dispatchers.IO.limitedParallelism(5)

        for (profile in profiles) {
            if (profile.serverAddress.isIpAddress()) continue
            launch(lookupDispatcher) {
                try {
                    val results = if (shouldResolveByDefaultNetwork) {
                        // FakeDNS
                        resolveByDefaultNetwork(profile.serverAddress)
                    } else {
                        // System DNS is enough (when VPN connected, it uses sing-box)
                        InetAddress.getAllByName(profile.serverAddress).filterNotNull()
                    }
                    if (results.isEmpty()) error("empty response")
                    this@GroupUpdater.rewriteAddress(profile, results, networkStrategy)
                } catch (e: Exception) {
                    Logs.d("Lookup ${profile.serverAddress}", e)
                }
            }
        }
    }

    private fun rewriteAddress(
        bean: AbstractBean, addresses: List<InetAddress>, networkStrategy: String,
    ) {
        val address = addresses.selectByNetworkStrategy(networkStrategy)?.hostAddress
            ?: error("empty response")

        with(bean) {
            when (this) {
                is HttpBean -> {
                    if (isTLS && sni.isBlank()) sni = bean.serverAddress
                }

                is StandardV2RayBean -> {
                    when (security) {
                        "tls" -> if (sni.isBlank()) sni = bean.serverAddress
                    }
                }

                is HysteriaBean -> {
                    if (sni.isBlank()) sni = bean.serverAddress
                }
            }

            bean.serverAddress = address
        }
    }

    /** Force resolve & remove deduplication */
    open suspend fun tidyProxies(
        proxies: List<AbstractBean>,
        subscription: SubscriptionBean,
        proxyGroup: ProxyGroup,
        byUser: Boolean,
        warnings: MutableList<GroupUpdateWarning>,
    ): GroupUpdateResult.Success {
        var newProxies = if (subscription.filterNotRegex.isNotBlank()) {
            val regex = subscription.filterNotRegex.toRegex()
            proxies.filterNot { regex.containsMatchIn(it.name) }
        } else {
            proxies
        }

        val proxiesMap = LinkedHashMap<String, AbstractBean>()
        for (proxy in newProxies) {
            var index = 0
            var name = proxy.displayName()
            while (proxiesMap.containsKey(name)) {
                index++
                name = name.replace(" (${index - 1})", "")
                name = "$name ($index)"
                proxy.name = name
            }
            proxiesMap[proxy.displayName()] = proxy
        }
        newProxies = proxiesMap.values.toList()

        if (subscription.forceResolve) {
            try {
                forceResolve(newProxies)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w(e)
                warnings += GroupUpdateWarning(
                    proxyGroup.displayName(),
                    resolveRepository().getString(Res.string.force_resolve_error, e.readableMessage),
                )
            }
        }

        val exists = onIoDispatcher {
            SagerDatabase.proxyDao.getByGroup(proxyGroup.id).first()
        }
        val duplicate = ArrayList<String>()
        if (subscription.deduplication) {
            Logs.d("Before deduplication: ${newProxies.size}")
            val uniqueProxies = LinkedHashSet<Deduplication>()
            val uniqueNames = HashMap<Deduplication, String>()
            for (_proxy in newProxies) {
                val proxy = Deduplication(_proxy, _proxy.javaClass.toString())
                if (!uniqueProxies.add(proxy)) {
                    val index = uniqueProxies.indexOf(proxy)
                    if (uniqueNames.containsKey(proxy)) {
                        val name = uniqueNames[proxy]!!.replace(" ($index)", "")
                        if (name.isNotBlank()) {
                            duplicate.add("$name ($index)")
                            uniqueNames[proxy] = ""
                        }
                    }
                    duplicate.add(_proxy.displayName() + " ($index)")
                } else {
                    uniqueNames[proxy] = _proxy.displayName()
                }
            }
            uniqueProxies.retainAll(uniqueNames.keys)
            newProxies = uniqueProxies.toList().map { it.bean }
        }

        Logs.d("New profiles: ${newProxies.size}")

        Logs.d("Unique profiles: ${newProxies.size}")

        val existingByName = exists.associateBy { it.displayName() }.toMutableMap()

        val toUpdate = ArrayList<ProxyEntity>()
        val toInsert = ArrayList<ProxyEntity>()
        val updated = mutableMapOf<String, String>()

        var userOrder = 1L
        var changed = 0
        for (bean in newProxies) {
            val name = bean.displayName()
            val entity = existingByName.remove(name)
            if (entity != null) {
                val existsBean = entity.requireBean()
                existsBean.applyFeatureSettings(bean)
                when {
                    existsBean != bean -> {
                        changed++
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        updated[entity.displayName()] = name

                        Logs.d("Updated profile: $name")
                    }

                    entity.userOrder != userOrder -> {
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        entity.userOrder = userOrder

                        Logs.d("Reordered profile: $name")
                    }

                    else -> {
                        Logs.d("Ignored profile: $name")
                    }
                }
            } else {
                changed++
                toInsert.add(
                    ProxyEntity(
                        groupId = proxyGroup.id, userOrder = userOrder,
                    ).apply {
                        putBean(bean)
                    },
                )
                Logs.d("Inserted profile: $name")
            }
            userOrder++
        }

        val toDelete = existingByName.values.toList()
        changed += toDelete.size
        val deleted = toDelete.map { it.displayName() }
        val added = toInsert.map { it.displayName() }

        Logs.d("toDelete profiles: ${toDelete.size}")
        Logs.d("toReplace profiles: ${exists.size - toDelete.size}")

        SagerDatabase.proxyDao.syncProxies(toInsert, toUpdate, toDelete)
        if (toInsert.isNotEmpty()) {
            Logs.d("Inserted profiles: ${toInsert.size}")
        }
        if (toUpdate.isNotEmpty()) {
            Logs.d("Updated profiles: ${toUpdate.size}")
        }
        if (toDelete.isNotEmpty()) {
            Logs.d("Deleted profiles: ${toDelete.size}")
        }

        val existCount = SagerDatabase.proxyDao.countByGroup(proxyGroup.id).first().toInt()

        if (existCount != newProxies.size) {
            Logs.e("Exist profiles: $existCount, new profiles: ${newProxies.size}")
        }

        subscription.lastUpdated = (System.currentTimeMillis() / 1000).toInt()
        SagerDatabase.groupDao.updateGroup(proxyGroup)

        return GroupUpdateResult.Success(
            group = proxyGroup,
            diff = GroupUpdateDiff(
                changed = changed,
                added = added,
                updated = updated,
                deleted = deleted,
                duplicate = duplicate,
            ),
            warnings = warnings.toList(),
            byUser = byUser,
        )
    }

    companion object {

        private val _updatingGroups = MutableStateFlow<Set<Long>>(emptySet())
        val updatingGroups = _updatingGroups.asStateFlow()

        suspend fun executeUpdate(
            proxyGroup: ProxyGroup,
            byUser: Boolean,
            allowDisconnectedUpdate: Boolean = false,
        ): GroupUpdateResult {
            var added = false
            _updatingGroups.update { current ->
                if (proxyGroup.id !in current) {
                    added = true
                    current + proxyGroup.id
                } else {
                    current
                }
            }
            if (!added) return GroupUpdateResult.AlreadyUpdating(proxyGroup)

            try {
                val subscription = proxyGroup.subscription!!
                val connected = DataStore.serviceState.connected
                if (
                    byUser &&
                    subscription.link.startsWith("http://") &&
                    subscription.updateWhenConnectedOnly &&
                    !connected &&
                    !allowDisconnectedUpdate
                ) {
                    return GroupUpdateResult.ConfirmRequired(
                        group = proxyGroup,
                        message = resolveRepository().getString(Res.string.update_subscription_warning),
                    )
                }

                val warnings = mutableListOf<GroupUpdateWarning>()
                return try {
                    when (subscription.type) {
                        SubscriptionType.RAW -> RawUpdater
                        SubscriptionType.OOCv1 -> OpenOnlineConfigUpdater
                        SubscriptionType.SIP008 -> SIP008Updater
                        else -> throw IllegalArgumentException()
                    }.doUpdate(proxyGroup, subscription, byUser, warnings)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Logs.w(e)
                    GroupUpdateResult.Failure(
                        group = proxyGroup,
                        message = e.readableMessage,
                        warnings = warnings.toList(),
                        byUser = byUser,
                    )
                }
            } finally {
                finishUpdate(proxyGroup)
            }
        }

        suspend fun finishUpdate(proxyGroup: ProxyGroup) {
            _updatingGroups.update { it - proxyGroup.id }
        }

    }

}
