package io.nekohasekai.sagernet.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun rememberScrollHideState(listState: LazyListState): State<Boolean> {
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index == layoutInfo.totalItemsCount - 1
        }
    }

    val visible = remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousScrollOffset = listState.firstVisibleItemScrollOffset
        var lastScrollingDown = false

        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collectLatest { (currentIndex, currentOffset) ->
                val scrollDelta = currentOffset - previousScrollOffset
                val isScrollingDown = when {
                    currentIndex > previousIndex -> true
                    currentIndex < previousIndex -> false
                    scrollDelta > 10 -> true
                    scrollDelta < -10 -> false
                    else -> lastScrollingDown
                }

                visible.value = !isScrollingDown || !isAtBottom

                lastScrollingDown = isScrollingDown
                previousIndex = currentIndex
                previousScrollOffset = currentOffset
            }
    }

    return visible
}

@Composable
fun rememberScrollHideState(scrollState: ScrollState): State<Boolean> {
    val visible = remember { mutableStateOf(true) }

    LaunchedEffect(scrollState) {
        var previousValue = scrollState.value
        var lastScrollingDown = false

        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .collectLatest { currentValue ->
                val scrollDelta = currentValue - previousValue
                val isScrollingDown = when {
                    scrollDelta > 10 -> true
                    scrollDelta < -10 -> false
                    else -> lastScrollingDown
                }

                visible.value = !isScrollingDown

                lastScrollingDown = isScrollingDown
                previousValue = currentValue
            }
    }

    return visible
}
