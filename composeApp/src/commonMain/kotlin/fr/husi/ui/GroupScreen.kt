package fr.husi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalClipboard
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ernestoyaquello.dragdropswipelazycolumn.DragDropSwipeLazyColumn
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItem
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItemScope
import com.ernestoyaquello.dragdropswipelazycolumn.config.DraggableSwipeableItemColors
import com.ernestoyaquello.dragdropswipelazycolumn.state.rememberDragDropSwipeLazyColumnState
import fr.husi.GroupType
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import androidx.compose.foundation.layout.fillMaxHeight
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.PlatformMenuIcon
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import fr.husi.compose.QRCodeDialog
import fr.husi.compose.SagerFab
import fr.husi.compose.SheetActionRow
import fr.husi.compose.SheetSectionTitle
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.StatsBar
import fr.husi.compose.TextButton
import fr.husi.compose.rememberScrollHideState
import fr.husi.compose.setPlainText
import fr.husi.compose.withNavigation
import fr.husi.database.SagerDatabase
import fr.husi.fmt.toUniversalLink
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.formatTime
import fr.husi.ktx.onIoDispatcher
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.write
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import fr.husi.resources.*
import fr.husi.repository.repo
import fr.husi.ktx.showAndDismissOld
import fr.husi.libcore.Libcore

