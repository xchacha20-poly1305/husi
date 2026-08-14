@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.CapsuleActionButton
import fr.husi.compose.CapsuleSearchInputField
import fr.husi.compose.CapsuleSearchTopBar
import fr.husi.compose.SagerFabClearance
import fr.husi.compose.SheetActionRow
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.ansiEscape
import fr.husi.compose.fadingEdge
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.RadioButton
import fr.husi.compose.material3.Text
import fr.husi.compose.setPlainText
import fr.husi.ktx.readableMessage
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.action_copy
import fr.husi.resources.cancel
import fr.husi.resources.clear_logcat
import fr.husi.resources.close
import fr.husi.resources.copy_all
import fr.husi.resources.delete_sweep
import fr.husi.resources.keyboard_arrow_down
import fr.husi.resources.logcat
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.pause
import fr.husi.resources.play_arrow
import fr.husi.resources.resume
import fr.husi.resources.scroll_to_bottom
import fr.husi.resources.search
import fr.husi.resources.search_go
import fr.husi.resources.share
import fr.husi.utils.SendLog
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun LogcatScreen(
    modifier: Modifier = Modifier,
    viewModel: LogcatScreenViewModel = viewModel { LogcatScreenViewModel() },
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val snackbar = LocalSnackbarEmitter.current
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    val isAtBottom by remember {
        derivedStateOf {
            !listState.canScrollForward
        }
    }

    var expandMenu by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val queryLowerCase by remember {
        derivedStateOf { uiState.searchQuery?.lowercase() }
    }
    LaunchedEffect(listState) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }
            .collect { isScrolling ->
                if (isScrolling) {
                    wasScrolling = true
                } else if (wasScrolling) {
                    autoScroll = isAtBottom
                    wasScrolling = false
                }
            }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { uiState.logs.size }
            .collect { size ->
                if (size == 0) {
                    autoScroll = true
                    return@collect
                }
                if (!uiState.pause && autoScroll) {
                    listState.scrollToItem(size - 1)
                }
            }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbar.show(StringOrRes.Direct(message))
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing
    val searchBarState = rememberSearchBarState()
    val searchTextFieldState = viewModel.searchTextFieldState
    val searchInputField: @Composable () -> Unit = {
        CapsuleSearchInputField(
            textFieldState = searchTextFieldState,
            searchBarState = searchBarState,
            onSearch = { focusManager.clearFocus() },
            placeholder = { Text(stringResource(Res.string.search_go)) },
            leadingIcon = {
                Icon(vectorResource(Res.drawable.search), null)
            },
            trailingIcon = if (searchBarState.currentValue == SearchBarValue.Expanded) {
                {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.cancel),
                        onClick = {
                            viewModel.clearSearchQuery()
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                    )
                }
            } else {
                null
            },
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleSearchTopBar(
                inputField = searchInputField,
                navigationIcon = null,
                actions = {
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(
                                if (uiState.pause) {
                                    Res.drawable.play_arrow
                                } else {
                                    Res.drawable.pause
                                },
                            ),
                            contentDescription = stringResource(
                                if (uiState.pause) Res.string.resume else Res.string.pause,
                            ),
                            onClick = viewModel::togglePause,
                        )
                    }
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.keyboard_arrow_down),
                            contentDescription = stringResource(Res.string.scroll_to_bottom),
                            onClick = {
                                if (uiState.logs.isNotEmpty()) scope.launch {
                                    listState.animateScrollToItem(uiState.logs.lastIndex)
                                }
                            },
                        )
                    }
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.share),
                            contentDescription = stringResource(Res.string.logcat),
                            onClick = { showBottomSheet = true },
                        )
                    }
                    CapsuleActionButton {
                        Box {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.more_vert),
                                contentDescription = stringResource(Res.string.more),
                                onClick = { expandMenu = true },
                            )
                            DropdownMenuPopup(
                                expanded = expandMenu,
                                onDismissRequest = { expandMenu = false },
                            ) {
                                DropdownMenuGroup(
                                    shapes = MenuDefaults.groupShape(0, 2),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.clear_logcat)) },
                                        onClick = viewModel::clearLog,
                                        leadingIcon = {
                                            Icon(vectorResource(Res.drawable.delete_sweep), null)
                                        },
                                        colors = MenuDefaults.itemColors().copy(
                                            leadingIconColor = MaterialTheme.colorScheme.error,
                                        ),
                                        shape = MenuDefaults.itemShape(0, 1).shape,
                                    )
                                }

                                Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))

                                DropdownMenuGroup(
                                    shapes = MenuDefaults.groupShape(1, 2),
                                ) {
                                    val levels = LogLevel.entries
                                    for ((index, level) in levels.withIndex()) {
                                        DropdownMenuItem(
                                            text = { Text(level.name) },
                                            onClick = {
                                                viewModel.setLogLevel(level)
                                                expandMenu = false
                                            },
                                            trailingIcon = {
                                                RadioButton(
                                                    selected = uiState.logLevel == level,
                                                    onClick = null,
                                                )
                                            },
                                            shape = MenuDefaults.itemShape(index, levels.size).shape,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection),
            top = innerPadding.calculateTopPadding(),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = max(innerPadding.calculateBottomPadding(), SagerFabClearance),
        )
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .fadingEdge(listState),
                            state = listState,
                            contentPadding = contentPadding,
                        ) {
                            itemsIndexed(
                                items = uiState.logs,
                                key = { index, _ -> index },
                                contentType = { _, _ -> 0 },
                            ) { _, logLine ->
                                LogCard(
                                    logLine = logLine.message,
                                    highlightQuery = queryLowerCase,
                                )
                            }
                        }
                    }
                }

                BoxedVerticalScrollbar(
                    modifier = Modifier.fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
                )
            }
        }
    }

    if (showBottomSheet) ModalBottomSheet(
        onDismissRequest = { showBottomSheet = false },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            SheetActionRow(
                text = stringResource(Res.string.action_copy),
                leadingIcon = {
                    Icon(vectorResource(Res.drawable.copy_all), null)
                },
                onClick = {
                    scope.launch {
                        val log = SendLog.buildLog(resolveRepository().externalAssetsDir)
                        clipboard.setPlainText(log)
                    }
                },
            )
            ShareActionRow(scope) { e ->
                snackbar.show(StringOrRes.Direct(e.readableMessage))
            }
        }
    }
}

@Composable
private fun LogCard(
    modifier: Modifier = Modifier,
    logLine: String,
    highlightQuery: String? = null,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = logLine.ansiEscape(highlightQuery),
            modifier = Modifier.padding(12.dp),
        )
    }
}
