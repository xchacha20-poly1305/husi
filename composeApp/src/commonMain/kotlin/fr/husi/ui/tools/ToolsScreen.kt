package fr.husi.ui.tools

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import fr.husi.compose.SwipeableSnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.util.fastCoerceIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.material3.PrimaryTabRow
import fr.husi.compose.material3.Tab
import fr.husi.compose.material3.Text
import fr.husi.compose.paddingExceptBottom
import fr.husi.database.DataStore
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.backup
import fr.husi.resources.menu_tools
import fr.husi.resources.ok
import fr.husi.resources.tools_network
import fr.husi.ui.MainViewModel
import fr.husi.ui.MainViewModelAlertDialog
import fr.husi.ui.MainViewModelUiEvent
import fr.husi.ui.NavRoutes
import fr.husi.ui.getStringOrRes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

private const val PAGE_NETWORK = 0
private const val PAGE_BACKUP = 1
private const val PAGE_DEBUG = 2

@Composable
fun ToolsScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    onBackPress: () -> Unit,
    onOpenTool: (NavRoutes.ToolsPage) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    var showAlertDialog by remember { mutableStateOf<MainViewModelUiEvent.AlertDialog?>(null) }

    val isExpert by DataStore.configurationStore
        .booleanFlow(Key.APP_EXPERT, false)
        .collectAsStateWithLifecycle(false)
    val pagerState = rememberPagerState(
        initialPage = PAGE_NETWORK,
        pageCount = { 2 + if (isExpert) 1 else 0 },
    )

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val topAppBarColors = TopAppBarDefaults.topAppBarColors()
    val appBarContainerColor by animateColorAsState(
        targetValue = lerp(
            topAppBarColors.containerColor,
            topAppBarColors.scrolledContainerColor,
            scrollBehavior.state.overlappedFraction.fastCoerceIn(0f, 1f),
        ),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "appBarContainerColor",
    )
    val windowInsets = WindowInsets.safeDrawing

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Surface(color = appBarContainerColor) {
                Column {
                    CapsuleTopBar(
                        navigationIcon = {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.arrow_back),
                                contentDescription = stringResource(Res.string.back),
                                onClick = onBackPress,
                            )
                        },
                        title = { Text(stringResource(Res.string.menu_tools)) },
                        windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        scrollBehavior = scrollBehavior,
                    )
                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = appBarContainerColor,
                    ) {
                        Tab(
                            selected = pagerState.currentPage == PAGE_NETWORK,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(PAGE_NETWORK)
                                }
                            },
                            text = { Text(stringResource(Res.string.tools_network)) },
                        )
                        Tab(
                            selected = pagerState.currentPage == PAGE_BACKUP,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(PAGE_BACKUP)
                                }
                            },
                            text = { Text(stringResource(Res.string.backup)) },
                        )
                        if (isExpert) Tab(
                            selected = pagerState.currentPage == PAGE_DEBUG,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(PAGE_DEBUG)
                                }
                            },
                            text = { Text("DEBUG") },
                        )
                    }
                }
            }
        },
        snackbarHost = { SwipeableSnackbarHost(snackbarState) },
    ) { innerPadding ->
        val bottomPadding = innerPadding.calculateBottomPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    PAGE_NETWORK -> NetworkScreen(
                        bottomPadding = bottomPadding,
                        onVisibleChange = {},
                        onOpenTool = onOpenTool,
                    )

                    PAGE_BACKUP -> BackupScreen(
                        bottomPadding = bottomPadding,
                        onVisibleChange = {},
                        showSnackbar = { message ->
                            scope.launch {
                                snackbarState.showSnackbar(
                                    message = message,
                                    actionLabel = resolveRepository().getString(Res.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                    )

                    PAGE_DEBUG -> DebugScreen(
                        bottomPadding = bottomPadding,
                        onVisibleChange = {},
                        showSnackbar = { message ->
                            scope.launch {
                                snackbarState.showSnackbar(
                                    message = message,
                                    actionLabel = resolveRepository().getString(Res.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.uiEvent.collect { event ->
            when (event) {
                is MainViewModelUiEvent.Snackbar -> scope.launch {
                    snackbarState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = resolveRepository().getString(Res.string.ok),
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

                is MainViewModelUiEvent.AlertDialog -> showAlertDialog = event
            }
        }
    }

    showAlertDialog?.let { dialog ->
        MainViewModelAlertDialog(dialog) {
            showAlertDialog = null
        }
    }
}
