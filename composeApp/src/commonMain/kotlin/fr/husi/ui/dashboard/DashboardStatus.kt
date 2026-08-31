package fr.husi.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    isEditing: Boolean,
    selectClashMode: (mode: String) -> Unit,
    showError: (String) -> Unit,
    onCopySuccess: () -> Unit,
    onWidgetsChange: (List<DashboardWidgetEntry>) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var sourceAddressesVisible by remember { mutableStateOf(false) }
    var networkInterfacesVisible by remember { mutableStateOf(false) }

    val openConnectEndpoints by openConnectController.endpoints.collectAsStateWithLifecycle()
    val openVPNEndpoints by openVPNController.endpoints.collectAsStateWithLifecycle()

    val entries = uiState.dashboardWidgets
    val hasContent = { widget: DashboardWidget ->
        when (widget) {
            DashboardWidget.OpenConnect -> openConnectEndpoints.isNotEmpty()
            DashboardWidget.OpenVPN -> openVPNEndpoints.isNotEmpty()
            DashboardWidget.ClashMode -> uiState.clashModes.isNotEmpty()
            else -> true
        }
    }
    val visibleWidgets = entries.visibleWidgets().let { widgets ->
        if (isEditing) widgets else widgets.filter(hasContent)
    }
    val hiddenWidgets = entries.hiddenWidgets()

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
            DashboardWidgetFlexBox(
                widgets = visibleWidgets,
                isReorderable = isEditing,
                onOrderChange = { order -> onWidgetsChange(entries.reorderVisibleWidgets(order)) },
                modifier = Modifier.fillMaxWidth(),
                overlay = { widget ->
                    if (isEditing) DashboardWidgetBadge(
                        icon = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.dashboard_hide_widget),
                        onClick = { onWidgetsChange(entries.setWidgetVisible(widget, false)) },
                    )
                },
            ) { widget ->
                if (hasContent(widget)) {
                    DashboardWidgetContent(
                        widget = widget,
                        uiState = uiState,
                        openConnectController = openConnectController,
                        openVPNController = openVPNController,
                        sourceAddressesVisible = sourceAddressesVisible,
                        onToggleSourceAddressesVisible = {
                            sourceAddressesVisible = !sourceAddressesVisible
                        },
                        networkInterfacesVisible = networkInterfacesVisible,
                        onToggleNetworkInterfacesVisible = {
                            networkInterfacesVisible = !networkInterfacesVisible
                        },
                        selectClashMode = selectClashMode,
                        showError = showError,
                        onCopy = { value ->
                            scope.launch { clipboard.setPlainText(value) }
                            onCopySuccess()
                        },
                    )
                } else {
                    DashboardWidgetPlaceholderCard(widget)
                }
            }

            if (isEditing && hiddenWidgets.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.dashboard_hidden_widgets),
                    style = MaterialTheme.typography.titleMedium,
                )
                DashboardWidgetFlexBox(
                    widgets = hiddenWidgets,
                    isReorderable = false,
                    onOrderChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    overlay = { widget ->
                        DashboardWidgetBadge(
                            icon = vectorResource(Res.drawable.add),
                            contentDescription = stringResource(Res.string.dashboard_show_widget),
                            onClick = { onWidgetsChange(entries.setWidgetVisible(widget, true)) },
                        )
                    },
                ) { widget ->
                    DashboardWidgetPlaceholderCard(widget)
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
private fun DashboardWidgetContent(
    widget: DashboardWidget,
    uiState: DashboardState,
    openConnectController: OpenConnectAuthController,
    openVPNController: OpenVPNAuthController,
    sourceAddressesVisible: Boolean,
    onToggleSourceAddressesVisible: () -> Unit,
    networkInterfacesVisible: Boolean,
    onToggleNetworkInterfacesVisible: () -> Unit,
    selectClashMode: (mode: String) -> Unit,
    showError: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    when (widget) {
        DashboardWidget.ProxySpeed -> ProxySpeedCard(
            uiState = uiState,
            modifier = Modifier.fillMaxSize(),
        )

        DashboardWidget.DirectSpeed -> DirectSpeedCard(
            uiState = uiState,
            modifier = Modifier.fillMaxSize(),
        )

        DashboardWidget.Status -> StatusCard(uiState = uiState)

        DashboardWidget.OpenConnect -> OpenConnectStatusSection(
            controller = openConnectController,
            showError = showError,
        )

        DashboardWidget.OpenVPN -> OpenVPNStatusSection(
            controller = openVPNController,
            showError = showError,
        )

        DashboardWidget.SourceAddress -> SourceAddressCard(
            uiState = uiState,
            visible = sourceAddressesVisible,
            onToggleVisible = onToggleSourceAddressesVisible,
            onCopy = onCopy,
        )

        DashboardWidget.ClashMode -> ClashModeCard(
            uiState = uiState,
            selectClashMode = selectClashMode,
        )

        DashboardWidget.NetworkInterfaces -> NetworkInterfacesCard(
            uiState = uiState,
            visible = networkInterfacesVisible,
            onToggleVisible = onToggleNetworkInterfacesVisible,
        )
    }
}

private val WIDGET_BADGE_OVERHANG = 8.dp

@Composable
private fun BoxScope.DashboardWidgetBadge(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = WIDGET_BADGE_OVERHANG, y = -WIDGET_BADGE_OVERHANG)
                .size(24.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun DashboardWidgetPlaceholderCard(widget: DashboardWidget) {
    ElevatedCard(modifier = Modifier.fillMaxSize()) {
        Text(
            text = dashboardWidgetTitle(widget),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun dashboardWidgetTitle(widget: DashboardWidget): String = stringResource(
    when (widget) {
        DashboardWidget.ProxySpeed -> Res.string.status_proxy
        DashboardWidget.DirectSpeed -> Res.string.status_direct
        DashboardWidget.Status -> Res.string.status_status
        DashboardWidget.OpenConnect -> Res.string.action_openconnect
        DashboardWidget.OpenVPN -> Res.string.action_openvpn
        DashboardWidget.SourceAddress -> Res.string.source_address
        DashboardWidget.ClashMode -> Res.string.clash_mode
        DashboardWidget.NetworkInterfaces -> Res.string.network_interfaces
    },
)

@Composable
private fun StatusCard(uiState: DashboardState) {
    ElevatedCard(
        modifier = Modifier.fillMaxSize(),
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
}

@Composable
private fun SourceAddressCard(
    uiState: DashboardState,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onCopy: (String) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HideableSectionTitle(
                title = stringResource(Res.string.source_address),
                visible = visible,
                onToggleVisible = onToggleVisible,
            )
            SourceAddressRow(
                label = "IPv4",
                address = uiState.ipv4,
                visible = visible,
                onCopy = onCopy,
            )
            SourceAddressRow(
                label = "IPv6",
                address = uiState.ipv6,
                visible = visible,
                onCopy = onCopy,
            )
        }
    }
}

@Composable
private fun ClashModeCard(
    uiState: DashboardState,
    selectClashMode: (mode: String) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxSize(),
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
}

@Composable
private fun NetworkInterfacesCard(
    uiState: DashboardState,
    visible: Boolean,
    onToggleVisible: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HideableSectionTitle(
                title = stringResource(Res.string.network_interfaces),
                visible = visible,
                onToggleVisible = onToggleVisible,
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
                                    text = if (visible) {
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
