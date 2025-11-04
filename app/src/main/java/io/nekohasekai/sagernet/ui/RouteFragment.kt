package io.nekohasekai.sagernet.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.GravityCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ernestoyaquello.dragdropswipelazycolumn.DragDropSwipeLazyColumn
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItem
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItemScope
import com.ernestoyaquello.dragdropswipelazycolumn.config.DraggableSwipeableItemColors
import com.ernestoyaquello.dragdropswipelazycolumn.state.rememberDragDropSwipeLazyColumnState
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.ComposeSnackbarAdapter
import io.nekohasekai.sagernet.compose.HideOnBottomScrollBehavior
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.paddingExceptBottom
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.RuleEntity.Companion.OUTBOUND_BLOCK
import io.nekohasekai.sagernet.database.RuleEntity.Companion.OUTBOUND_DIRECT
import io.nekohasekai.sagernet.database.RuleEntity.Companion.OUTBOUND_PROXY
import io.nekohasekai.sagernet.databinding.ComposeHolderBinding
import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class RouteFragment : OnKeyDownFragment(R.layout.compose_holder) {

    private val viewModel by viewModels<RouteFragmentViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as MainActivity
        val binding = ComposeHolderBinding.bind(view)
        binding.root.setContent {
            AppTheme {
                RouteScreen(
                    viewModel = viewModel,
                    onBackPress = {
                        activity.binding.drawerLayout.openDrawer(GravityCompat.START)
                    },
                    fab = activity.binding.fab,
                    bottomBar = activity.binding.stats,
                )
            }
        }
    }

}

@Composable
private fun RouteScreen(
    modifier: Modifier = Modifier,
    viewModel: RouteFragmentViewModel,
    onBackPress: () -> Unit,
    fab: FloatingActionButton,
    bottomBar: BottomAppBar,
) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val undoManager = remember {
        UndoSnackbarManager(
            snackbar = ComposeSnackbarAdapter(
                showSnackbar = { message, actionLabel ->
                    snackbarState.showSnackbar(
                        message = context.getStringOrRes(message),
                        actionLabel = context.getStringOrRes(actionLabel),
                        duration = SnackbarDuration.Short,
                    )
                },
                scope = scope,
            ),
            callback = viewModel,
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            undoManager.flush()
        }
    }

    val dragDropListState = rememberDragDropSwipeLazyColumnState()
    HideOnBottomScrollBehavior(dragDropListState.lazyListState, fab, bottomBar)

    val density = LocalDensity.current
    var bottomBarHeightDp by remember { mutableStateOf(0.dp) }

    DisposableEffect(bottomBar) {
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            bottomBarHeightDp = with(density) { bottomBar.height.toDp() }
        }
        bottomBar.viewTreeObserver.addOnGlobalLayoutListener(listener)
        bottomBarHeightDp = with(density) { bottomBar.height.toDp() }
        onDispose {
            bottomBar.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var menuExpanded by remember { mutableStateOf(false) }
    var showResetAlert by remember { mutableStateOf(false) }

    fun needReload() = scope.launch {
        if (!DataStore.serviceState.started) return@launch
        val result = snackbarState.showSnackbar(
            message = context.getString(R.string.need_reload),
            actionLabel = context.getString(R.string.apply),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            repo.reloadService()
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_route)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.menu),
                        contentDescription = stringResource(R.string.menu),
                        onClick = onBackPress,
                    )
                },
                actions = {
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.add_road),
                        contentDescription = stringResource(R.string.route_add),
                    ) {
                        context.startActivity(
                            Intent(context, RouteSettingsActivity::class.java)
                        )
                    }
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.more_vert),
                        contentDescription = stringResource(R.string.more),
                        onClick = { menuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.route_reset)) },
                            onClick = {
                                menuExpanded = false
                                showResetAlert = true
                            },
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.route_manage_assets)) },
                            onClick = {
                                menuExpanded = false
                                context.startActivity(
                                    Intent(context, AssetsActivity::class.java)
                                )
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarState,
                modifier = Modifier.padding(bottom = bottomBarHeightDp),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                            text = stringResource(R.string.route_warn),
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
                    onIndicesChangedViaDragAndDrop = {
                        viewModel.submitReorder(it)
                        needReload()
                    },
                ) { i, rule ->
                    val swipeState = rememberSwipeToDismissBoxState()

                    // Monitor swipe state changes and perform deletion when user completes swipe gesture.
                    // After deletion, immediately reset state to Settled to prevent re-triggering
                    // when item is restored via undo. Without this, the preserved swipeState
                    // (due to stable key) would cause onDismiss to fire again on recomposition.
                    LaunchedEffect(swipeState.currentValue) {
                        if (swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
                            viewModel.undoableRemove(i)
                            undoManager.remove(i to rule)
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
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(ImageVector.vectorResource(R.drawable.delete), null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RuleCard(
                                rule = rule,
                                viewModel = viewModel,
                                onNeedReload = { needReload() },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }

            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = dragDropListState.lazyListState),
                style = defaultMaterialScrollbarStyle().copy(
                    thickness = 12.dp,
                ),
            )
        }
    }

    if (showResetAlert) AlertDialog(
        onDismissRequest = { showResetAlert = false },
        title = { Text(stringResource(R.string.confirm)) },
        text = { Text(stringResource(R.string.clear_profiles_message)) },
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
                showResetAlert = false
                viewModel.reset()
            }
        },
        dismissButton = {
            TextButton(stringResource(android.R.string.cancel)) {
                showResetAlert = false
            }
        },
    )
}

