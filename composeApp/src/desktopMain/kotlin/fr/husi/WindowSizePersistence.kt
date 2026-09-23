package fr.husi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.v2.WindowState
import fr.husi.database.DataStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val DEFAULT_WINDOW_SIZE = DpSize(1200.dp, 800.dp)

internal fun initialWindowSize(): DpSize {
    if (!DataStore.rememberWindowSize.getBlocking()) {
        return DEFAULT_WINDOW_SIZE
    }
    val width = DataStore.windowWidth.getBlocking()
    val height = DataStore.windowHeight.getBlocking()
    if (width <= 0 || height <= 0) {
        return DEFAULT_WINDOW_SIZE
    }
    return DpSize(width.dp, height.dp)
}

@OptIn(ExperimentalComposeUiApi::class, FlowPreview::class)
@Composable
internal fun RecordWindowSizeEffect(windowState: WindowState) {
    LaunchedEffect(windowState) {
        val floatingSize = snapshotFlow {
            val isFloating = windowState.isInitialized
                    && windowState.placement == WindowPlacement.Floating
                    && !windowState.isMinimized
            if (isFloating) {
                windowState.size
            } else {
                null
            }
        }.filterNotNull()

        combine(DataStore.rememberWindowSize.flow(), floatingSize) { enabled, size ->
            size.takeIf { enabled }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .debounce(500.milliseconds)
            .collect { size ->
                DataStore.windowWidth.set(size.width.value.roundToInt())
                DataStore.windowHeight.set(size.height.value.roundToInt())
            }
    }
}
