package fr.husi.ui.dashboard

import androidx.compose.runtime.Composable
import fr.husi.resources.Res
import fr.husi.resources.smart_block_blocked
import fr.husi.resources.smart_block_degraded
import fr.husi.resources.smart_block_normal
import fr.husi.resources.smart_rank_most_used
import fr.husi.resources.smart_rank_occasional_used
import fr.husi.resources.smart_rank_rarely_used
import kotlin.math.abs
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.stringResource

object SmartUsageRank {
    const val MOST_USED = "MostUsed"
    const val OCCASIONAL_USED = "OccasionalUsed"
    const val RARELY_USED = "RarelyUsed"
}

internal fun formatSmartWeight(weight: Double): String {
    if (!weight.isFinite()) return ""
    val scaled = (abs(weight) * 100.0).roundToLong()
    if (scaled == 0L) return "0"
    val sign = if (weight < 0.0) "-" else ""
    val whole = scaled / 100L
    val fraction = (scaled % 100L).toInt()
    return when {
        fraction == 0 -> "$sign$whole"
        fraction % 10 == 0 -> "$sign$whole.${fraction / 10}"
        else -> "$sign$whole.${fraction.toString().padStart(2, '0')}"
    }
}

@Composable
internal fun smartRankLabel(rank: String): String = when (rank) {
    SmartUsageRank.MOST_USED -> stringResource(Res.string.smart_rank_most_used)
    SmartUsageRank.OCCASIONAL_USED -> stringResource(Res.string.smart_rank_occasional_used)
    SmartUsageRank.RARELY_USED -> stringResource(Res.string.smart_rank_rarely_used)
    else -> rank
}

@Composable
internal fun smartBlockStateLabel(state: String): String = when (state) {
    SmartConnectionInfo.SMART_BLOCK_NORMAL -> stringResource(Res.string.smart_block_normal)
    SmartConnectionInfo.SMART_BLOCK_DEGRADED -> stringResource(Res.string.smart_block_degraded)
    SmartConnectionInfo.SMART_BLOCK_BLOCKED -> stringResource(Res.string.smart_block_blocked)
    else -> state
}