@Composable
fun GroupScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    viewModel: GroupScreenViewModel = viewModel { GroupScreenViewModel() },
    onDrawerClick: () -> Unit,
    openGroupSettings: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.commit()
        }
    }

    var showUpdateAll by remember { mutableStateOf(false) }
    var qrDialogData by remember { mutableStateOf<Pair<String, String>?>(null) } // url:name
    var clearGroupConfirm by remember { mutableStateOf<Long?>(null) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.hiddenGroups) {
        if (uiState.hiddenGroups > 0) {
            val result = snackbarState.showAndDismissOld(
                message = repo.getPluralString(
                    Res.plurals.removed,
                    uiState.hiddenGroups,
                    uiState.hiddenGroups,
                ),
                actionLabel = repo.getString(Res.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undo()
            }
        }
    }

    var groupToExport by remember { mutableStateOf<Long?>(null) }
    val exportProfiles = rememberFileSaverLauncher { file ->
        if (file != null && groupToExport != null) {
            viewModel.exportToFile(
                group = groupToExport!!,
                writeContent = { content ->
                    file.write(content.encodeToByteArray())
                },
                showSnackbar = { str ->
                    scope.launch {
                        snackbarState.showSnackbar(
                            message = getStringOrRes(str),
                            actionLabel = repo.getString(Res.string.ok),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            )
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing
    val dragDropListState = rememberDragDropSwipeLazyColumnState()
    val scrollHideVisible by rememberScrollHideState(dragDropListState.lazyListState)

    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.menu_group)) },
                navigationIcon = {
                    PlatformMenuIcon(
                        imageVector = vectorResource(Res.drawable.menu),
                        contentDescription = stringResource(Res.string.menu),
                        onClick = onDrawerClick,
                    )
                },
                actions = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.update),
                        contentDescription = stringResource(Res.string.update_all_subscription),
                        onClick = { showUpdateAll = true },
                    )
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.playlist_add),
                        contentDescription = stringResource(Res.string.group_create),
                        onClick = {
                            openGroupSettings(0L)
                        },
                    )
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
            DragDropSwipeLazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                state = dragDropListState,
                items = uiState.groups.toImmutableList(),
                key = { it.group.id },
                contentType = { 0 },
                userScrollEnabled = true,
                contentPadding = innerPadding.withNavigation(),
                onIndicesChangedViaDragAndDrop = { viewModel.submitReorder(it) },
            ) { _, groupState ->
                val swipeState = rememberSwipeToDismissBoxState()

                LaunchedEffect(swipeState.currentValue) {
                    if (swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
                        viewModel.undoableRemove(groupState.group.id)
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
                        enableDismissFromStartToEnd = !groupState.group.ungrouped && !groupState.isUpdating,
                        enableDismissFromEndToStart = !groupState.group.ungrouped && !groupState.isUpdating,
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
                        GroupCard(
                            state = groupState,
                            viewModel = viewModel,
                            openGroupSettings = openGroupSettings,
                            snackbar = { message ->
                                scope.launch {
                                    snackbarState.showSnackbar(
                                        message = message,
                                        actionLabel = repo.getString(Res.string.ok),
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                            showQRDialog = { url, name ->
                                qrDialogData = url to name
                            },
                            showClearGroupDialog = {
                                clearGroupConfirm = groupState.group.id
                            },
                            exportToFile = {
                                groupToExport = groupState.group.id
                                exportProfiles.launch("profiles_${groupState.group.displayName()}", "txt")
                            },
                        )
                    }
                }
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

    if (showUpdateAll) AlertDialog(
        onDismissRequest = { showUpdateAll = false },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                viewModel.doUpdateAll()
                showUpdateAll = false
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.cancel)) {
                showUpdateAll = false
            }
        },
        icon = { Icon(vectorResource(Res.drawable.update), null) },
        title = { Text(stringResource(Res.string.confirm)) },
        text = { Text(stringResource(Res.string.update_all_subscription)) },
    )

    qrDialogData?.let { (url, name) ->
        QRCodeDialog(
            url = url,
            name = name,
            onDismiss = { qrDialogData = null },
            showSnackbar = { message ->
                scope.launch {
                    snackbarState.showSnackbar(
                        message = message,
                        actionLabel = repo.getString(Res.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
        )
    }

    clearGroupConfirm?.let { id ->
        AlertDialog(
            onDismissRequest = { clearGroupConfirm = null },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    viewModel.clearGroup(id)
                    clearGroupConfirm = null
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    clearGroupConfirm = null
                }
            },
            icon = { Icon(vectorResource(Res.drawable.mop), null) },
            title = { Text(stringResource(Res.string.confirm)) },
            text = { Text(stringResource(Res.string.clear_profiles_message)) },
        )
    }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraggableSwipeableItemScope<GroupItemUiState>.GroupCard(
    modifier: Modifier = Modifier,
    state: GroupItemUiState,
    viewModel: GroupScreenViewModel,
    openGroupSettings: (Long) -> Unit,
    snackbar: suspend (message: String) -> Unit,
    showQRDialog: (url: String, name: String) -> Unit,
    showClearGroupDialog: () -> Unit,
    exportToFile: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val group = state.group

    var showOptionsSheet by remember { mutableStateOf(false) }
    val optionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isUpdating) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (state.updateProgress != null) {
                LinearProgressIndicator(
                    progress = { state.updateProgress.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

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
                            .padding(start = 0.dp, end = 4.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = group.displayName(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )

                                val subscription = group.subscription
                                subscription?.username.blankAsNull()?.let { username ->
                                    Text(
                                        text = username,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Row {
                            if (!group.ungrouped) {
                                SimpleIconButton(
                                    imageVector = vectorResource(Res.drawable.edit),
                                    contentDescription = stringResource(Res.string.edit),
                                    onClick = {
                                        openGroupSettings(group.id)
                                    },
                                )
                            }

                            Box {
                                SimpleIconButton(
                                    imageVector = vectorResource(Res.drawable.more_vert),
                                    contentDescription = stringResource(Res.string.menu),
                                    onClick = { showOptionsSheet = true },
                                )

                                if (showOptionsSheet) {
                                    ModalBottomSheet(
                                        onDismissRequest = { showOptionsSheet = false },
                                        sheetState = optionsSheetState,
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            val subscriptionLink =
                                                group.subscription?.link?.blankAsNull()

                                            subscriptionLink?.let { link ->
                                                SheetSectionTitle(
                                                    text = stringResource(Res.string.share_subscription),
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = vectorResource(
                                                                Res.drawable.share,
                                                            ),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    },
                                                )
                                                SheetActionRow(
                                                    text = stringResource(Res.string.action_export_clipboard),
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = vectorResource(
                                                                Res.drawable.content_copy,
                                                            ),
                                                            contentDescription = null,
                                                        )
                                                    },
                                                    onClick = {
                                                        scope.launch {
                                                            clipboard.setPlainText(link)
                                                            snackbar(repo.getString(Res.string.copy_success))
                                                        }
                                                        showOptionsSheet = false
                                                    },
                                                )
                                                SheetActionRow(
                                                    text = stringResource(Res.string.share_qr_nfc),
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = vectorResource(
                                                                Res.drawable.qr_code,
                                                            ),
                                                            contentDescription = null,
                                                        )
                                                    },
                                                    onClick = {
                                                        showQRDialog(link, group.displayName())
                                                        showOptionsSheet = false
                                                    },
                                                )
                                            }
                                            if (group.subscription != null) {
                                                HorizontalDivider()
                                                SheetSectionTitle(
                                                    text = stringResource(Res.string.internal_link),
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = vectorResource(
                                                                Res.drawable.link,
                                                            ),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    },
                                                )
                                                SheetActionRow(
                                                    text = stringResource(Res.string.action_export_clipboard),
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = vectorResource(
                                                                Res.drawable.content_copy,
                                                            ),
                                                            contentDescription = null,
                                                        )
                                                    },
                                                    onClick = {
                                                        scope.launch {
                                                            clipboard.setPlainText(group.toUniversalLink())
                                                            snackbar(repo.getString(Res.string.copy_success))
                                                        }
                                                        showOptionsSheet = false
                                                    },
                                                )
                                                SheetActionRow(
                                                    text = stringResource(Res.string.share_qr_nfc),
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = vectorResource(
                                                                Res.drawable.qr_code,
                                                            ),
                                                            contentDescription = null,
                                                        )
                                                    },
                                                    onClick = {
                                                        showQRDialog(
                                                            group.toUniversalLink(),
                                                            group.displayName(),
                                                        )
                                                        showOptionsSheet = false
                                                    },
                                                )
                                            }
                                            HorizontalDivider()
                                            SheetSectionTitle(
                                                text = stringResource(Res.string.action_export),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.file_export),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                            )
                                            SheetActionRow(
                                                text = stringResource(Res.string.action_export_clipboard),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.content_copy),
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    scope.launch {
                                                        val links = onIoDispatcher {
                                                            SagerDatabase.proxyDao
                                                                .getByGroup(group.id)
                                                                .first()
                                                        }.joinToString("\n") {
                                                            it.toStdLink()
                                                        }
                                                        clipboard.setPlainText(links)
                                                        snackbar(repo.getString(Res.string.copy_success))
                                                    }
                                                    showOptionsSheet = false
                                                },
                                            )
                                            SheetActionRow(
                                                text = stringResource(Res.string.action_export_file),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.file_export),
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    exportToFile()
                                                    showOptionsSheet = false
                                                },
                                            )
                                            HorizontalDivider()
                                            SheetActionRow(
                                                text = stringResource(Res.string.clear_profiles),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.delete),
                                                        contentDescription = null,
                                                    )
                                                },
                                                textColor = MaterialTheme.colorScheme.error,
                                                iconTint = MaterialTheme.colorScheme.error,
                                                onClick = {
                                                    showOptionsSheet = false
                                                    showClearGroupDialog()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val subscription = group.subscription
                            if (subscription != null &&
                                (subscription.bytesUsed > 0L || subscription.bytesRemaining > 0)
                            ) {
                                Text(
                                    text = if (subscription.bytesRemaining > 0L) {
                                        stringResource(
                                            Res.string.subscription_traffic,
                                            Libcore.formatBytes(subscription.bytesUsed),
                                            Libcore.formatBytes(subscription.bytesRemaining),
                                        )
                                    } else {
                                        stringResource(
                                            Res.string.subscription_used,
                                            Libcore.formatBytes(subscription.bytesUsed),
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                if (subscription.expiryDate > 0) {
                                    Text(
                                        text = stringResource(
                                            Res.string.subscription_expire,
                                            formatTime(subscription.expiryDate * 1000L),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Text(
                                text = when (state.group.type) {
                                    GroupType.BASIC -> {
                                        if (state.counts == 0L) {
                                            stringResource(Res.string.group_status_empty)
                                        } else {
                                            stringResource(
                                                Res.string.group_status_proxies,
                                                state.counts,
                                            )
                                        }
                                    }

                                    GroupType.SUBSCRIPTION -> {
                                        if (state.counts == 0L) {
                                            stringResource(Res.string.group_status_empty_subscription)
                                        } else {
                                            val formattedDate = Instant.ofEpochMilli(state.group.subscription!!.lastUpdated * 1000L)
                                                .atZone(ZoneId.systemDefault())
                                                .format(DateTimeFormatter.ofPattern("M - d"))
                                            stringResource(
                                                Res.string.group_status_proxies_subscription,
                                                state.counts,
                                                formattedDate,
                                            )
                                        }
                                    }

                                    else -> error("impossible")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (group.type == GroupType.SUBSCRIPTION) {
                            TextButton(
                                onClick = { viewModel.doUpdate(group) },
                                modifier = Modifier.padding(end = 8.dp),
                                enabled = !state.isUpdating,
                            ) {
                                Text(
                                    text = stringResource(Res.string.group_update),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
