package fr.husi.bg

import fr.husi.database.AssetEntity
import fr.husi.database.DataStore
import fr.husi.database.SagerDatabase
import fr.husi.ktx.Logs
import fr.husi.repository.resolveRepository
import kotlinx.coroutines.flow.first

data class RouteAssetAutoUpdatePlan(
    val repeatIntervalMinutes: Int,
    val initialDelaySeconds: Long,
)

private sealed interface RouteAssetAutoUpdateCandidate {
    val repeatIntervalMinutes: Int
    val secondsUntilDue: Long
}

private data class ManagedRouteAssetAutoUpdateCandidate(
    override val repeatIntervalMinutes: Int,
    override val secondsUntilDue: Long,
) : RouteAssetAutoUpdateCandidate

private data class CustomRouteAssetAutoUpdateCandidate(
    val asset: AssetEntity,
    override val repeatIntervalMinutes: Int,
    override val secondsUntilDue: Long,
) : RouteAssetAutoUpdateCandidate

object RouteAssetAutoUpdatePlanner {

    suspend fun plan(): RouteAssetAutoUpdatePlan? {
        return plan(
            globalAutoUpdateDelayMinutes = DataStore.routeAssetsAutoUpdateDelay,
            globalLastUpdatedSeconds = DataStore.routeAssetsLastUpdated,
            assets = loadAutoUpdateAssets(),
            nowSeconds = currentEpochSeconds(),
        )
    }

    fun plan(
        globalAutoUpdateDelayMinutes: Int,
        globalLastUpdatedSeconds: Long,
        assets: List<AssetEntity>,
        nowSeconds: Long,
    ): RouteAssetAutoUpdatePlan? {
        val candidates = routeAssetAutoUpdateCandidates(
            globalAutoUpdateDelayMinutes = globalAutoUpdateDelayMinutes,
            globalLastUpdatedSeconds = globalLastUpdatedSeconds,
            assets = assets,
            nowSeconds = nowSeconds,
        )
        if (candidates.isEmpty()) return null

        return RouteAssetAutoUpdatePlan(
            repeatIntervalMinutes = candidates.minOf(RouteAssetAutoUpdateCandidate::repeatIntervalMinutes),
            initialDelaySeconds = candidates.minOf(RouteAssetAutoUpdateCandidate::secondsUntilDue),
        )
    }

    suspend fun loadAutoUpdateAssets(): List<AssetEntity> {
        return SagerDatabase.assetDao.getAll().first()
            .filter { it.autoUpdateDelay > 0 }
    }
}

object RouteAssetAutoUpdateRunner {

    suspend fun run(nowSeconds: Long = currentEpochSeconds()) {
        val repository = resolveRepository()

        if (isManagedAssetsDue(DataStore.routeAssetsAutoUpdateDelay, DataStore.routeAssetsLastUpdated, nowSeconds)) {
            runCatching {
                Logs.d("auto update: updating managed route assets")
                updateManagedRouteAssets(
                    externalAssetsDir = repository.externalAssetsDir,
                    cacheDir = repository.cacheDir,
                    checkedAtSeconds = nowSeconds,
                )
            }.onFailure { error ->
                if (error !is NoUpdateException) {
                    Logs.e("auto update: update managed route assets", error)
                }
            }
        }

        val dueAssets = dueAssets(
            assets = RouteAssetAutoUpdatePlanner.loadAutoUpdateAssets(),
            nowSeconds = nowSeconds,
        )
        for (asset in dueAssets) {
            runCatching {
                Logs.d("auto update: updating route asset ${asset.name}")
                asset.version = updateSingleRouteAsset(asset, repository.externalAssetsDir)
                asset.lastUpdated = nowSeconds
                SagerDatabase.assetDao.update(asset)
            }.onFailure {
                Logs.e("auto update: update route asset ${asset.name}", it)
            }
        }
    }

