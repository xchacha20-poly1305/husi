package fr.husi.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import fr.husi.compose.material3.Text
import fr.husi.libcore.Libcore
import fr.husi.resources.Res
import fr.husi.resources.speed
import fr.husi.resources.status_direct
import fr.husi.resources.status_proxy
import org.jetbrains.compose.resources.stringResource

// Reference: https://github.com/SagerNet/sing-box-for-android/blob/8f6343802a6d8e0fa478d9e642cbb58c147e671b/app/src/main/java/io/nekohasekai/sfa/compose/LineChart.kt

@Composable
internal fun DashboardSpeedRow(
    uiState: DashboardState,
    modifier: Modifier = Modifier,
) {
    val proxyCeiling = uiState.proxySpeedHistory.maxOrNull() ?: 0f
    val directCeiling = uiState.directSpeedHistory.maxOrNull() ?: 0f
    val valueCeiling = maxOf(proxyCeiling, directCeiling, 1f) * 1.2f

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpeedCard(
            title = stringResource(Res.string.status_proxy),
            txRate = uiState.txRateProxy,
            rxRate = uiState.rxRateProxy,
            history = uiState.proxySpeedHistory,
            lineColor = MaterialTheme.colorScheme.primary,
            valueCeiling = valueCeiling,
            modifier = Modifier.weight(1f),
        )
        SpeedCard(
            title = stringResource(Res.string.status_direct),
            txRate = uiState.txRateDirect,
            rxRate = uiState.rxRateDirect,
            history = uiState.directSpeedHistory,
            lineColor = MaterialTheme.colorScheme.tertiary,
            valueCeiling = valueCeiling,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SpeedCard(
    title: String,
    txRate: Long,
    rxRate: Long,
    history: List<Float>,
    lineColor: Color,
    valueCeiling: Float,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "▲ " + stringResource(
                    Res.string.speed,
                    Libcore.formatBytes(txRate),
                ),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "▼ " + stringResource(
                    Res.string.speed,
                    Libcore.formatBytes(rxRate),
                ),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LineChart(
                data = history,
                lineColor = lineColor,
                animate = false,
                valueCeiling = valueCeiling,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun LineChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    animate: Boolean = true,
    valueCeiling: Float? = null,
) {
    val animationProgress = remember { Animatable(if (animate) 0f else 1f) }

    LaunchedEffect(data) {
        if (animate) {
            animationProgress.snapTo(0f)
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300),
            )
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
    ) {
        val width = size.width
        val height = size.height
        val dataMax = maxOf(data.maxOrNull() ?: 1f, 1f)
        val maxValue = valueCeiling?.fastCoerceAtLeast(1f) ?: (dataMax * 1.2f)
        val pointCount = data.size

        val gridLineCount = 3
        for (i in 0..gridLineCount) {
            val y = height * i / gridLineCount
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
            )
        }

        if (pointCount <= 1) return@Canvas

        val spacing = width / (pointCount - 1).toFloat()
        val points = data.mapIndexed { index, value ->
            val x = index * spacing
            val normalizedValue = (value / maxValue).fastCoerceIn(0f, 1f)
            Offset(x, height * (1 - normalizedValue))
        }

        val progress = if (animate) animationProgress.value else 1f
        val visibleCount = maxOf(
            2,
            ((points.size - 1) * progress).toInt() + 1,
        ).fastCoerceAtMost(points.size)

        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until visibleCount) {
            path.lineTo(points[i].x, points[i].y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        val lastPoint = points[visibleCount - 1]
        val fillPath = Path()
        fillPath.addPath(path)
        fillPath.lineTo(lastPoint.x, height)
        fillPath.lineTo(points[0].x, height)
        fillPath.close()
        drawPath(
            path = fillPath,
            color = lineColor.copy(alpha = 0.1f),
        )
    }
}
