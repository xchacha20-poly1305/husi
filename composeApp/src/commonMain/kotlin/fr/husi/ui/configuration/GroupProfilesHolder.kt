@file:OptIn(ExperimentalLayoutApi::class)

package fr.husi.ui.configuration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ernestoyaquello.dragdropswipelazycolumn.DragDropSwipeLazyColumn
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItem
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItemScope
import com.ernestoyaquello.dragdropswipelazycolumn.config.DraggableSwipeableItemColors
import com.ernestoyaquello.dragdropswipelazycolumn.state.rememberDragDropSwipeLazyColumnState
import fr.husi.GroupType
import androidx.compose.foundation.layout.fillMaxHeight
import fr.husi.compose.BoxedVerticalScrollbar
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import fr.husi.compose.SheetActionRow
import fr.husi.compose.SheetSectionTitle
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.colorForUrlTestDelay
import fr.husi.compose.rememberScrollHideState
import fr.husi.compose.setPlainText
import fr.husi.database.DataStore
import fr.husi.database.ProxyEntity
import fr.husi.database.displayType
import fr.husi.fmt.config.ConfigBean
import fr.husi.fmt.toUniversalLink
import fr.husi.ktx.Logs
import fr.husi.ktx.ValidateResult
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.isInsecure
import fr.husi.ktx.onMainDispatcher
import fr.husi.ktx.readableMessage
import fr.husi.ktx.readableUrlTestError
import fr.husi.ui.StringOrRes
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.write
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import fr.husi.resources.*
import fr.husi.libcore.Libcore

