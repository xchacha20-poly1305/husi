package fr.husi.ui.tailscale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.husi.compose.PreferenceGroupDefaults
import fr.husi.compose.TooltipIconButton
import fr.husi.compose.material3.Card
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Surface
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.available
import fr.husi.resources.connecting
import fr.husi.resources.file_upload
import fr.husi.resources.play_arrow
import fr.husi.resources.start
import fr.husi.resources.stop
import fr.husi.resources.taildrop
import fr.husi.resources.taildrop_drop_zone
import fr.husi.resources.taildrop_send_to
import fr.husi.resources.tailscale_ping
import fr.husi.resources.tailscale_ping_derp
import fr.husi.resources.tailscale_ping_derp_region
import fr.husi.resources.tailscale_ping_direct
import fr.husi.resources.tailscale_ping_endpoint
import fr.husi.resources.tailscale_ping_no_data
import fr.husi.resources.tailscale_ping_peer_relay
import fr.husi.resources.tailscale_ping_peer_relay_address
import fr.husi.tailscale.TailscalePingState
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

private val DropZoneCornerRadius = 16.dp
private val DropZoneShape = RoundedCornerShape(DropZoneCornerRadius)
private const val MIN_SPARKLINE_SAMPLES = 2

/**
 * A titled block on the peer screen that is not a list of settings, so it is one card with room
 * to breathe instead of a group of segmented rows. It shares the groups' shape and colour, so it
 * still sits on the same ground as them.
 */
@Composable
private fun PeerSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = PreferenceGroupDefaults.groupShape,
        color = PreferenceGroupDefaults.itemContainerColor,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
internal fun PingCard(
    ping: TailscalePingState,
    enabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    PeerSectionCard(
        title = stringResource(Res.string.tailscale_ping),
        action = {
            PingToggleButton(
                running = ping.running,
                enabled = ping.running || enabled,
                onClick = if (ping.running) onStop else onStart,
            )
        },
    ) {
        when {
            ping.hasResult -> PingResult(ping)
            ping.running -> Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(Res.string.connecting),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> Text(
                text = stringResource(Res.string.tailscale_ping_no_data),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ping.error.isNotEmpty()) {
            Text(
                text = ping.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PingToggleButton(running: Boolean, enabled: Boolean, onClick: () -> Unit) {
    TooltipIconButton(
        onClick = onClick,
        icon = vectorResource(if (running) Res.drawable.stop else Res.drawable.play_arrow),
        contentDescription = stringResource(if (running) Res.string.stop else Res.string.start),
        enabled = enabled,
        colors = if (running) {
            IconButtonDefaults.filledTonalIconButtonColors()
        } else {
            IconButtonDefaults.filledIconButtonColors()
        },
    )
}

@Composable
private fun PingResult(ping: TailscalePingState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.available, ping.latencyMilliseconds.roundToInt()),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.weight(1f),
        )
        PingPathBadge(ping)
    }
    if (ping.latencyHistory.size >= MIN_SPARKLINE_SAMPLES) {
        PingSparkline(samples = ping.latencyHistory)
    }
    val details = buildList {
        if (ping.endpoint.isNotEmpty()) {
            add(Res.string.tailscale_ping_endpoint to ping.endpoint)
        }
        if (ping.derpRegionCode.isNotEmpty()) {
            add(Res.string.tailscale_ping_derp_region to ping.derpRegionCode)
        }
        if (ping.peerRelay.isNotEmpty()) {
            add(Res.string.tailscale_ping_peer_relay_address to ping.peerRelay)
        }
    }
    if (details.isNotEmpty()) PingDetails(details)
}

@Composable
private fun PingPathBadge(ping: TailscalePingState) {
    val colors = MaterialTheme.colorScheme
    val (label, container) = when {
        ping.peerRelay.isNotEmpty() -> Res.string.tailscale_ping_peer_relay to colors.secondaryContainer
        ping.direct -> Res.string.tailscale_ping_direct to colors.tertiaryContainer
        else -> Res.string.tailscale_ping_derp to colors.secondaryContainer
    }
    Surface(shape = CircleShape, color = container) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Label and value pairs, selectable so an address can be copied without a button per line. */
@Composable
private fun PingDetails(details: List<Pair<StringResource, String>>) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, value) in details) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(label),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Latency line over a tinted area, with the highest and lowest sample labelled on the right. */
@Composable
private fun PingSparkline(samples: List<Float>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val areaColor = lineColor.copy(alpha = 0.16f)
    val min = samples.min()
    val max = samples.max()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            val strokeWidth = 2.dp.toPx()
            // Inset by half the stroke so the extremes are not clipped at the edges.
            val top = strokeWidth / 2
            val plotHeight = size.height - strokeWidth
            val span = (max - min).takeIf { value -> value > 0f } ?: 1f
            val line = Path()
            samples.forEachIndexed { index, sample ->
                val x = index.toFloat() / samples.lastIndex.toFloat() * size.width
                val y = top + plotHeight - (sample - min) / span * plotHeight
                if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            val area = Path().also { area ->
                area.addPath(line)
                area.lineTo(size.width, size.height)
                area.lineTo(0f, size.height)
                area.close()
            }
            drawPath(path = area, color = areaColor)
            drawPath(
                path = line,
                color = lineColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            for (value in listOf(max, min)) {
                Text(
                    text = stringResource(Res.string.available, value.roundToInt()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** The whole card is the drop target, passed in through [modifier]; [dragging] highlights the zone. */
@Composable
internal fun SendFilesCard(
    peerName: String,
    dragging: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PeerSectionCard(
        title = stringResource(Res.string.taildrop),
        modifier = modifier,
    ) {
        DropZone(
            title = stringResource(Res.string.taildrop_send_to, peerName),
            dragging = dragging,
            onClick = onClick,
        )
    }
}

@Composable
private fun DropZone(
    title: String,
    dragging: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val borderColor = if (dragging) colors.primary else colors.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 176.dp)
            .clip(DropZoneShape)
            .background(if (dragging) colors.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
            .drawBehind {
                val strokeWidth = 1.5.dp.toPx()
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius(DropZoneCornerRadius.toPx()),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                        ),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.file_upload),
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.taildrop_drop_zone),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
