@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import fr.husi.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import fr.husi.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.utils.PackageCache
import fr.husi.resources.*

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal actual fun AppManagerScreen(
    onBackPress: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val viewModel: AppManagerViewModel = viewModel {
        AppManagerViewModel(context.packageManager, context.packageName)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                AppManagerUiEvent.Finish -> onBackPress()
            }
        }
    }

    AppListScaffold(
        viewModel = viewModel,
        title = { Text(stringResource(Res.string.proxied_apps)) },
        isLoading = uiState.isLoading,
        apps = uiState.apps,
        filteredApps = uiState.filteredApps,
        snackbarMessage = uiState.snackbarMessage,
        onNavigationClick = onBackPress,
        modifier = modifier,
        extraTopBarContent = {
            ProxyModeSelector(
                selectedMode = uiState.mode,
                onSelect = { viewModel.setProxyMode(it) },
            )
        },
        dropdownMenuItems = { onDismiss ->
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_scan_china_apps)) },
                onClick = {
                    viewModel.scanChinaApps()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.document_scanner),
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.invert_selections)) },
                onClick = {
                    viewModel.invertSections()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.compare_arrows),
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.clear_selections)) },
                onClick = {
                    viewModel.clearSections()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.cleaning_services),
                        contentDescription = null,
                    )
                },
            )
        },
    )

    val scanned = uiState.scanned
    if (scanned != null) {
        ScanDialog(
            scanned = scanned,
            progress = uiState.scanProcess,
            onCancel = { viewModel.cancelScan() },
        )
    }
}

@Composable
private fun ProxyModeSelector(
    selectedMode: ProxyMode,
    onSelect: (ProxyMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth(),
    ) {
        val isDisabled = selectedMode == ProxyMode.DISABLED
        SegmentedButton(
            selected = isDisabled,
            onClick = { onSelect(ProxyMode.DISABLED) },
            shape = SegmentedButtonDefaults.itemShape(0, 3),
            icon = {
                SegmentedButtonDefaults.Icon(
                    active = isDisabled,
                    activeContent = {
                        Icon(vectorResource(Res.drawable.question_mark), null)
                    },
                    inactiveContent = {
                        Icon(vectorResource(Res.drawable.close), null)
                    },
                )
            },
            label = { Text(stringResource(Res.string.off)) },
        )
        val isProxy = selectedMode == ProxyMode.PROXY
        SegmentedButton(
            selected = isProxy,
            onClick = { onSelect(ProxyMode.PROXY) },
            shape = SegmentedButtonDefaults.itemShape(1, 3),
            icon = {
                SegmentedButtonDefaults.Icon(
                    active = isProxy,
                    inactiveContent = {
                        Icon(vectorResource(Res.drawable.flight_takeoff), null)
                    },
                )
            },
            label = { Text(stringResource(Res.string.route_proxy)) },
        )
        val isBypass = selectedMode == ProxyMode.BYPASS
        SegmentedButton(
            selected = isBypass,
            onClick = { onSelect(ProxyMode.BYPASS) },
            shape = SegmentedButtonDefaults.itemShape(2, 3),
            icon = {
                SegmentedButtonDefaults.Icon(
                    active = isBypass,
                    inactiveContent = {
                        Icon(vectorResource(Res.drawable.shuffle), null)
                    },
                )
            },
            label = { Text(stringResource(Res.string.bypass_apps)) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanDialog(
    scanned: List<String>,
    progress: Float?,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val listState = rememberLazyListState()
    var previousCount by remember { mutableIntStateOf(scanned.size) }

    LaunchedEffect(scanned.size) {
        if (scanned.isNotEmpty() && scanned.size >= previousCount) {
            listState.scrollToItem(scanned.size - 1)
        }
        previousCount = scanned.size
    }

    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.cancel))
            }
        },
        title = { Text(stringResource(Res.string.action_scan_china_apps)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val indicatorModifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                if (progress == null) {
                    LinearProgressIndicator(
                        modifier = indicatorModifier,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = indicatorModifier,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    state = listState,
                ) {
                    items(
                        items = scanned,
                        key = { it },
                        contentType = { 0 },
                    ) { item ->
                        val label = PackageCache.loadLabel(packageManager, item)
                        Text(
                            text = "$label ($item)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        },
    )
}

@Preview
@Composable
private fun PreviewAppManagerScreen() {
    PreviewContainer {
        AppManagerScreen(
            onBackPress = {},
        )
    }
}
