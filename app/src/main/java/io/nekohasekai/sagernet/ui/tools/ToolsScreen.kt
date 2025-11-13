package io.nekohasekai.sagernet.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.paddingExceptBottom
import io.nekohasekai.sagernet.ktx.isExpert
import io.nekohasekai.sagernet.ui.MainViewModel
import io.nekohasekai.sagernet.ui.MainViewModelUiEvent
import io.nekohasekai.sagernet.ui.getStringOrRes
import kotlinx.coroutines.launch

private const val PAGE_NETWORK = 0
private const val PAGE_BACKUP = 1
private const val PAGE_DEBUG = 2

@Composable
fun ToolsScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    openDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }

    val isExpert = remember { isExpert }
    val pagerState = rememberPagerState(
        initialPage = PAGE_NETWORK,
        pageCount = { 2 + if (isExpert) 1 else 0 },
    )

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_tools)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.menu),
                        contentDescription = stringResource(R.string.menu),
                        onClick = openDrawer,
                    )
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding),
        ) {
            PrimaryTabRow(
                selectedTabIndex = PAGE_NETWORK,
            ) {
                Tab(
                    selected = pagerState.currentPage == PAGE_NETWORK,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(PAGE_NETWORK)
                        }
                    },
                    text = { Text(stringResource(R.string.tools_network)) },
                )
                Tab(
                    selected = pagerState.currentPage == PAGE_BACKUP,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(PAGE_BACKUP)
                        }
                    },
                    text = { Text(stringResource(R.string.backup)) },
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

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    PAGE_NETWORK -> NetworkScreen()
                    PAGE_BACKUP -> BackupScreen(
                        showSnackbar = { message ->
                            scope.launch {
                                snackbarState.showSnackbar(
                                    message = message,
                                    actionLabel = context.getString(android.R.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                    )
                    PAGE_DEBUG -> DebugScreen { message ->
                        scope.launch {
                            snackbarState.showSnackbar(
                                message = message,
                                actionLabel = context.getString(android.R.string.ok),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.uiEvent.collect { event ->
            when (event) {
                is MainViewModelUiEvent.Snackbar -> scope.launch {
                    snackbarState.showSnackbar(
                        message = context.getStringOrRes(event.message),
                        actionLabel = context.getString(android.R.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                is MainViewModelUiEvent.SnackbarWithAction -> scope.launch {
                    val result = snackbarState.showSnackbar(
                        message = context.getStringOrRes(event.message),
                        actionLabel = context.getStringOrRes(event.actionLabel),
                        duration = SnackbarDuration.Short,
                    )
                    event.callback(result)
                }

                else -> {}
            }
        }
    }
}