    fun isManagedAssetsDue(
        globalAutoUpdateDelayMinutes: Int,
        globalLastUpdatedSeconds: Long,
        nowSeconds: Long,
    ): Boolean {
        if (globalAutoUpdateDelayMinutes <= 0) return false
        return managedRouteAssetCandidate(
            globalAutoUpdateDelayMinutes = globalAutoUpdateDelayMinutes,
            globalLastUpdatedSeconds = globalLastUpdatedSeconds,
            nowSeconds = nowSeconds,
        ).secondsUntilDue <= 0L
    }

    fun dueAssets(
        assets: List<AssetEntity>,
        nowSeconds: Long,
    ): List<AssetEntity> {
        return assets.filter { it.autoUpdateDelay > 0 }
            .map { customRouteAssetCandidate(it, nowSeconds) }
            .filter { it.secondsUntilDue <= 0L }
            .map(CustomRouteAssetAutoUpdateCandidate::asset)
    }
}

private fun routeAssetAutoUpdateCandidates(
    globalAutoUpdateDelayMinutes: Int,
    globalLastUpdatedSeconds: Long,
    assets: List<AssetEntity>,
    nowSeconds: Long,
): List<RouteAssetAutoUpdateCandidate> {
    return buildList {
        if (globalAutoUpdateDelayMinutes > 0) {
            add(
                managedRouteAssetCandidate(
                    globalAutoUpdateDelayMinutes = globalAutoUpdateDelayMinutes,
                    globalLastUpdatedSeconds = globalLastUpdatedSeconds,
                    nowSeconds = nowSeconds,
                ),
            )
        }
        for (asset in assets) {
            if (asset.autoUpdateDelay > 0) {
                add(customRouteAssetCandidate(asset, nowSeconds))
            }
        }
    }
}

private fun managedRouteAssetCandidate(
    globalAutoUpdateDelayMinutes: Int,
    globalLastUpdatedSeconds: Long,
    nowSeconds: Long,
): ManagedRouteAssetAutoUpdateCandidate {
    val repeatIntervalMinutes = effectiveRouteAssetDelayMinutes(globalAutoUpdateDelayMinutes)
    return ManagedRouteAssetAutoUpdateCandidate(
        repeatIntervalMinutes = repeatIntervalMinutes,
        secondsUntilDue = routeAssetSecondsUntilDue(
            lastUpdatedSeconds = globalLastUpdatedSeconds,
            repeatIntervalMinutes = repeatIntervalMinutes,
            nowSeconds = nowSeconds,
        ),
    )
}

private fun customRouteAssetCandidate(
    asset: AssetEntity,
    nowSeconds: Long,
): CustomRouteAssetAutoUpdateCandidate {
    val repeatIntervalMinutes = effectiveRouteAssetDelayMinutes(asset.autoUpdateDelay)
    return CustomRouteAssetAutoUpdateCandidate(
        asset = asset,
        repeatIntervalMinutes = repeatIntervalMinutes,
        secondsUntilDue = routeAssetSecondsUntilDue(
            lastUpdatedSeconds = asset.lastUpdated,
            repeatIntervalMinutes = repeatIntervalMinutes,
            nowSeconds = nowSeconds,
        ),
    )
}

private fun effectiveRouteAssetDelayMinutes(autoUpdateDelayMinutes: Int): Int {
    return autoUpdateDelayMinutes.coerceAtLeast(1)
}

private fun routeAssetSecondsUntilDue(
    lastUpdatedSeconds: Long,
    repeatIntervalMinutes: Int,
    nowSeconds: Long,
): Long {
    val elapsedSeconds = nowSeconds - lastUpdatedSeconds
    val delaySeconds = repeatIntervalMinutes.toLong() * 60L
    return (delaySeconds - elapsedSeconds).coerceAtLeast(0L)
}

expect object RouteAssetUpdater {
    suspend fun reconfigureUpdater()
}
