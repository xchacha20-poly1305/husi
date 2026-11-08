package fr.husi.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object PreferenceGroupDefaults {
    /**
     * Radius of the group as a whole. The pair of radii is spelled out here rather than taken from
     * [MaterialTheme.shapes] so that the outer and inner corners cannot drift apart.
     */
    val GroupCornerRadius = 20.dp

    /** Radius of a single row. Rows at the edges are rounded up by the group's clip. */
    val ItemCornerRadius = 4.dp

    val groupShape: Shape
        @Composable get() = RoundedCornerShape(GroupCornerRadius)

    val itemShape: Shape
        @Composable get() = RoundedCornerShape(ItemCornerRadius)

    /** A row is brighter than the ground it sits on; see [screenContainerColor]. */
    val itemContainerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceBright

    /**
     * Ground that a preference list sits on.
     *
     * It is darker than [itemContainerColor] so that rows read as raised entries, which is the
     * relationship a native Android preference page uses in both light and dark themes.
     * [ProvidePreferenceLocals] paints it.
     */
    val screenContainerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow

    /** Gap that separates two rows, matching a menu's group spacing. */
    val ItemSpacing = 2.dp

    /** Height every row reaches, so that rows without an icon match rows with one. */
    val ItemMinHeight = 72.dp

    /** Distance from the group's left edge to a [PreferenceCategory] title. */
    val CategoryTitleIndent = 8.dp

    /** Arrangement for a nested column of rows, such as the body of an `AnimatedVisibility`. */
    val itemArrangement: Arrangement.Vertical = Arrangement.spacedBy(ItemSpacing)
}

/**
 * Container of a single preference row.
 *
 * Every row draws its own surface, so a group reads as separate entries the way a native Android
 * preference group does, rather than as one card cut up by divider lines.
 */
@Composable
fun PreferenceItemSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.heightIn(min = PreferenceGroupDefaults.ItemMinHeight),
        shape = PreferenceGroupDefaults.itemShape,
        color = PreferenceGroupDefaults.itemContainerColor,
        content = content,
    )
}
