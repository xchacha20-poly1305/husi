package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.view.GravityCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.HideOnBottomScrollBehavior
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.ansiEscape
import io.nekohasekai.sagernet.compose.paddingWithNavigation
import io.nekohasekai.sagernet.compose.showAndDismissOld
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.databinding.ComposeHolderBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.utils.SendLog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class LogcatFragment : OnKeyDownFragment(R.layout.compose_holder) {

    private val viewModel: LogcatFragmentViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as MainActivity
        val binding = ComposeHolderBinding.bind(view)
        binding.root.setContent {
            AppTheme {
                LogcatScreen(
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
private fun LogcatScreen(
    modifier: Modifier = Modifier,
    viewModel: LogcatFragmentViewModel,
    openDrawer: () -> Unit,
    fab: FloatingActionButton,
    bottomBar: BottomAppBar,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val snackbarState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    HideOnBottomScrollBehavior(listState, fab, bottomBar)
    val density = LocalDensity.current
    var bottomBarHeightDp by remember { mutableStateOf(0.dp) }
    DisposableEffect(bottomBar) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            bottomBarHeightDp = with(density) { bottomBar.height.toDp() }
        }
        bottomBar.viewTreeObserver.addOnGlobalLayoutListener(listener)
        bottomBarHeightDp = with(density) { bottomBar.height.toDp() }
        onDispose {
            bottomBar.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.logs.size) {
        if (!uiState.pinScroll && uiState.logs.isNotEmpty()) {
            listState.scrollToItem(uiState.logs.size - 1)
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarState.showAndDismissOld(
                message = message,
                actionLabel = context.getString(android.R.string.ok),
                duration = SnackbarDuration.Short,
            )
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_log)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.menu),
                        contentDescription = stringResource(R.string.menu),
                        onClick = openDrawer,
                    )
                },
                actions = {
                    SimpleIconButton(
                        imageVector = if (uiState.pinScroll) {
                            ImageVector.vectorResource(R.drawable.sailing)
                        } else {
                            ImageVector.vectorResource(R.drawable.push_pin)
                        },
                        contentDescription = stringResource(R.string.pin_log),
                        onClick = { viewModel.togglePinScroll() },
                    )
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.send),
                        contentDescription = stringResource(R.string.logcat),
                    ) {
                        scope.launch {
                            try {
                                SendLog.sendLog(context, "husi")
                            } catch (e: Exception) {
                                Logs.e(e)
                                snackbarState.showAndDismissOld(
                                    message = e.readableMessage,
                                    actionLabel = context.getString(android.R.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    }
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.delete_sweep),
                        contentDescription = stringResource(R.string.clear_logcat),
                        onClick = { viewModel.clearLog() },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarState,
                modifier = Modifier.padding(bottom = bottomBarHeightDp),
            )
        },
    ) { innerPadding ->
        val basePadding = innerPadding.paddingWithNavigation()
        val layoutDirection = LocalLayoutDirection.current
        SelectionContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                state = listState,
                contentPadding = PaddingValues(
                    start = basePadding.calculateStartPadding(layoutDirection),
                    top = basePadding.calculateTopPadding(),
                    end = basePadding.calculateEndPadding(layoutDirection),
                    bottom = basePadding.calculateBottomPadding() + bottomBarHeightDp,
                ),
            ) {
                itemsIndexed(
                    items = uiState.logs,
                    key = { index, _ -> index },
                    contentType = { _, _ -> 0 },
                ) { _, logLine ->
                    LogCard(logLine = logLine)
                }
            }
        }
    }

}

@Composable
private fun LogCard(
    modifier: Modifier = Modifier,
    logLine: String,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = logLine.ansiEscape(),
            modifier = Modifier.padding(12.dp),
        )
    }
}