package fr.husi.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.Text
import fr.husi.core.NatBehaviour
import fr.husi.core.StunPhase
import fr.husi.resources.Res
import fr.husi.resources.available
import fr.husi.resources.latency
import fr.husi.resources.loading
import fr.husi.resources.nat_address_and_port_dependent
import fr.husi.resources.nat_address_dependent
import fr.husi.resources.nat_endpoint_independent
import fr.husi.resources.nat_external_address
import fr.husi.resources.nat_filtering
import fr.husi.resources.nat_mapping
import fr.husi.resources.nat_result_hint
import fr.husi.resources.nat_type_detection
import fr.husi.resources.nat_type_not_supported
import fr.husi.resources.unknown
import org.jetbrains.compose.resources.stringResource

@Immutable
internal data class StunReport(
    val phase: StunPhase = StunPhase.Binding,
    val externalAddress: String? = null,
    val latencyMs: Int? = null,
    val mapping: NatBehaviour = NatBehaviour.Unknown,
    val filtering: NatBehaviour = NatBehaviour.Unknown,
    val natTypeUnsupported: Boolean = true,
)

@Composable
internal fun StunReportContent(
    report: StunReport,
    isDoing: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.nat_result_hint),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (isDoing) {
                    Text(
                        text = stringResource(Res.string.loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        ResultValue(
            label = stringResource(Res.string.nat_external_address),
            value = report.externalAddress,
            highlight = true,
        )

        Column {
            ResultLine(
                label = stringResource(Res.string.latency),
                value = report.latencyMs?.let { stringResource(Res.string.available, it) },
            )
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            if (!isDoing && report.natTypeUnsupported) {
                ResultLine(
                    label = stringResource(Res.string.nat_type_detection),
                    value = stringResource(Res.string.nat_type_not_supported),
                )
            } else {
                ResultLine(
                    label = stringResource(Res.string.nat_mapping),
                    value =
                        natBehaviourLabel(report.mapping).takeIf {
                            report.phase > StunPhase.NatMapping
                        },
                    color = natBehaviourColor(report.mapping),
                )
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
                ResultLine(
                    label = stringResource(Res.string.nat_filtering),
                    value =
                        natBehaviourLabel(report.filtering).takeIf {
                            report.phase > StunPhase.NatFiltering
                        },
                    color = natBehaviourColor(report.filtering),
                )
            }
        }
    }
}

@Composable
private fun ResultValue(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    color: Color? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().heightIn(min = if (highlight) 72.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        if (value == null) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PendingIndicator()
            }
        } else {
            SelectionContainer {
                Text(
                    text = value,
                    color =
                        color
                            ?: if (highlight) MaterialTheme.colorScheme.primary
                            else Color.Unspecified,
                    style =
                        if (highlight) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                    fontWeight = if (highlight) FontWeight.Bold else null,
                )
            }
        }
    }
}

@Composable
private fun ResultLine(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    color: Color? = null,
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
        if (value == null) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                PendingIndicator()
            }
        } else {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    color = color ?: Color.Unspecified,
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PendingIndicator(modifier: Modifier = Modifier) {
    CircularWavyProgressIndicator(
        modifier = modifier,
    )
}

@Composable
private fun natBehaviourLabel(value: NatBehaviour): String =
    stringResource(
        when (value) {
            NatBehaviour.Unknown -> Res.string.unknown
            NatBehaviour.EndpointIndependent -> Res.string.nat_endpoint_independent
            NatBehaviour.AddressDependent -> Res.string.nat_address_dependent
            NatBehaviour.AddressAndPortDependent -> Res.string.nat_address_and_port_dependent
        },
    )

private fun natBehaviourColor(value: NatBehaviour): Color =
    when (value) {
        NatBehaviour.EndpointIndependent -> Color.Green
        NatBehaviour.AddressDependent -> Color.Yellow
        NatBehaviour.AddressAndPortDependent -> Color.Red
        NatBehaviour.Unknown -> Color.Unspecified
    }
