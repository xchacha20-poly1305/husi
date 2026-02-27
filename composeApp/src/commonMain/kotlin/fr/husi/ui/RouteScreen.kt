@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ernestoyaquello.dragdropswipelazycolumn.DragDropSwipeLazyColumn
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItem
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItemScope
import com.ernestoyaquello.dragdropswipelazycolumn.config.DraggableSwipeableItemColors
import com.ernestoyaquello.dragdropswipelazycolumn.state.rememberDragDropSwipeLazyColumnState
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import androidx.compose.foundation.layout.fillMaxHeight
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.PlatformMenuIcon
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import fr.husi.compose.SagerFab
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.StatsBar
import fr.husi.compose.TextButton
import fr.husi.compose.navigationBarsAlwaysInsets
import fr.husi.compose.paddingExceptBottom
import fr.husi.compose.rememberScrollHideState
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.RuleEntity
import fr.husi.database.RuleEntity.Companion.OUTBOUND_BLOCK
import fr.husi.database.RuleEntity.Companion.OUTBOUND_DIRECT
import fr.husi.database.RuleEntity.Companion.OUTBOUND_PROXY
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.showAndDismissOld
import fr.husi.repository.repo
import fr.husi.resources.Res
import fr.husi.resources.add_road
import fr.husi.resources.apply
import fr.husi.resources.apps_message
import fr.husi.resources.cag_dns
import fr.husi.resources.cancel
import fr.husi.resources.clear_profiles_message
import fr.husi.resources.confirm
import fr.husi.resources.delete
import fr.husi.resources.dns_only
import fr.husi.resources.drag_indicator
import fr.husi.resources.edit
import fr.husi.resources.error_title
import fr.husi.resources.layers
import fr.husi.resources.menu
import fr.husi.resources.menu_route
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.need_reload
import fr.husi.resources.ok
import fr.husi.resources.process
import fr.husi.resources.removed
import fr.husi.resources.replay
import fr.husi.resources.route_add
import fr.husi.resources.route_block
import fr.husi.resources.route_bypass
import fr.husi.resources.route_manage_assets
import fr.husi.resources.route_proxy
import fr.husi.resources.route_reset
import fr.husi.resources.route_warn
import fr.husi.resources.undo
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource


