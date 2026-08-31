package fr.husi.ui.dashboard

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Inspired by https://github.com/chen08209/FlClash/blob/62addf738a76b1a492e19af2dbabdb6d572b9e72/lib/enum/enum.dart#L248

enum class DashboardWidgetWidth {
    Half,
    Full,
}

enum class DashboardWidget(
    val preferenceValue: String,
    val width: DashboardWidgetWidth,
) {
    ProxySpeed("proxySpeed", DashboardWidgetWidth.Half),
    DirectSpeed("directSpeed", DashboardWidgetWidth.Half),
    Status("status", DashboardWidgetWidth.Half),
    OpenConnect("openConnect", DashboardWidgetWidth.Full),
    OpenVPN("openVPN", DashboardWidgetWidth.Full),
    SourceAddress("sourceAddress", DashboardWidgetWidth.Half),
    ClashMode("clashMode", DashboardWidgetWidth.Half),
    NetworkInterfaces("networkInterfaces", DashboardWidgetWidth.Full),
}

@Immutable
data class DashboardWidgetEntry(
    val widget: DashboardWidget,
    val visible: Boolean,
)

fun defaultDashboardWidgets(): List<DashboardWidgetEntry> = DashboardWidget.entries.map {
    DashboardWidgetEntry(widget = it, visible = true)
}

fun List<DashboardWidgetEntry>.visibleWidgets(): List<DashboardWidget> =
    filter { it.visible }.map { it.widget }

fun List<DashboardWidgetEntry>.hiddenWidgets(): List<DashboardWidget> =
    filterNot { it.visible }.map { it.widget }

fun List<DashboardWidgetEntry>.reorderVisibleWidgets(
    order: List<DashboardWidget>,
): List<DashboardWidgetEntry> {
    val entriesByWidget = associateBy { it.widget }
    val reordered = order.mapNotNull { entriesByWidget[it] }.filter { it.visible }
    val untouched = filter { it.visible && it.widget !in order }
    return reordered + untouched + filterNot { it.visible }
}

fun List<DashboardWidgetEntry>.setWidgetVisible(
    widget: DashboardWidget,
    visible: Boolean,
): List<DashboardWidgetEntry> {
    val updated = map { if (it.widget == widget) it.copy(visible = visible) else it }
    return updated.filter { it.visible } + updated.filterNot { it.visible }
}

@Serializable
private data class StoredDashboardWidget(
    val id: String,
    val visible: Boolean,
)

private val dashboardWidgetJson = Json {
    ignoreUnknownKeys = true
}

fun encodeDashboardWidgets(entries: List<DashboardWidgetEntry>): String {
    val stored = entries.map {
        StoredDashboardWidget(id = it.widget.preferenceValue, visible = it.visible)
    }
    return dashboardWidgetJson.encodeToString(stored)
}

fun decodeDashboardWidgets(stored: String): List<DashboardWidgetEntry> {
    if (stored.isBlank()) return defaultDashboardWidgets()

    val decoded = try {
        dashboardWidgetJson.decodeFromString<List<StoredDashboardWidget>>(stored)
    } catch (_: Exception) {
        return defaultDashboardWidgets()
    }

    val widgetsByPreferenceValue = DashboardWidget.entries.associateBy { it.preferenceValue }
    val entries = mutableListOf<DashboardWidgetEntry>()
    for (item in decoded) {
        val widget = widgetsByPreferenceValue[item.id] ?: continue
        if (entries.any { it.widget == widget }) continue
        entries += DashboardWidgetEntry(widget = widget, visible = item.visible)
    }

    for (widget in DashboardWidget.entries) {
        if (entries.none { it.widget == widget }) {
            entries += DashboardWidgetEntry(widget = widget, visible = true)
        }
    }
    return entries
}
