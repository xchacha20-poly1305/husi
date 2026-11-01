package fr.husi.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import fr.husi.compose.material3.Text
import fr.husi.ktx.formatBytes
import fr.husi.resources.Res
import fr.husi.resources.dashboard_traffic_total
import fr.husi.resources.dashboard_traffic_usage
import fr.husi.resources.download
import fr.husi.resources.upload
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TrafficUsageCard(uiState: DashboardState, modifier: Modifier = Modifier) {
    val uploadColor = MaterialTheme.colorScheme.primary
    val downloadColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val upload = uiState.txTotal.fastCoerceAtLeast(0)
    val download = uiState.rxTotal.fastCoerceAtLeast(0)
    val total = upload.toDouble() + download.toDouble()

    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.dashboard_traffic_usage), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Canvas(Modifier.size(88.dp)) {
                    val stroke = Stroke(width = 12.dp.toPx())
                    val inset = stroke.width / 2
                    val chartSize = Size(size.width - stroke.width, size.height - stroke.width)
                    drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), chartSize, style = stroke)
                    if (total > 0) {
                        val uploadSweep = (upload / total * 360).toFloat()
                        drawArc(uploadColor, -90f, uploadSweep, false, Offset(inset, inset), chartSize, style = stroke)
                        drawArc(
                            downloadColor,
                            -90f + uploadSweep,
                            360f - uploadSweep,
                            false,
                            Offset(inset, inset),
                            chartSize,
                            style = stroke,
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrafficUsageLegend(uploadColor, stringResource(Res.string.upload), upload)
                    TrafficUsageLegend(downloadColor, stringResource(Res.string.download), download)
                    TrafficUsageLegend(trackColor, stringResource(Res.string.dashboard_traffic_total), total.toLong())
                }
            }
        }
    }
}

@Composable
private fun TrafficUsageLegend(color: Color, label: String, bytes: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = bytes.formatBytes(), style = MaterialTheme.typography.bodyMedium)
    }
}