@Composable
private fun DraggableSwipeableItemScope<RuleEntity>.RuleCard(
    modifier: Modifier = Modifier,
    rule: RuleEntity,
    viewModel: RouteFragmentViewModel,
    onNeedReload: () -> Unit,
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
                imageVector = ImageVector.vectorResource(id = R.drawable.drag_indicator),
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
                    .padding(0.dp)
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

                    val context = LocalContext.current
                    IconButton(
                        onClick = {
                            context.startActivity(
                                Intent(context, RouteSettingsActivity::class.java)
                                    .putExtra(
                                        RouteSettingsActivity.EXTRA_ROUTE_ID,
                                        rule.id,
                                    ),
                            )
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.edit),
                            contentDescription = stringResource(id = R.string.edit),
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
                        text = rule.summary(LocalContext.current),
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
                    val context = LocalContext.current
                    Text(
                        text = when (rule.action) {
                            "", SingBoxOptions.ACTION_ROUTE -> rule.displayOutbound(context)
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

private fun RuleEntity.summary(context: Context): String {
    var summary = ""
    if (domains.isNotBlank()) summary += "$domains\n"
    if (ip.isNotBlank()) summary += "$ip\n"
    if (source.isNotBlank()) summary += "source: $source\n"
    if (sourcePort.isNotBlank()) summary += "sourcePort: $sourcePort\n"
    if (port.isNotBlank()) summary += "port: $port\n"
    if (network.isNotEmpty()) summary += "network: $network\n"
    if (protocol.isNotEmpty()) summary += "protocol: $protocol\n"
    if (clientType.isNotEmpty()) summary += "client: $clientType\n"
    if (packages.isNotEmpty()) summary += context.getString(
        R.string.apps_message, packages.size
    ) + "\n"
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

    if (customConfig.isNotBlank()) summary += context.getString(R.string.menu_route) + "\n"
    if (customDnsConfig.isNotBlank()) summary += context.getString(R.string.cag_dns) + "\n"

    // Even has "\n" suffix, TextView's "..." will be added and remove the last "\n".
    val lines = summary.trim().split("\n")
    return if (lines.size > 5) {
        lines.subList(0, 5).joinToString("\n", postfix = "\n...")
    } else {
        summary.trim()
    }
}

private fun RuleEntity.displayOutbound(context: Context): String {
    return when (outbound) {
        OUTBOUND_PROXY -> context.getString(R.string.route_proxy)
        OUTBOUND_DIRECT -> context.getString(R.string.route_bypass)
        OUTBOUND_BLOCK -> context.getString(R.string.route_block)
        else -> ProfileManager.getProfile(outbound)?.displayName()
            ?: context.getString(R.string.error_title)
    }
}
