package io.nekohasekai.sagernet.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
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
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.AutoFadeVerticalScrollbar
import io.nekohasekai.sagernet.compose.HideOnBottomScrollBehavior
import io.nekohasekai.sagernet.compose.QRCodeDialog
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.paddingWithNavigation
import io.nekohasekai.sagernet.compose.setPlainText
import io.nekohasekai.sagernet.compose.showAndDismissOld
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.ComposeHolderBinding
import io.nekohasekai.sagernet.fmt.toUniversalLink
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.formatTime
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
class GroupFragment : OnKeyDownFragment(R.layout.compose_holder) {

    private val viewModel: GroupFragmentViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = (requireActivity() as MainActivity)
        val binding = ComposeHolderBinding.bind(view)
        binding.root.setContent {
            AppTheme {
                GroupScreen(
                    viewModel = viewModel,
                    openDrawer = {
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
private fun GroupScreen(
    modifier: Modifier = Modifier,
    viewModel: GroupFragmentViewModel,
    openDrawer: () -> Unit,
    fab: FloatingActionButton,
    bottomBar: BottomAppBar,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
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
            val result = snackbarHostState.showAndDismissOld(
                message = resources.getQuantityString(
                    R.plurals.removed,
                    uiState.hiddenGroups,
                    uiState.hiddenGroups,
                ),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undo()
            }
        }
    }

    var groupToExport by remember { mutableStateOf<Long?>(null) }
    val exportProfiles = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { data ->
        if (data != null && groupToExport != null) {
            viewModel.exportToFile(
                group = groupToExport!!,
                writeContent = { content ->
                    context.contentResolver.openOutputStream(data)!!.bufferedWriter().use {
                        it.write(content)
                    }
                },
                showSnackbar = { str ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getStringOrRes(str),
                            actionLabel = context.getString(android.R.string.ok),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            )
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
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

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_group)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.menu),
                        contentDescription = stringResource(R.string.menu),
                        onClick = openDrawer,
                    )
                },
                actions = {
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.update),
                        contentDescription = stringResource(R.string.update_all_subscription),
                        onClick = { showUpdateAll = true },
                    )
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.playlist_add),
                        contentDescription = stringResource(R.string.group_create),
                        onClick = {
                            context.startActivity(
                                Intent(context, GroupSettingsActivity::class.java),
                            )
                        },
                    )
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomBarHeightDp),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            DragDropSwipeLazyColumn(
                state = dragDropListState,
                items = uiState.groups.toImmutableList(),
                key = { it.group.id },
                contentType = { 0 },
                userScrollEnabled = true,
                contentPadding = innerPadding.paddingWithNavigation(),
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
                                Icon(ImageVector.vectorResource(R.drawable.delete), null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GroupCard(
                            state = groupState,
                            viewModel = viewModel,
                            snackbar = { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = message,
                                        actionLabel = context.getString(android.R.string.ok),
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
                                exportProfiles.launch("profiles_${groupState.group.displayName()}.txt")
                            },
                        )
                    }
                }
            }

            AutoFadeVerticalScrollbar(
                modifier = Modifier.align(Alignment.TopEnd),
                scrollState = dragDropListState.lazyListState,
                style = defaultMaterialScrollbarStyle().copy(
                    thickness = 12.dp,
                ),
            )
        }
    }

    if (showUpdateAll) AlertDialog(
        onDismissRequest = { showUpdateAll = false },
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
                viewModel.doUpdateAll()
                showUpdateAll = false
            }
        },
        dismissButton = {
            TextButton(stringResource(android.R.string.cancel)) {
                showUpdateAll = false
            }
        },
        icon = { Icon(ImageVector.vectorResource(R.drawable.update), null) },
        title = { Text(stringResource(R.string.confirm)) },
        text = { Text(stringResource(R.string.update_all_subscription)) },
    )

    qrDialogData?.let { (url, name) ->
        QRCodeDialog(
            url = url,
            name = name,
            onDismiss = { qrDialogData = null },
            showSnackbar = { message ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = context.getString(android.R.string.ok),
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
                TextButton(stringResource(android.R.string.ok)) {
                    viewModel.clearGroup(id)
                    clearGroupConfirm = null
                }
            },
            dismissButton = {
                TextButton(stringResource(android.R.string.cancel)) {
                    clearGroupConfirm = null
                }
            },
            icon = { Icon(ImageVector.vectorResource(R.drawable.mop), null) },
            title = { Text(stringResource(R.string.confirm)) },
            text = { Text(stringResource(R.string.clear_profiles_message)) },
        )
    }
}

