package io.nekohasekai.sagernet.compose

import android.view.View
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HideOnBottomScrollBehavior(
    listState: LazyListState,
    fab: FloatingActionButton,
) {
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index == layoutInfo.totalItemsCount - 1
        }
    }

    DisposableEffect(fab) {
        if (!fab.isVisible) {
            fab.show()
        }
        onDispose {
            if (!fab.isVisible) {
                fab.show()
            }
        }
    }

    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousScrollOffset = listState.firstVisibleItemScrollOffset

        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collectLatest { (currentIndex, currentOffset) ->
                val isScrollingDown = if (currentIndex != previousIndex) {
                    currentIndex > previousIndex
                } else {
                    currentOffset > previousScrollOffset
                }

                when {
                    !isScrollingDown -> {
                        if (!fab.isVisible) {
                            fab.show()
                        }
                    }
                    isAtBottom && isScrollingDown -> {
                        fab.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                            override fun onHidden(button: FloatingActionButton) {
                                super.onHidden(button)
                                button.visibility = View.INVISIBLE
                            }
                        })
                    }
                }

                previousIndex = currentIndex
                previousScrollOffset = currentOffset
            }
    }
}
