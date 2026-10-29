package fr.husi.ui.dashboard

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardWidgetGridTest {

    @Test
    fun `column count follows the width bands`() {
        assertEquals(8, dashboardGridColumnCount(contentWidth = 519.dp))
        assertEquals(12, dashboardGridColumnCount(contentWidth = 520.dp))
        assertEquals(12, dashboardGridColumnCount(contentWidth = 879.dp))
        assertEquals(16, dashboardGridColumnCount(contentWidth = 880.dp))
        // A desktop window takes more cards per row instead of stretching the four it starts with.
        assertEquals(20, dashboardGridColumnCount(contentWidth = 1360.dp))
        assertEquals(32, dashboardGridColumnCount(contentWidth = 2800.dp))
    }

    @Test
    fun `small widgets share the first row`() {
        val placements = placeDashboardGridItems(
            spans = listOf(4, 4),
            heights = listOf(100, 100),
            columnCount = 8,
            gap = 12,
        )

        assertEquals(
            listOf(
                DashboardGridPlacement(column = 0, top = 0, height = 100),
                DashboardGridPlacement(column = 4, top = 0, height = 100),
            ),
            placements,
        )
    }

    @Test
    fun `cards in a row stretch to the tallest card`() {
        val placements = placeDashboardGridItems(
            spans = listOf(4, 4),
            heights = listOf(100, 160),
            columnCount = 8,
            gap = 12,
        )

        assertEquals(
            listOf(
                DashboardGridPlacement(column = 0, top = 0, height = 160),
                DashboardGridPlacement(column = 4, top = 0, height = 160),
            ),
            placements,
        )
    }

    @Test
    fun `a wide widget starts a new row when the remaining cells are too few`() {
        val placements = placeDashboardGridItems(
            spans = listOf(4, 8),
            heights = listOf(100, 100),
            columnCount = 8,
            gap = 12,
        )

        assertEquals(
            listOf(
                DashboardGridPlacement(column = 0, top = 0, height = 100),
                DashboardGridPlacement(column = 0, top = 112, height = 100),
            ),
            placements,
        )
    }

    @Test
    fun `a later narrow widget backfills the gap left by a wide one`() {
        val placements = placeDashboardGridItems(
            spans = listOf(4, 4, 4, 8, 4),
            heights = listOf(100, 100, 100, 300, 150),
            columnCount = 16,
            gap = 12,
        )

        assertEquals(
            listOf(
                DashboardGridPlacement(column = 0, top = 0, height = 150),
                DashboardGridPlacement(column = 4, top = 0, height = 150),
                DashboardGridPlacement(column = 8, top = 0, height = 150),
                DashboardGridPlacement(column = 0, top = 162, height = 300),
                DashboardGridPlacement(column = 12, top = 0, height = 150),
            ),
            placements,
        )
    }

    @Test
    fun `rows stack below the tallest card of the previous row`() {
        val placements = placeDashboardGridItems(
            spans = listOf(4, 4, 4),
            heights = listOf(100, 200, 50),
            columnCount = 8,
            gap = 12,
        )

        assertEquals(
            listOf(
                DashboardGridPlacement(column = 0, top = 0, height = 200),
                DashboardGridPlacement(column = 4, top = 0, height = 200),
                DashboardGridPlacement(column = 0, top = 212, height = 50),
            ),
            placements,
        )
    }

    @Test
    fun `a span wider than the grid is clamped to a full row`() {
        val placements = placeDashboardGridItems(
            spans = listOf(12, 12),
            heights = listOf(100, 100),
            columnCount = 8,
            gap = 12,
        )

        assertEquals(
            listOf(
                DashboardGridPlacement(column = 0, top = 0, height = 100),
                DashboardGridPlacement(column = 0, top = 112, height = 100),
            ),
            placements,
        )
    }
}