@Composable
fun RouteScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    viewModel: RouteScreenViewModel = viewModel { RouteScreenViewModel() },
    onDrawerClick: () -> Unit,
    openRouteSettings: (Long) -> Unit,
    openAssets: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.commit()
        }
    }

    val dragDropListState = rememberDragDropSwipeLazyColumnState()
    val scrollHideVisible by rememberScrollHideState(dragDropListState.lazyListState)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMoreAction by remember { mutableStateOf(false) }
    var showResetAlert by remember { mutableStateOf(false) }

    fun needReload() = scope.launch {
        if (!DataStore.serviceState.started) return@launch
        val result = snackbarState.showSnackbar(
            message = repo.getString(Res.string.need_reload),
            actionLabel = repo.getString(Res.string.apply),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            repo.reloadService()
        }
    }

    LaunchedEffect(uiState.pendingDeleteCount) {
        if (uiState.pendingDeleteCount > 0) {
            val result = snackbarState.showAndDismissOld(
                message = repo.getPluralString(
                    Res.plurals.removed,
                    uiState.pendingDeleteCount,
                    uiState.pendingDeleteCount,
                ),
                actionLabel = repo.getString(Res.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undo()
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing

    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.menu_route)) },
                navigationIcon = {
                    PlatformMenuIcon(
                        imageVector = vectorResource(Res.drawable.menu),
                        contentDescription = stringResource(Res.string.menu),
                        onClick = onDrawerClick,
                    )
                },
                actions = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.add_road),
                        contentDescription = stringResource(Res.string.route_add),
                        onClick = {
                            openRouteSettings(-1L)
                        },
                    )
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.replay),
                        contentDescription = stringResource(Res.string.route_reset),
                        onClick = { showResetAlert = true },
                    )
                    Box {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.more_vert),
                            contentDescription = stringResource(Res.string.more),
                            onClick = { showMoreAction = true },
                        )
                        DropdownMenu(
                            expanded = showMoreAction,
                            onDismissRequest = { showMoreAction = false },
                            shape = MenuDefaults.standaloneGroupShape,
                            containerColor = MenuDefaults.groupStandardContainerColor,
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.route_manage_assets)) },
                                onClick = {
                                    showMoreAction = false
                                    openAssets()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = vectorResource(Res.drawable.layers),
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            SagerFab(
                visible = scrollHideVisible,
                state = serviceStatus.state,
                showSnackbar = { message ->
                    scope.launch {
                        snackbarState.showSnackbar(
                            message = getStringOrRes(message),
                            actionLabel = repo.getString(Res.string.ok),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (serviceStatus.state == ServiceState.Connected) {
                StatsBar(
                    status = serviceStatus,
                    visible = scrollHideVisible,
                    mainViewModel = mainViewModel,
                )
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .paddingExceptBottom(innerPadding),
            ) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    elevation = CardDefaults.elevatedCardElevation(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val uriHandler = LocalUriHandler.current
                        Text(
                            text = stringResource(Res.string.route_warn),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    uriHandler.openUri("https://github.com/xchacha20-poly1305/husi/wiki/Route")
                                },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                DragDropSwipeLazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = dragDropListState,
                    items = uiState.rules.toImmutableList(),
                    key = { it.id },
                    contentType = { 0 },
                    contentPadding = PaddingValues(
                        bottom = navigationBarsAlwaysInsets()
                            .asPaddingValues()
                            .calculateBottomPadding(),
                    ),
                    userScrollEnabled = true,
                    onIndicesChangedViaDragAndDrop = {
                        viewModel.submitReorder(it)
                        needReload()
                    },
                ) { _, rule ->
                    val swipeState = rememberSwipeToDismissBoxState()

                    // Monitor swipe state changes and perform deletion when user completes swipe gesture.
                    // After deletion, immediately reset state to Settled to prevent re-triggering
                    // when item is restored via undo. Without this, the preserved swipeState
                    // (due to stable key) would cause onDismiss to fire again on recomposition.
                    LaunchedEffect(swipeState.currentValue) {
                        if (swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
                            viewModel.undoableRemove(rule.id)
                            swipeState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }

                    DraggableSwipeableItem(
                        modifier = Modifier.animateDraggableSwipeableItem(),
                        colors = DraggableSwipeableItemColors.createRemembered(
                            containerBackgroundColor = Color.Transparent,
                            containerBackgroundColorWhileDragged = Color.Transparent,
                        ),
                    ) {
                        SwipeToDismissBox(
                            state = swipeState,
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(vectorResource(Res.drawable.delete), null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RuleCard(
                                rule = rule,
                                viewModel = viewModel,
                                onNeedReload = { needReload() },
                                openRouteSettings = openRouteSettings,
                            )
                        }
                    }
                }

                // Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }

            BoxedVerticalScrollbar(
                modifier = Modifier.fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = dragDropListState.lazyListState),
                style = defaultMaterialScrollbarStyle().copy(
                    thickness = 12.dp,
                ),
            )
        }
    }

    if (showResetAlert) AlertDialog(
        onDismissRequest = { showResetAlert = false },
        title = { Text(stringResource(Res.string.confirm)) },
        text = { Text(stringResource(Res.string.clear_profiles_message)) },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                showResetAlert = false
                viewModel.reset()
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.cancel)) {
                showResetAlert = false
            }
        },
    )

    LaunchedEffect(Unit) {
        mainViewModel.uiEvent.collect { event ->
            when (event) {
                is MainViewModelUiEvent.Snackbar -> scope.launch {
                    snackbarState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = repo.getString(Res.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                is MainViewModelUiEvent.SnackbarWithAction -> scope.launch {
                    val result = snackbarState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = getStringOrRes(event.actionLabel),
                        duration = SnackbarDuration.Short,
                    )
                    event.callback(result)
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun DraggableSwipeableItemScope<RuleEntity>.RuleCard(
    modifier: Modifier = Modifier,
    rule: RuleEntity,
    viewModel: RouteScreenViewModel,
    onNeedReload: () -> Unit,
    openRouteSettings: (Long) -> Unit,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.drag_indicator),
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(40.dp)
                    .padding(8.dp)
                    .dragDropModifier(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 0.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = rule.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(
                        onClick = {
                            openRouteSettings(rule.id)
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.edit),
                            contentDescription = stringResource(Res.string.edit),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                ) {
                    Text(
                        text = rule.summary(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when (rule.action) {
                            "", SingBoxOptions.ACTION_ROUTE -> rule.displayOutbound()
                            else -> "action: ${rule.action}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )

                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = {
                            viewModel.toggleEnabled(rule)
                            onNeedReload()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleEntity.summary(): String {
    if (dnsOnly) return stringResource(Res.string.dns_only)

    var summary = ""
    if (domains.isNotBlank()) summary += "$domains\n"
    if (ip.isNotBlank()) summary += "$ip\n"
    if (source.isNotBlank()) summary += "source: $source\n"
    if (sourcePort.isNotBlank()) summary += "sourcePort: $sourcePort\n"
    if (port.isNotBlank()) summary += "port: $port\n"
    if (network.isNotEmpty()) summary += "network: $network\n"
    if (protocol.isNotEmpty()) summary += "protocol: $protocol\n"
    if (clientType.isNotEmpty()) summary += "client: $clientType\n"
    if (packages.isNotEmpty()) {
        summary += if (repo.isAndroid) {
            stringResource(Res.string.apps_message, packages.size,)
        } else {
            "${stringResource(Res.string.process)}: ${packages.joinToString(", ")}"
        } + "\n"
    }
    if (ssid.isNotBlank()) summary += "ssid: $ssid\n"
    if (bssid.isNotBlank()) summary += "bssid: $bssid\n"
    if (clashMode.isNotBlank()) summary += "clashMode: $clashMode\n"
    if (networkType.isNotEmpty()) summary += "networkType: $networkType\n"
    if (networkIsExpensive) summary += "networkIsExpensive\n"
    if (networkInterfaceAddress.isNotEmpty()) summary += "networkInterfaceAddress: $networkInterfaceAddress\n"

    if (overrideAddress.isNotBlank()) summary += "overrideAddress: $overrideAddress\n"
    if (overridePort > 0) summary += "overridePort: $overridePort\n"
    if (tlsFragment) {
        summary += "TLS fragment\n"
        if (tlsFragmentFallbackDelay.isNotBlank()) {
            summary += "tlsFragmentFallbackDelay: $tlsFragmentFallbackDelay\n"
        }
    }
    if (tlsRecordFragment) {
        summary += "TLS record fragment\n"
    }

    if (resolveStrategy.isNotBlank()) summary += "resolveStrategy: $resolveStrategy\n"
    if (resolveDisableCache) summary += "resolveDisableCache\n"
    if (resolveRewriteTTL >= 0) summary += "resolveRewriteTTL: $resolveRewriteTTL\n"
    if (resolveClientSubnet.isNotBlank()) summary += "resolveClientSubnet: $resolveClientSubnet\n"

    if (sniffTimeout.isNotBlank()) summary += "sniffTimeout: $sniffTimeout\n"
    if (sniffers.isNotEmpty()) summary += "sniffers: $sniffers\n"

    if (customConfig.isNotBlank()) summary += stringResource(Res.string.menu_route) + "\n"
    if (customDnsConfig.isNotBlank()) summary += stringResource(Res.string.cag_dns) + "\n"

    // Even has "\n" suffix, TextView's "..." will be added and remove the last "\n".
    val lines = summary.trim().split("\n")
    return if (lines.size > 5) {
        lines.subList(0, 5).joinToString("\n", postfix = "\n...")
    } else {
        summary.trim()
    }
}

@Composable
private fun RuleEntity.displayOutbound(): String {
    return when (outbound) {
        OUTBOUND_PROXY -> stringResource(Res.string.route_proxy)
        OUTBOUND_DIRECT -> stringResource(Res.string.route_bypass)
        OUTBOUND_BLOCK -> stringResource(Res.string.route_block)
        else -> ProfileManager.getProfile(outbound)?.displayName()
            ?: stringResource(Res.string.error_title)
    }
}
