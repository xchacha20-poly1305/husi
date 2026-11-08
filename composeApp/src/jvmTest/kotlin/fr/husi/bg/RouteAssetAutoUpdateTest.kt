package fr.husi.bg

import fr.husi.database.AssetEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteAssetAutoUpdateTest {

    @Test
    fun `planner uses earliest due time across managed and custom assets`() {
        val nowSeconds = 10_000L

        val plan = RouteAssetAutoUpdatePlanner.plan(
            globalAutoUpdateDelayMinutes = 60,
            globalLastUpdatedSeconds = nowSeconds - 20 * 60,
            assets = listOf(
                autoUpdateAsset(name = "fast.srs", delayMinutes = 15, lastUpdatedSeconds = nowSeconds - 10 * 60),
                autoUpdateAsset(name = "slow.srs", delayMinutes = 90, lastUpdatedSeconds = nowSeconds - 5 * 60),
            ),
            nowSeconds = nowSeconds,
        )

        assertEquals(15, plan?.repeatIntervalMinutes)
        assertEquals(5 * 60L, plan?.initialDelaySeconds)
    }

    @Test
    fun `planner returns null when every route asset auto update is disabled`() {
        assertNull(
            RouteAssetAutoUpdatePlanner.plan(
                globalAutoUpdateDelayMinutes = 0,
                globalLastUpdatedSeconds = 0L,
                assets = listOf(
                    AssetEntity(name = "disabled.srs", autoUpdateDelay = 0),
                ),
                nowSeconds = 1_000L,
            ),
        )
    }

    @Test
    fun `managed assets are due immediately when overdue`() {
        assertTrue(
            RouteAssetAutoUpdateRunner.isManagedAssetsDue(
                globalAutoUpdateDelayMinutes = 30,
                globalLastUpdatedSeconds = 100L,
                nowSeconds = 100L + 31L * 60L,
            ),
        )
        assertFalse(
            RouteAssetAutoUpdateRunner.isManagedAssetsDue(
                globalAutoUpdateDelayMinutes = 30,
                globalLastUpdatedSeconds = 100L,
                nowSeconds = 100L + 10L * 60L,
            ),
        )
    }

    @Test
    fun `due assets only include overdue custom assets with positive intervals`() {
        val dueAssets = RouteAssetAutoUpdateRunner.dueAssets(
            assets = listOf(
                autoUpdateAsset(name = "due.srs", delayMinutes = 10, lastUpdatedSeconds = 0L),
                autoUpdateAsset(name = "not-due.srs", delayMinutes = 10, lastUpdatedSeconds = 8 * 60L),
                AssetEntity(name = "disabled.srs", autoUpdateDelay = 0, lastUpdated = 0L),
            ),
            nowSeconds = 12 * 60L,
        )

        assertEquals(listOf("due.srs"), dueAssets.map { it.name })
    }

    private fun autoUpdateAsset(
        name: String,
        delayMinutes: Int,
        lastUpdatedSeconds: Long,
    ): AssetEntity {
        return AssetEntity(
            name = name,
            autoUpdateDelay = delayMinutes,
            lastUpdated = lastUpdatedSeconds,
        )
    }
}
