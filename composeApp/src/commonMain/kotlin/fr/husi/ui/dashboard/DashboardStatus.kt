package fr.husi.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import fr.husi.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.setPlainText
import kotlinx.coroutines.launch
import fr.husi.resources.*
import fr.husi.libcore.Libcore
import fr.husi.ui.openconnect.OpenConnectAuthController
import fr.husi.ui.openvpn.OpenVPNAuthController
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter

@Composable
internal fun DashboardStatusScreen(
    modifier: Modifier = Modifier,
    uiState: DashboardState,
    openConnectController: OpenConnectAuthController,
    openVPNController: OpenVPNAuthController,
    bottomPadding: Dp,
    selectClashMode: (mode: String) -> Unit,
    showError: (String) -> Unit,
    onCopySuccess: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var sourceAddressesVisible by remember { mutableStateOf(false) }
    var networkInterfacesVisible by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomPadding + 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        DashboardSpeedRow(uiState = uiState)

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.status_status),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(Res.string.status_memory))
                    Text(Libcore.formatMemoryBytes(uiState.memory))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(Res.string.status_goroutines))
                    Text(uiState.goroutines.toString())
                }
            }
        }

        OpenConnectStatusSection(
            controller = openConnectController,
            showError = showError,
        )

        OpenVPNStatusSection(
            controller = openVPNController,
            showError = showError,
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HideableSectionTitle(
                    title = stringResource(Res.string.source_address),
                    visible = sourceAddressesVisible,
                    onToggleVisible = { sourceAddressesVisible = !sourceAddressesVisible },
                )
                SourceAddressRow(
                    label = "IPv4",
                    address = uiState.ipv4,
                    visible = sourceAddressesVisible,
                    onCopy = { value ->
                        scope.launch {
                            clipboard.setPlainText(value)
                        }
                        onCopySuccess()
                    },
                )
                SourceAddressRow(
                    label = "IPv6",
                    address = uiState.ipv6,
                    visible = sourceAddressesVisible,
                    onCopy = { value ->
                        scope.launch {
                            clipboard.setPlainText(value)
                        }
                        onCopySuccess()
                    },
                )
            }
        }

        if (uiState.clashModes.isNotEmpty()) ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.clash_mode),
                    style = MaterialTheme.typography.titleMedium,
                )
                uiState.clashModes.forEach { mode ->
                    val selected = mode == uiState.selectedClashMode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Button(
                            onClick = { selectClashMode(mode) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !selected,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            ),
                            border = if (!selected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            } else {
                                null
                            },
                        ) {
                            Text(mode)
                        }
                    }
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HideableSectionTitle(
                    title = stringResource(Res.string.network_interfaces),
                    visible = networkInterfacesVisible,
                    onToggleVisible = { networkInterfacesVisible = !networkInterfacesVisible },
                )
                SelectionContainer {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        uiState.networkInterfaces.forEach { interfaceInfo ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = interfaceInfo.name,
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                )
                                for (address in interfaceInfo.addresses) {
                                    Text(
                                        text = if (networkInterfacesVisible) {
                                            address
                                        } else {
                                            maskNetworkAddress(address)
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        }

        BoxedVerticalScrollbar(
            modifier = Modifier.fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = scrollState),
            style = defaultMaterialScrollbarStyle().copy(
                thickness = 12.dp,
            ),
        )
    }
}

@Composable
private fun SourceAddressRow(
    label: String,
    address: String?,
    visible: Boolean,
    onCopy: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
        )
        val text = when {
            address == null -> stringResource(Res.string.no_statistics)
            visible -> address
            else -> maskNetworkAddress(address)
        }
        Text(
            text = text,
            modifier = Modifier.clickable(enabled = address != null) {
                onCopy(address ?: return@clickable)
            },
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmallEmphasized,
        )
    }
}

@Composable
private fun HideableSectionTitle(
    title: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        SimpleIconButton(
            imageVector = vectorResource(
                if (visible) {
                    Res.drawable.visibility
                } else {
                    Res.drawable.visibility_off
                },
            ),
            contentDescription = stringResource(
                if (visible) {
                    Res.string.hide
                } else {
                    Res.string.show
                },
            ),
            onClick = onToggleVisible,
        )
    }
}

private fun maskNetworkAddress(address: String): String {
    return if (':' in address) {
        "*::*"
    } else {
        "*.*.*.*"
    }
}
