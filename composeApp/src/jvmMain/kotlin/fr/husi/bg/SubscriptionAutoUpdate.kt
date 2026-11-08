package fr.husi.bg

import fr.husi.database.DataStore
import fr.husi.database.ProxyGroup
import fr.husi.database.SagerDatabase
import fr.husi.group.GroupUpdater
import fr.husi.ktx.Logs

data class SubscriptionAutoUpdatePlan(
    val repeatIntervalMinutes: Int,
    val initialDelaySeconds: Long,
)

private data class AutoUpdateCandidate(
    val group: ProxyGroup,
    val repeatIntervalMinutes: Int,
    val secondsUntilDue: Long,
    val updateWhenConnectedOnly: Boolean,
)

object SubscriptionAutoUpdatePlanner {

    suspend fun plan(): SubscriptionAutoUpdatePlan? {
        return plan(
            subscriptions = loadAutoUpdateSubscriptions(),
            nowSeconds = currentEpochSeconds(),
        )
    }

    fun plan(
        subscriptions: List<ProxyGroup>,
        nowSeconds: Long,
    ): SubscriptionAutoUpdatePlan? {
        val candidates = autoUpdateCandidates(subscriptions, nowSeconds)
        if (candidates.isEmpty()) return null

        return SubscriptionAutoUpdatePlan(
            repeatIntervalMinutes = candidates.minOf(AutoUpdateCandidate::repeatIntervalMinutes),
            initialDelaySeconds = candidates.minOf(AutoUpdateCandidate::secondsUntilDue),
        )
    }

    suspend fun loadAutoUpdateSubscriptions(): List<ProxyGroup> {
        return SagerDatabase.groupDao.subscriptions()
            .filter { it.subscription!!.autoUpdate }
    }
}

object SubscriptionAutoUpdateRunner {

    suspend fun run(
        nowSeconds: Long = currentEpochSeconds(),
        onBeforeUpdate: suspend (ProxyGroup) -> Unit = {},
    ) {
        run(
            subscriptions = SubscriptionAutoUpdatePlanner.loadAutoUpdateSubscriptions(),
            nowSeconds = nowSeconds,
            onBeforeUpdate = onBeforeUpdate,
        )
    }

    suspend fun run(
        subscriptions: List<ProxyGroup>,
        nowSeconds: Long,
        onBeforeUpdate: suspend (ProxyGroup) -> Unit = {},
    ) {
        for (profile in dueSubscriptions(subscriptions, nowSeconds)) {
            Logs.d("auto update: updating ${profile.displayName()}")
            onBeforeUpdate(profile)
            GroupUpdater.executeUpdate(profile, false)
        }
    }

    suspend fun dueSubscriptions(
        nowSeconds: Long = currentEpochSeconds(),
        connected: Boolean = DataStore.serviceState.connected,
    ): List<ProxyGroup> {
        return dueSubscriptions(
            subscriptions = SubscriptionAutoUpdatePlanner.loadAutoUpdateSubscriptions(),
            nowSeconds = nowSeconds,
            connected = connected,
        )
    }

    fun dueSubscriptions(
        subscriptions: List<ProxyGroup>,
        nowSeconds: Long,
        connected: Boolean = DataStore.serviceState.connected,
    ): List<ProxyGroup> {
        return autoUpdateCandidates(subscriptions, nowSeconds).filter { candidate ->
            if (!connected && candidate.updateWhenConnectedOnly) {
                return@filter false
            }
            if (candidate.secondsUntilDue > 0L) {
                Logs.d("auto update: not updating ${candidate.group.displayName()}")
                false
            } else {
                true
            }
        }.map(AutoUpdateCandidate::group)
    }
}

private fun autoUpdateCandidates(
    subscriptions: List<ProxyGroup>,
    nowSeconds: Long,
): List<AutoUpdateCandidate> {
    return subscriptions.mapNotNull { subscription ->
        autoUpdateCandidate(subscription, nowSeconds)
    }
}

private fun autoUpdateCandidate(
    group: ProxyGroup,
    nowSeconds: Long,
): AutoUpdateCandidate? {
    val subscription = group.subscription!!
    val interval = AutoUpdateInterval(subscription.autoUpdateDelay)
    if (!interval.isEnabled) return null

    return AutoUpdateCandidate(
        group = group,
        repeatIntervalMinutes = interval.minutes,
        secondsUntilDue = interval.secondsUntilDue(
            lastUpdatedSeconds = subscription.lastUpdated.toLong(),
            nowSeconds = nowSeconds,
        ),
        updateWhenConnectedOnly = subscription.updateWhenConnectedOnly,
    )
}

internal fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000L
