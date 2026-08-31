package fr.husi.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardWidgetTest {

    @Test
    fun `decode blank falls back to every widget visible`() {
        assertEquals(defaultDashboardWidgets(), decodeDashboardWidgets(""))
    }

    @Test
    fun `decode invalid json falls back to every widget visible`() {
        assertEquals(defaultDashboardWidgets(), decodeDashboardWidgets("not json"))
    }

    @Test
    fun `encode then decode keeps order and visibility`() {
        val entries = defaultDashboardWidgets()
            .setWidgetVisible(DashboardWidget.SourceAddress, false)
            .let { it.reorderVisibleWidgets(it.visibleWidgets().reversed()) }

        assertEquals(entries, decodeDashboardWidgets(encodeDashboardWidgets(entries)))
    }

    @Test
    fun `decode drops unknown identifiers`() {
        val stored = """
            [
                {"id":"status","visible":true},
                {"id":"widgetFromTheFuture","visible":true}
            ]
        """.trimIndent()

        assertEquals(DashboardWidget.Status, decodeDashboardWidgets(stored).first().widget)
        assertEquals(DashboardWidget.entries.size, decodeDashboardWidgets(stored).size)
    }

    @Test
    fun `decode appends widgets missing from the stored value`() {
        val stored = encodeDashboardWidgets(
            listOf(DashboardWidgetEntry(DashboardWidget.Status, visible = false)),
        )
        val decoded = decodeDashboardWidgets(stored)

        assertEquals(DashboardWidget.entries.size, decoded.size)
        assertEquals(DashboardWidgetEntry(DashboardWidget.Status, visible = false), decoded.first())
        assertEquals(
            DashboardWidget.entries.filterNot { it == DashboardWidget.Status },
            decoded.drop(1).map { it.widget },
        )
        assertEquals(emptyList(), decoded.drop(1).filterNot { it.visible })
    }

    @Test
    fun `reorderVisibleWidgets keeps hidden widgets after the visible ones`() {
        val hidden = defaultDashboardWidgets().setWidgetVisible(DashboardWidget.ProxySpeed, false)
        val swapped = hidden.visibleWidgets().toMutableList().apply { add(1, removeAt(0)) }
        val entries = hidden.reorderVisibleWidgets(swapped)

        assertEquals(listOf(DashboardWidget.ProxySpeed), entries.hiddenWidgets())
        assertEquals(
            listOf(DashboardWidget.Status, DashboardWidget.DirectSpeed),
            entries.visibleWidgets().take(2),
        )
    }

    @Test
    fun `reorderVisibleWidgets keeps visible widgets missing from the given order`() {
        val entries = defaultDashboardWidgets()
            .reorderVisibleWidgets(listOf(DashboardWidget.NetworkInterfaces))

        assertEquals(DashboardWidget.NetworkInterfaces, entries.visibleWidgets().first())
        assertEquals(DashboardWidget.entries.size, entries.size)
        assertEquals(emptyList(), entries.hiddenWidgets())
    }

    @Test
    fun `setWidgetVisible appends a restored widget to the end of the visible ones`() {
        val entries = defaultDashboardWidgets()
            .setWidgetVisible(DashboardWidget.ProxySpeed, false)
            .setWidgetVisible(DashboardWidget.ProxySpeed, true)

        assertEquals(emptyList(), entries.hiddenWidgets())
        assertEquals(DashboardWidget.ProxySpeed, entries.visibleWidgets().last())
    }
}