@Composable
private fun DraggableSwipeableItemScope<GroupItemUiState>.GroupCard(
    modifier: Modifier = Modifier,
    state: GroupItemUiState,
    viewModel: GroupFragmentViewModel,
    snackbar: suspend (message: String) -> Unit,
    showQRDialog: (url: String, name: String) -> Unit,
    showClearGroupDialog: () -> Unit,
    exportToFile: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val group = state.group

    var showOptionsMenu by remember { mutableStateOf(false) }
    var showShareSubscription by remember { mutableStateOf(false) }
    var showUniversalLinkMenu by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

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
                    imageVector = ImageVector.vectorResource(R.drawable.drag_indicator),
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
                                    imageVector = ImageVector.vectorResource(R.drawable.edit),
                                    contentDescription = stringResource(R.string.edit),
                                    onClick = {
                                        context.startActivity(
                                            Intent(context, GroupSettingsActivity::class.java)
                                                .putExtra(
                                                    GroupSettingsActivity.EXTRA_GROUP_ID,
                                                    group.id,
                                                ),
                                        )
                                    },
                                )
                            }

                            Box {
                                SimpleIconButton(
                                    imageVector = ImageVector.vectorResource(R.drawable.more_vert),
                                    contentDescription = stringResource(R.string.menu),
                                    onClick = { showOptionsMenu = true },
                                )

                                DropdownMenu(
                                    expanded = showOptionsMenu,
                                    onDismissRequest = { showOptionsMenu = false },
                                ) {
                                    if (group.subscription?.link?.isNotBlank() == true) DropdownMenuItem(
                                        text = { Text(stringResource(R.string.share_subscription)) },
                                        onClick = {
                                            showOptionsMenu = false
                                            showShareSubscription = true
                                        },
                                        trailingIcon = {
                                            Icon(
                                                ImageVector.vectorResource(R.drawable.keyboard_arrow_right),
                                                null,
                                            )
                                        },
                                    )

                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.internal_link)) },
                                        onClick = {
                                            showOptionsMenu = false
                                            showUniversalLinkMenu = true
                                        },
                                        trailingIcon = {
                                            Icon(
                                                ImageVector.vectorResource(R.drawable.keyboard_arrow_right),
                                                null,
                                            )
                                        },
                                    )

                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_export)) },
                                        onClick = {
                                            showOptionsMenu = false
                                            showExport = true
                                        },
                                        trailingIcon = {
                                            Icon(
                                                ImageVector.vectorResource(R.drawable.keyboard_arrow_right),
                                                null,
                                            )
                                        },
                                    )

                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.clear_profiles)) },
                                        onClick = showClearGroupDialog,
                                        trailingIcon = {
                                            Icon(
                                                ImageVector.vectorResource(R.drawable.delete),
                                                null,
                                            )
                                        },
                                    )
                                }

                                group.subscription?.link?.blankAsNull()?.let { link ->
                                    DropdownMenu(
                                        expanded = showShareSubscription,
                                        onDismissRequest = { showShareSubscription = false },
                                    ) {
                                        Text(
                                            text = stringResource(R.string.share_subscription),
                                            modifier = Modifier.padding(
                                                horizontal = 16.dp,
                                                vertical = 12.dp,
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_export_clipboard)) },
                                            onClick = {
                                                scope.launch {
                                                    clipboard.setPlainText(link)
                                                    snackbar(context.getString(R.string.copy_success))
                                                }
                                                showShareSubscription = false
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    ImageVector.vectorResource(R.drawable.content_copy),
                                                    null,
                                                )
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.share_qr_nfc)) },
                                            onClick = {
                                                showQRDialog(link, group.displayName())
                                                showShareSubscription = false
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    ImageVector.vectorResource(R.drawable.qr_code),
                                                    null,
                                                )
                                            },
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showUniversalLinkMenu,
                                    onDismissRequest = { showUniversalLinkMenu = false },
                                ) {
                                    Text(
                                        text = stringResource(R.string.internal_link),
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 12.dp,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_export_clipboard)) },
                                        onClick = {
                                            scope.launch {
                                                clipboard.setPlainText(group.toUniversalLink())
                                                snackbar(context.getString(R.string.copy_success))
                                            }
                                            showUniversalLinkMenu = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.share_qr_nfc)) },
                                        onClick = {
                                            showQRDialog(
                                                group.toUniversalLink(),
                                                group.displayName(),
                                            )
                                            showUniversalLinkMenu = false
                                        },
                                    )
                                }

                                DropdownMenu(
                                    expanded = showExport,
                                    onDismissRequest = { showExport = false },
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_export),
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 12.dp,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_export_clipboard)) },
                                        onClick = {
                                            scope.launch {
                                                val links = onIoDispatcher {
                                                    SagerDatabase.proxyDao
                                                        .getByGroup(group.id)
                                                        .joinToString("\n") {
                                                            it.toStdLink()
                                                        }
                                                }
                                                clipboard.setPlainText(links)
                                                snackbar(context.getString(R.string.copy_success))
                                            }
                                            showExport = false
                                        },
                                        trailingIcon = {
                                            Icon(
                                                ImageVector.vectorResource(R.drawable.content_copy),
                                                null,
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_export_file)) },
                                        onClick = {
                                            exportToFile()
                                            showExport = false
                                        },
                                        trailingIcon = {
                                            Icon(
                                                ImageVector.vectorResource(R.drawable.file_export),
                                                null,
                                            )
                                        },
                                    )
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
                                            R.string.subscription_traffic,
                                            Formatter.formatFileSize(
                                                context,
                                                subscription.bytesUsed,
                                            ),
                                            Formatter.formatFileSize(
                                                context,
                                                subscription.bytesRemaining,
                                            ),
                                        )
                                    } else {
                                        stringResource(
                                            R.string.subscription_used,
                                            Formatter.formatFileSize(
                                                context,
                                                subscription.bytesUsed,
                                            ),
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                if (subscription.expiryDate > 0) {
                                    Text(
                                        text = stringResource(
                                            R.string.subscription_expire,
                                            context.formatTime(subscription.expiryDate * 1000L),
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
                                            stringResource(R.string.group_status_empty)
                                        } else {
                                            stringResource(
                                                R.string.group_status_proxies,
                                                state.counts,
                                            )
                                        }
                                    }

                                    GroupType.SUBSCRIPTION -> {
                                        if (state.counts == 0L) {
                                            stringResource(R.string.group_status_empty_subscription)
                                        } else {
                                            val dateFormat =
                                                SimpleDateFormat("M - d", Locale.getDefault())
                                            val formattedDate = dateFormat.format(
                                                Date(state.group.subscription!!.lastUpdated * 1000L),
                                            )
                                            stringResource(
                                                R.string.group_status_proxies_subscription,
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
                                    text = stringResource(R.string.group_update),
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