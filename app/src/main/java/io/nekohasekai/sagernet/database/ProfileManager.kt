package io.nekohasekai.sagernet.database

import android.database.sqlite.SQLiteCantOpenDatabaseException
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.RuleItem
import io.nekohasekai.sagernet.fmt.SingBoxOptions.ACTION_ROUTE
import io.nekohasekai.sagernet.fmt.SingBoxOptions.ACTION_HIJACK_DNS
import io.nekohasekai.sagernet.fmt.SingBoxOptions.ACTION_REJECT
import io.nekohasekai.sagernet.fmt.SingBoxOptions.ACTION_SNIFF
import io.nekohasekai.sagernet.fmt.SingBoxOptions.NetworkICMP
import io.nekohasekai.sagernet.fmt.SingBoxOptions.NetworkUDP
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.repository.repo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.IOException
import java.sql.SQLException
import java.util.Locale

object ProfileManager {

    private val defaultGroupLock = Any()

    suspend fun createProfile(groupId: Long, bean: AbstractBean): ProxyEntity {
        bean.applyDefaultValues()

        val profile = ProxyEntity(groupId = groupId).apply {
            id = 0
            putBean(bean)
            userOrder = SagerDatabase.proxyDao.nextOrder(groupId) ?: 1
        }
        profile.id = SagerDatabase.proxyDao.addProxy(profile)
        return profile
    }

    suspend fun updateProfile(profile: ProxyEntity) {
        SagerDatabase.proxyDao.updateProxy(profile)
    }

    suspend fun updateProfile(profiles: List<ProxyEntity>) {
        SagerDatabase.proxyDao.updateProxy(profiles)
    }

    suspend fun updateTraffic(profile: ProxyEntity, tx: Long?, rx: Long?) {
        SagerDatabase.proxyDao.updateTraffic(profile.id, tx, rx)
    }

    suspend fun deleteProfile(groupId: Long, profileId: Long) {
        if (SagerDatabase.proxyDao.deleteById(profileId) == 0) return
        if (DataStore.selectedProxy == profileId) {
            DataStore.selectedProxy = 0L
        }
        if (SagerDatabase.proxyDao.countByGroup(groupId).first() > 1) {
            GroupManager.rearrange(groupId)
        }
    }

    suspend fun deleteProfiles(groupId: Long, profileIDs: List<Long>) {
        if (profileIDs.isEmpty()) return
        SagerDatabase.proxyDao.deleteProxies(profileIDs)
        if (profileIDs.contains(DataStore.selectedProxy)) {
            DataStore.selectedProxy = 0L
        }
        if (SagerDatabase.proxyDao.countByGroup(groupId).first() > 1) {
            GroupManager.rearrange(groupId)
        }
    }

    fun getProfile(profileId: Long): ProxyEntity? {
        if (profileId == 0L) return null
        return try {
            SagerDatabase.proxyDao.getById(profileId)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            throw IOException(ex)
        } catch (ex: SQLException) {
            Logs.w(ex)
            null
        }
    }

    fun getProfiles(profileIds: List<Long>): List<ProxyEntity> {
        if (profileIds.isEmpty()) return listOf()
        return try {
            SagerDatabase.proxyDao.getEntities(profileIds)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            throw IOException(ex)
        } catch (ex: SQLException) {
            Logs.w(ex)
            listOf()
        }
    }

    suspend fun createRule(rule: RuleEntity, post: Boolean = true): RuleEntity {
        rule.userOrder = SagerDatabase.rulesDao.nextOrder() ?: 1
        rule.id = SagerDatabase.rulesDao.createRule(rule)
        return rule
    }

    suspend fun updateRule(rule: RuleEntity) {
        SagerDatabase.rulesDao.updateRule(rule)
    }

    suspend fun deleteRule(ruleId: Long) {
        SagerDatabase.rulesDao.deleteById(ruleId)
    }

    suspend fun deleteRules(rules: List<RuleEntity>) {
        SagerDatabase.rulesDao.deleteRules(rules)
    }

    suspend fun deleteRulesByIds(ruleIds: List<Long>) {
        SagerDatabase.rulesDao.deleteByIds(ruleIds)
    }

