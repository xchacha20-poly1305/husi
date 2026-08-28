package fr.husi.bg

import androidx.compose.ui.util.fastCoerceAtLeast

/**
 * An auto update interval in minutes, as stored by subscription groups
 * ([fr.husi.database.SubscriptionBean.autoUpdateDelay]) and by route assets
 * ([fr.husi.database.AssetEntity.autoUpdateDelay]).
 *
 * A non-positive interval means the user turned auto update off. Both schedulers share this rule,
 * so an interval of zero can never silently degrade into "update as often as possible".
 */
@JvmInline
internal value class AutoUpdateInterval(val minutes: Int) {

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }

    val isEnabled: Boolean get() = minutes > 0

    fun secondsUntilDue(lastUpdatedSeconds: Long, nowSeconds: Long): Long {
        val elapsedSeconds = nowSeconds - lastUpdatedSeconds
        val intervalSeconds = minutes.toLong() * SECONDS_PER_MINUTE
        return (intervalSeconds - elapsedSeconds).fastCoerceAtLeast(0L)
    }

}
