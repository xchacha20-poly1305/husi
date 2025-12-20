@file:OptIn(ExperimentalLayoutApi::class)

package io.nekohasekai.sagernet.ui.configuration

import android.app.Activity
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ernestoyaquello.dragdropswipelazycolumn.DragDropSwipeLazyColumn
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItem
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItemScope
import com.ernestoyaquello.dragdropswipelazycolumn.config.DraggableSwipeableItemColors
import com.ernestoyaquello.dragdropswipelazycolumn.state.rememberDragDropSwipeLazyColumnState
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.AutoFadeVerticalScrollbar
import io.nekohasekai.sagernet.compose.SheetActionRow
import io.nekohasekai.sagernet.compose.SheetSectionTitle
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.colorForUrlTestDelay
import io.nekohasekai.sagernet.compose.rememberScrollHideState
import io.nekohasekai.sagernet.compose.setPlainText
import io.nekohasekai.sagernet.compose.startFilesForResult
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.displayType
import io.nekohasekai.sagernet.fmt.config.ConfigBean
import io.nekohasekai.sagernet.fmt.toUniversalLink
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.ValidateResult
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.isInsecure
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.readableUrlTestError
import io.nekohasekai.sagernet.ui.StringOrRes
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
internal fun GroupHolderScreen(
    modifier: Modifier = Modifier,
    viewModel: GroupProfilesHolderViewModel,
    showActions: Boolean = true,
    onProfileSelect: (Long) -> Unit,
    needReload: () -> Unit,
    showQR: (name: String, url: String) -> Unit,
    onCopySuccess: () -> Unit,
    showSnackbar: (message: StringOrRes) -> Unit,
    showUndoSnackbar: (count: Int, onUndo: () -> Unit) -> Unit,
    onScrollHideChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
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
    val editProfileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (DataStore.currentProfile == editingID) {
                needReload()
            }
            editingID = -1L
        }
    }

    var exportConfig by remember { mutableStateOf("") }
    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { data ->
        if (data != null) lifecycleOwner.lifecycleScope.launch {
            try {
                context.contentResolver.openOutputStream(data)!!
                    .bufferedWriter().use {
                        it.write(exportConfig)
                    }
                onMainDispatcher {
                    showSnackbar(StringOrRes.Res(R.string.action_export_msg))
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

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        DragDropSwipeLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
            state = dragDropListState,
            items = uiState.profiles.toImmutableList(),
            key = { it.profile.id },
            contentType = { 0 },
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBarsIgnoringVisibility
                    .asPaddingValues()
                    .calculateBottomPadding(),
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
                val context = LocalContext.current
                ProxyCard(
                    profile = item,
                    select = { onProfileSelect(item.profile.id) },
                    edit = {
                        editingID = item.profile.id
                        editProfileLauncher.launch(
                            item.profile.settingIntent(
                                ctx = context,
                                isSubscription = viewModel.group.type == GroupType.SUBSCRIPTION,
                            ),
                        )
                    },
                    delete = { viewModel.undoableRemove(item.profile.id) },
                    showQR = { url ->
                        showQR(item.profile.displayName(), url)
                    },
                    exportToFile = { name, config ->
                        exportConfig = config
                        startFilesForResult(exportFileLauncher, name) {
                            showSnackbar(StringOrRes.Res(it))
                        }
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

        AutoFadeVerticalScrollbar(
            modifier = Modifier.align(Alignment.TopEnd),
            scrollState = dragDropListState.lazyListState,
            style = defaultMaterialScrollbarStyle().copy(
                thickness = 12.dp,
            ),
        )
    }

    if (showErrorAlert != null) AlertDialog(
        onDismissRequest = { showErrorAlert = null },
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
                showErrorAlert = null
            }
        },
        icon = {
            Icon(ImageVector.vectorResource(R.drawable.error), null)
        },
        title = { Text(stringResource(R.string.error_title)) },
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
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val entity = profile.profile
    val bean = entity.requireBean()

    val (name, address) = when {
        blurAddress && bean.name.isNullOrBlank() -> bean.displayAddress().blur() to null
        blurAddress && showAddress -> bean.displayName() to bean.displayAddress().blur()
        showAddress -> bean.displayName() to bean.displayAddress()
        else -> bean.displayName() to null
    }

    val hasTraffic = entity.tx + entity.rx > 0L
    val trafficText = hasTraffic.takeIf { trafficStatistic }?.let {
        stringResource(
            R.string.traffic,
            Formatter.formatFileSize(context, entity.tx),
            Formatter.formatFileSize(context, entity.rx),
        )
    }

    val (statusText, statusColor) = when (entity.status) {
        in Int.MIN_VALUE..ProxyEntity.STATUS_INITIAL -> {
            trafficText.orEmpty() to MaterialTheme.colorScheme.onSurfaceVariant
        }

        ProxyEntity.STATUS_AVAILABLE -> {
            stringResource(
                R.string.available,
                entity.ping,
            ) to colorForUrlTestDelay(entity.ping)
        }

        ProxyEntity.STATUS_UNAVAILABLE -> {
            val text = readableUrlTestError(entity.error)?.let { stringResource(it) }
                ?: stringResource(R.string.unavailable)
            text to Color.Red
        }

        ProxyEntity.STATUS_UNREACHABLE -> {
            val text = readableUrlTestError(entity.error)?.let { stringResource(it) }
                ?: stringResource(R.string.connection_test_unreachable)
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
                imageVector = ImageVector.vectorResource(R.drawable.drag_indicator),
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
                            imageVector = ImageVector.vectorResource(R.drawable.edit),
                            contentDescription = stringResource(R.string.edit),
                            modifier = Modifier.size(40.dp),
                            onClick = edit,
                        )

                        val shareIcon: Int
                        val shareBackground: Color
                        val shareTint: Color
                        when (validateResult) {
                            is ValidateResult.Insecure -> {
                                shareIcon = R.drawable.warning
                                shareBackground = Color.Red
                                shareTint = Color.White
                            }

                            is ValidateResult.Deprecated -> {
                                shareIcon = R.drawable.warning
                                shareBackground = Color.Yellow
                                shareTint = Color.Gray
                            }

                            is ValidateResult.Secure -> {
                                shareIcon = R.drawable.share
                                shareBackground = Color.Transparent
                                shareTint = MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        }

                        Box {
                            val shareTooltipText = when (validateResult) {
                                is ValidateResult.Insecure -> stringResource(R.string.insecure)
                                is ValidateResult.Deprecated -> stringResource(R.string.deprecated)
                                is ValidateResult.Secure -> stringResource(R.string.share)
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
                                            imageVector = ImageVector.vectorResource(shareIcon),
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
                                                text = stringResource(R.string.share_qr_nfc),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = ImageVector.vectorResource(R.drawable.qr_code),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                            )
                                            if (entity.haveStandardLink()) {
                                                SheetActionRow(
                                                    text = stringResource(R.string.standard),
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = ImageVector.vectorResource(
                                                                R.drawable.send,
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
                                                text = stringResource(R.string.internal_link),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = ImageVector.vectorResource(R.drawable.link),
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
                                                text = stringResource(R.string.action_export_clipboard),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = ImageVector.vectorResource(R.drawable.share),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                            )
                                            if (entity.haveStandardLink()) {
                                                SheetActionRow(
                                                    text = stringResource(R.string.standard),
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = ImageVector.vectorResource(
                                                                R.drawable.content_copy,
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
                                                text = stringResource(R.string.internal_link),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = ImageVector.vectorResource(R.drawable.fingerprint),
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
                                            text = stringResource(R.string.menu_configuration),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = ImageVector.vectorResource(R.drawable.settings),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            },
                                        )
                                        SheetActionRow(
                                            text = stringResource(R.string.action_export_clipboard),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = ImageVector.vectorResource(R.drawable.copy_all),
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                scope.launch {
                                                    clipboard.setPlainText(entity.exportConfig().first)
                                                    onCopySuccess()
                                                }
                                                showShareSheet = false
                                            },
                                        )
                                        SheetActionRow(
                                            text = stringResource(R.string.action_export_file),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = ImageVector.vectorResource(R.drawable.file_export),
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                val data = entity.exportConfig()
                                                exportToFile(data.second, data.first)
                                                showShareSheet = false
                                            },
                                        )

                                        if (!canNotShareOutbound) {
                                            HorizontalDivider()
                                            SheetSectionTitle(
                                                text = stringResource(R.string.outbound),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = ImageVector.vectorResource(R.drawable.arrow_outward),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                            )
                                            SheetActionRow(
                                                text = stringResource(R.string.action_export_clipboard),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = ImageVector.vectorResource(R.drawable.copy_all),
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
                                                text = stringResource(R.string.action_export_file),
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = ImageVector.vectorResource(R.drawable.file_export),
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
                            imageVector = ImageVector.vectorResource(R.drawable.delete),
                            contentDescription = stringResource(R.string.delete),
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
                        text = entity.displayType(context),
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
            Icon(ImageVector.vectorResource(R.drawable.warning), null)
        },
        title = {
            Text(
                stringResource(
                    when (validateResult) {
                        is ValidateResult.Insecure -> R.string.insecure
                        is ValidateResult.Deprecated -> R.string.deprecated
                        else -> error("impossible")
                    },
                ),
            )
        },
        text = {
            val rawRes = when (validateResult) {
                is ValidateResult.Insecure -> validateResult.textRes
                is ValidateResult.Deprecated -> validateResult.textRes
                else -> error("impossible")
            }
            resources.openRawResource(rawRes).bufferedReader().use {
                Text(it.readText())
            }
        },
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
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