    /**
     * Get all rules as a Flow with automatic initialization.
     *
     * This is a wrapper around [SagerDatabase.rulesDao.allRules] that ensures default rules
     * are created on first app launch. When the Flow is first collected, it checks if the
     * rule list is empty and creates the following rules.
     *
     * Always use this method instead of calling the DAO directly to ensure proper initialization.
     */
    fun getRules(): Flow<List<RuleEntity>> {
        return SagerDatabase.rulesDao.allRules().onStart {
            val currentRules = SagerDatabase.rulesDao.allRules().first()
            if (currentRules.isEmpty() && !DataStore.rulesFirstCreate) {
                DataStore.rulesFirstCreate = true
                createRule(
                    RuleEntity(
                        enabled = true,
                        name = repo.getString(R.string.sniff),
                        action = ACTION_SNIFF,
                    ),
                )
                createRule(
                    RuleEntity(
                        enabled = true,
                        name = repo.getString(R.string.hijack_dns),
                        protocol = setOf("dns"),
                        action = ACTION_HIJACK_DNS,
                    ),
                )
                createRule(
                    RuleEntity(
                        enabled = true,
                        action = ACTION_ROUTE,
                        name = repo.getString(R.string.bypass_icmp),
                        network = setOf(NetworkICMP),
                        outbound = RuleEntity.OUTBOUND_DIRECT,
                    ),
                )
                createRule(
                    RuleEntity(
                        name = repo.getString(R.string.route_opt_block_quic),
                        action = ACTION_REJECT,
                        protocol = setOf("quic"),
                        network = setOf(NetworkUDP),
                    ),
                )
                createRule(
                    RuleEntity(
                        name = repo.getString(R.string.route_opt_block_ads),
                        action = ACTION_REJECT,
                        domains = "set+dns:geosite-category-ads-all",
                    ),
                )
                val walledCountry = mutableListOf("cn:中国")
                if (Locale.getDefault().country == Locale.US.country) {
                    // English users
                    walledCountry += "ir:Iran"
                }
                for (c in walledCountry) {
                    val country = c.substringBefore(":")
                    val displayCountry = c.substringAfter(":")
                    if (country == "cn") createRule(
                        RuleEntity(
                            name = repo.getString(R.string.route_play_store, displayCountry),
                            action = ACTION_ROUTE,
                            domains = "set+dns:geosite-google-play",
                            outbound = RuleEntity.OUTBOUND_PROXY,
                        ),
                        false,
                    )
                    createRule(
                        RuleEntity(
                            name = repo.getString(R.string.route_bypass_domain, displayCountry),
                            action = ACTION_ROUTE,
                            domains = "set+dns:geosite-$country",
                            outbound = RuleEntity.OUTBOUND_DIRECT,
                        ),
                        false,
                    )
                    createRule(
                        RuleEntity(
                            name = repo.getString(R.string.route_bypass_ip, displayCountry),
                            action = ACTION_ROUTE,
                            ip = "set-dns:geoip-$country",
                            outbound = RuleEntity.OUTBOUND_DIRECT,
                        ),
                        false,
                    )
                }
                createRule(
                    RuleEntity(
                        name = repo.getString(R.string.route_opt_bypass_lan),
                        action = ACTION_ROUTE,
                        ip = RuleItem.CONTENT_PRIVATE,
                        outbound = RuleEntity.OUTBOUND_DIRECT,
                    ),
                    false,
                )
            }
        }
    }

    fun enabledRules(): Flow<List<RuleEntity>> {
        return getRules().map {
            it.filter { it.enabled }
        }
    }

    /**
     * Get all groups as a Flow with automatic initialization.
     *
     * This is a wrapper around [SagerDatabase.groupDao.allGroups] that ensures at least one
     * group exists. When the Flow is first collected, it checks if the group list is empty
     * and creates a default ungrouped group if needed.
     *
     * Always use this method instead of calling the DAO directly to ensure proper initialization.
     */
    fun getGroups(): Flow<List<ProxyGroup>> {
        return SagerDatabase.groupDao.allGroups().onStart {
            ensureDefaultGroupId()
        }
    }

    fun ensureDefaultGroupId(): Long = synchronized(defaultGroupLock) {
        SagerDatabase.groupDao.firstGroupId()
            ?: SagerDatabase.groupDao.ungroupedId()
            ?: SagerDatabase.groupDao.createGroup(ProxyGroup(ungrouped = true))
    }

}