@Composable
internal fun GroupHolderScreen(
    modifier: Modifier = Modifier,
    viewModel: GroupProfilesHolderViewModel,
    bottomPadding: Dp,
    showActions: Boolean = true,
    onProfileSelect: (Long) -> Unit,
    openProfileEditor: ((type: Int, id: Long, isSubscription: Boolean, onResult: (updated: Boolean) -> Unit) -> Unit)? = null,
    needReload: () -> Unit,
    showQR: (name: String, url: String) -> Unit,
    onCopySuccess: () -> Unit,
    showSnackbar: (message: StringOrRes) -> Unit,
    showUndoSnackbar: (count: Int, onUndo: () -> Unit) -> Unit,
    onScrollHideChange: (Boolean) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.hiddenProfiles) {
        if (uiState.hiddenProfiles > 0) {
            showUndoSnackbar(uiState.hiddenProfiles) {
                viewModel.undo()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.commit()
        }
    }
    val showAddress by viewModel.alwaysShowAddress.collectAsStateWithLifecycle(false)
    val blurAddress by viewModel.blurredAddress.collectAsStateWithLifecycle(false)
    val trafficStatistics by viewModel.trafficStatistics.collectAsStateWithLifecycle(true)
    val securityAdvisory by viewModel.securityAdvisory.collectAsStateWithLifecycle(true)

    val dragDropListState = rememberDragDropSwipeLazyColumnState()
    val focusRequester = remember { FocusRequester() }

    val scrollHideVisible by rememberScrollHideState(dragDropListState.lazyListState)
    LaunchedEffect(scrollHideVisible) {
        onScrollHideChange(scrollHideVisible)
    }

    LaunchedEffect(uiState.scrollIndex) {
        uiState.scrollIndex?.let { index ->
            dragDropListState.lazyListState.animateScrollToItem(index)
            viewModel.consumeScrollIndex()
        }
    }

    LaunchedEffect(uiState.shouldRequestFocus) {
        if (uiState.shouldRequestFocus) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
                // non-TV environments
            }
            viewModel.consumeFocusRequest()
        }
    }

    var editingID by remember { mutableLongStateOf(-1L) }

    var exportConfig by remember { mutableStateOf("") }
    val exportFileLauncher = rememberFileSaverLauncher { file ->
        if (file != null) lifecycleOwner.lifecycleScope.launch {
            try {
                file.write(exportConfig.encodeToByteArray())
                onMainDispatcher {
                    showSnackbar(StringOrRes.Res(Res.string.action_export_msg))
                }
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher {
                    showSnackbar(StringOrRes.Direct(e.readableMessage))
                }
            }
        }
        exportConfig = ""
    }

    var showErrorAlert by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = modifier.fillMaxSize(),
    ) {
        DragDropSwipeLazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(focusRequester),
            state = dragDropListState,
            items = uiState.profiles.toImmutableList(),
            key = { it.profile.id },
            contentType = { 0 },
            contentPadding = PaddingValues(
                bottom = bottomPadding,
            ),
            userScrollEnabled = true,
            onIndicesChangedViaDragAndDrop = { viewModel.submitReordered(it) },
        ) { index, item ->
            DraggableSwipeableItem(
                modifier = Modifier
                    .padding(4.dp)
                    .animateDraggableSwipeableItem(),
                colors = DraggableSwipeableItemColors.createRemembered(
                    containerBackgroundColor = Color.Transparent,
                    containerBackgroundColorWhileDragged = Color.Transparent,
                    clickIndicationColor = Color.Transparent,
                    behindSwipeContainerBackgroundColor = Color.Transparent,
                    behindSwipeIconColor = Color.Transparent,
                ),
            ) {
                ProxyCard(
                    profile = item,
                    select = { onProfileSelect(item.profile.id) },
                    edit = {
                        editingID = item.profile.id
                        openProfileEditor?.invoke(
                            item.profile.type,
                            item.profile.id,
                            viewModel.group.type == GroupType.SUBSCRIPTION,
                        ) { updated ->
                            if (updated && editingID == DataStore.selectedProxy) {
                                needReload()
                            }
                        }
                    },
                    delete = { viewModel.undoableRemove(item.profile.id) },
                    showQR = { url ->
                        showQR(item.profile.displayName(), url)
                    },
                    exportToFile = { name, config ->
                        exportConfig = config
                        exportFileLauncher.launch(name, "json")
                    },
                    showErrorAlert = { showErrorAlert = it },
                    onCopySuccess = onCopySuccess,
                    showAddress = showAddress,
                    blurAddress = blurAddress,
                    trafficStatistic = trafficStatistics,
                    securityAdvice = securityAdvisory,
                    showActions = showActions,
                )
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

    if (showErrorAlert != null) AlertDialog(
        onDismissRequest = { showErrorAlert = null },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                showErrorAlert = null
            }
        },
        icon = {
            Icon(vectorResource(Res.drawable.error), null)
        },
        title = { Text(stringResource(Res.string.error_title)) },
        text = { Text(showErrorAlert!!) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraggableSwipeableItemScope<ProfileItem>.ProxyCard(
    modifier: Modifier = Modifier,
    profile: ProfileItem,
    select: () -> Unit,
    edit: () -> Unit,
    delete: () -> Unit,
    showQR: (url: String) -> Unit,
    onCopySuccess: () -> Unit,
    exportToFile: (name: String, config: String) -> Unit,
    showErrorAlert: (String) -> Unit,
    showAddress: Boolean,
    blurAddress: Boolean,
    trafficStatistic: Boolean,
    securityAdvice: Boolean,
    showActions: Boolean = true,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val entity = profile.profile
    val bean = entity.requireBean()

    val (name, address) = when {
        blurAddress && bean.name.isBlank() -> bean.displayAddress().blur() to null
        blurAddress && showAddress -> bean.displayName() to bean.displayAddress().blur()
        showAddress -> bean.displayName() to bean.displayAddress()
        else -> bean.displayName() to null
    }

    val hasTraffic = entity.tx + entity.rx > 0L
    val trafficText = hasTraffic.takeIf { trafficStatistic }?.let {
        stringResource(
            Res.string.traffic,
            Libcore.formatBytes(entity.tx),
            Libcore.formatBytes(entity.rx),
        )
    }

    val (statusText, statusColor) = when (entity.status) {
        in Int.MIN_VALUE..ProxyEntity.STATUS_INITIAL -> {
            trafficText.orEmpty() to MaterialTheme.colorScheme.onSurfaceVariant
        }

        ProxyEntity.STATUS_AVAILABLE -> {
            stringResource(
                Res.string.available,
                entity.ping,
            ) to colorForUrlTestDelay(entity.ping)
        }

        ProxyEntity.STATUS_UNAVAILABLE -> {
            val text = readableUrlTestError(entity.error)?.let { stringResource(it) }
                ?: stringResource(Res.string.unavailable)
            text to Color.Red
        }

        ProxyEntity.STATUS_UNREACHABLE -> {
            val text = readableUrlTestError(entity.error)?.let { stringResource(it) }
                ?: stringResource(Res.string.connection_test_unreachable)
            text to Color.Red
        }

        else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val showMiddleRow =
        address != null || (hasTraffic && entity.status > ProxyEntity.STATUS_INITIAL)

    var showShareSheet by remember { mutableStateOf(false) }
    var showSecurityAlert by remember { mutableStateOf(false) }
    val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val validateResult = if (showActions && securityAdvice) {
        bean.isInsecure()
    } else {
        ValidateResult.Secure
    }

    OutlinedCard(
        onClick = select,
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(),
        border = if (profile.isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardDefaults.outlinedCardBorder()
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.drag_indicator),
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(40.dp)
                    .padding(8.dp)
                    .dragDropModifier(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 0.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    if (showActions) {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.edit),
                            contentDescription = stringResource(Res.string.edit),
                            modifier = Modifier.size(40.dp),
                            onClick = edit,
                        )

                        val shareIcon: DrawableResource
                        val shareBackground: Color
                        val shareTint: Color
                        when (validateResult) {
                            is ValidateResult.Insecure -> {
                                shareIcon = Res.drawable.warning
                                shareBackground = Color.Red
                                shareTint = Color.White
                            }

                            is ValidateResult.Deprecated -> {
                                shareIcon = Res.drawable.warning
                                shareBackground = Color.Yellow
                                shareTint = Color.Gray
                            }

                            is ValidateResult.Secure -> {
                                shareIcon = Res.drawable.share
                                shareBackground = Color.Transparent
                                shareTint = MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        }

                        Box {
                            val shareTooltipText = when (validateResult) {
                                is ValidateResult.Insecure -> stringResource(Res.string.insecure)
                                is ValidateResult.Deprecated -> stringResource(Res.string.deprecated)
                                is ValidateResult.Secure -> stringResource(Res.string.share)
                            }
                            val shareTooltipState = rememberTooltipState()

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(shareBackground, shape = CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                        positioning = TooltipAnchorPosition.Below,
                                    ),
                                    tooltip = {
                                        PlainTooltip {
                                            Text(shareTooltipText)
                                        }
                                    },
                                    state = shareTooltipState,
                                ) {
                                    IconButton(
                                        onClick = {
                                            when (validateResult) {
                                                is ValidateResult.Insecure, is ValidateResult.Deprecated -> {
                                                    showSecurityAlert = true
                                                }

                                                is ValidateResult.Secure -> {
                                                    showShareSheet = true
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            imageVector = vectorResource(shareIcon),
                                            contentDescription = shareTooltipText,
                                            tint = shareTint,
                                        )
                                    }
                                }
                            }

                            if (showShareSheet) {
                                val canNotShareOutbound = entity.type == ProxyEntity.TYPE_CHAIN ||
                                        entity.type == ProxyEntity.TYPE_PROXY_SET ||
                                        entity.mustUsePlugin() ||
                                        (bean as? ConfigBean)?.type == ConfigBean.TYPE_CONFIG

                                ModalBottomSheet(
                                    onDismissRequest = { showShareSheet = false },
                                    sheetState = shareSheetState,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (entity.haveLink()) {
                                            SheetSectionTitle(
                                                text = stringResource(Res.string.share_qr_nfc),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.qr_code),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                            )
                                            if (entity.haveStandardLink()) {
                                                SheetActionRow(
                                                    text = stringResource(Res.string.standard),
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = vectorResource(
                                                                Res.drawable.send,
                                                            ),
                                                            contentDescription = null,
                                                        )
                                                    },
                                                    onClick = {
                                                        showQR(entity.toStdLink())
                                                        showShareSheet = false
                                                    },
                                                )
                                            }
                                            SheetActionRow(
                                                text = stringResource(Res.string.internal_link),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.link),
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    showQR(bean.toUniversalLink())
                                                    showShareSheet = false
                                                },
                                            )
                                            HorizontalDivider()
                                            SheetSectionTitle(
                                                text = stringResource(Res.string.action_export_clipboard),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.share),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                            )
                                            if (entity.haveStandardLink()) {
                                                SheetActionRow(
                                                    text = stringResource(Res.string.standard),
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
                                                            clipboard.setPlainText(entity.toStdLink())
                                                            onCopySuccess()
                                                        }
                                                        showShareSheet = false
                                                    },
                                                )
                                            }
                                            SheetActionRow(
                                                text = stringResource(Res.string.internal_link),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.fingerprint),
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    scope.launch {
                                                        clipboard.setPlainText(bean.toUniversalLink())
                                                        onCopySuccess()
                                                    }
                                                    showShareSheet = false
                                                },
                                            )
                                        }
                                        HorizontalDivider()
                                        SheetSectionTitle(
                                            text = stringResource(Res.string.menu_configuration),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = vectorResource(Res.drawable.settings),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            },
                                        )
                                        SheetActionRow(
                                            text = stringResource(Res.string.action_export_clipboard),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = vectorResource(Res.drawable.copy_all),
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                scope.launch {
                                                    runCatching {
                                                        clipboard.setPlainText(entity.exportConfig().first)
                                                    }.onSuccess {
                                                        onCopySuccess()
                                                    }.onFailure { e ->
                                                        showErrorAlert(e.readableMessage)
                                                    }
                                                }
                                                showShareSheet = false
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
                                                runCatching {
                                                    val data = entity.exportConfig()
                                                    exportToFile(data.second, data.first)
                                                }.onFailure { e ->
                                                    showErrorAlert(e.readableMessage)
                                                }
                                                showShareSheet = false
                                            },
                                        )

                                        if (!canNotShareOutbound) {
                                            HorizontalDivider()
                                            SheetSectionTitle(
                                                text = stringResource(Res.string.outbound),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.arrow_outward),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                            )
                                            SheetActionRow(
                                                text = stringResource(Res.string.action_export_clipboard),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = vectorResource(Res.drawable.copy_all),
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    scope.launch {
                                                        clipboard.setPlainText(entity.exportOutbound().first)
                                                        onCopySuccess()
                                                    }
                                                    showShareSheet = false
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
                                                    val data = entity.exportOutbound()
                                                    exportToFile(data.second, data.first)
                                                    showShareSheet = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.delete),
                            contentDescription = stringResource(Res.string.delete),
                            modifier = Modifier.size(40.dp),
                            onClick = delete,
                        )
                    }
                }

                if (showMiddleRow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 0.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        address?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        if (hasTraffic && entity.status > ProxyEntity.STATUS_INITIAL) {
                            trafficText?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 0.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = entity.displayType(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )

                    if (statusText.isNotEmpty()) {
                        val errorText = entity.error?.blankAsNull()
                        Text(
                            text = statusText,
                            modifier = Modifier.clickable {
                                errorText?.let(showErrorAlert)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor,
                        )
                    }
                }
            }
        }
    }

    if (showActions && showSecurityAlert) AlertDialog(
        onDismissRequest = {
            showSecurityAlert = false
            showShareSheet = true
        },
        icon = {
            Icon(vectorResource(Res.drawable.warning), null)
        },
        title = {
            Text(
                stringResource(
                    when (validateResult) {
                        is ValidateResult.Insecure -> Res.string.insecure
                        is ValidateResult.Deprecated -> Res.string.deprecated
                        else -> error("impossible")
                    },
                ),
            )
        },
        text = {
            val textRes = when (validateResult) {
                is ValidateResult.Insecure -> validateResult.textRes
                is ValidateResult.Deprecated -> validateResult.textRes
                else -> error("impossible")
            }
            Text(stringResource(textRes))
        },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                showSecurityAlert = false
                showShareSheet = true
            }
        },
    )
}

/** Make server address blurred. */
private fun String.blur(): String = when (length) {
    in 0 until 20 -> {
        val halfLength = length / 2
        substring(0, halfLength) + "*".repeat(length - halfLength)
    }

    in 20..30 -> substring(0, 15) + "*".repeat(length - 15)

    else -> substring(0, 15) + "*".repeat(15)
}
