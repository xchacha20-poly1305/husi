package fr.husi.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.Text
import fr.husi.core.NetworkQualityPhase
import fr.husi.ktx.formatBytes
import fr.husi.resources.Res
import fr.husi.resources.download_capacity
import fr.husi.resources.download_responsiveness
import fr.husi.resources.elapsed_time
import fr.husi.resources.idle_latency
import fr.husi.resources.responsiveness_rpm
import fr.husi.resources.speed
import fr.husi.resources.upload_capacity
import fr.husi.resources.upload_responsiveness
import org.jetbrains.compose.resources.stringResource

@Immutable
internal data class NetworkQualityReport(
    val phase: NetworkQualityPhase = NetworkQualityPhase.Idle,
    val downloadCapacity: Long = 0L,
    val uploadCapacity: Long = 0L,
    val downloadRpm: Int = 0,
    val uploadRpm: Int = 0,
    val idleLatencyMs: Int = 0,
    val elapsedMs: Long = 0L,
)

@Composable
internal fun NetworkQualityReportContent(
    report: NetworkQualityReport,
    modifier: Modifier = Modifier,
) {
    val downloadMeasured = report.phase >= NetworkQualityPhase.Download
    val uploadMeasured = report.phase >= NetworkQualityPhase.Upload

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ReportLine(
            label = stringResource(Res.string.idle_latency),
            value = report.idleLatencyMs.takeIf { it > 0 }?.let { "$it ms" },
        )
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        ReportLine(
            label = stringResource(Res.string.download_capacity),
            value =
                report.downloadCapacity
                    .takeIf { downloadMeasured }
                    ?.let { stringResource(Res.string.speed, it.formatBytes()) },
        )
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        ReportLine(
            label = stringResource(Res.string.download_responsiveness),
            value =
                report.downloadRpm
                    .takeIf { downloadMeasured }
                    ?.let { stringResource(Res.string.responsiveness_rpm, it.toString()) },
        )
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        ReportLine(
            label = stringResource(Res.string.upload_capacity),
            value =
                report.uploadCapacity
                    .takeIf { uploadMeasured }
                    ?.let { stringResource(Res.string.speed, it.formatBytes()) },
        )
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        ReportLine(
            label = stringResource(Res.string.upload_responsiveness),
            value =
                report.uploadRpm
                    .takeIf { uploadMeasured }
                    ?.let { stringResource(Res.string.responsiveness_rpm, it.toString()) },
        )
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        ReportLine(
            label = stringResource(Res.string.elapsed_time),
            value = report.elapsedMs.takeIf { it > 0 }?.let { "$it ms" },
        )
    }
}

@Composable
private fun ReportLine(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = value.orEmpty(),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun SwitchLine(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun ErrorText(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        modifier = modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}
