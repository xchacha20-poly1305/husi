package fr.husi.ui.tailscale

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.CapsuleActionsScope
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.ProvidePreferenceLocals
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.fadingEdge
import fr.husi.compose.material3.Text
import fr.husi.compose.withNavigation
import fr.husi.resources.Res
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.tailscale.TailscaleUiEvent
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.StringOrRes
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun CollectTailscaleEvents(
    events: SharedFlow<TailscaleUiEvent>,
    closed: SharedFlow<Unit>,
    onBackPress: () -> Unit,
) {
    val snackbar = LocalSnackbarEmitter.current
    val back = rememberUpdatedState(onBackPress)
    LaunchedEffect(events, closed) {
        launch {
            closed.collect { back.value() }
        }
        launch {
            events.collect { event ->
                when (event) {
                    is TailscaleUiEvent.Message -> snackbar.show(StringOrRes.Direct(event.message))
                }
            }
        }
    }
}

@Composable
internal fun TailscaleScreenScaffold(
    title: String,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable CapsuleActionsScope.() -> Unit = {},
    contentModifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = rememberHazeState()
    val windowInsets = WindowInsets.safeDrawing
    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                hazeState = hazeState,
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                title = { Text(title) },
                actions = actions,
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        ProvidePreferenceLocals {
            val contentPadding = innerPadding.withNavigation()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(contentModifier)
                    .hazeSource(hazeState),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .fadingEdge(listState),
                    contentPadding = contentPadding,
                    content = content,
                )
                BoxedVerticalScrollbar(
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
                )
            }
        }
    }
}
