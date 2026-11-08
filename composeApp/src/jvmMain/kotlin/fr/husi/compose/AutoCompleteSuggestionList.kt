package fr.husi.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

private val SuggestionListMaxHeight = 200.dp

/**
 * Shows [suggestions] as a scrollable list, keeping the item at [selectedIndex] visible.
 *
 * This deliberately uses a plain [Column] instead of a lazy list. Dropdown menus measure their
 * content with intrinsic measurements (`Modifier.width(IntrinsicSize.Max)`), which lazy layouts do
 * not support and crash on. Suggestion lists are short enough to be composed eagerly.
 */
@Composable
fun <T> AutoCompleteSuggestionList(
    suggestions: List<T>,
    selectedIndex: Int,
    onChooseSuggestion: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    val scrollState = rememberScrollState()
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    // Item index to its measured height, used to locate the selected item in the scrolled content.
    val itemHeights = remember(suggestions) { mutableStateMapOf<Int, Int>() }
    var viewportHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(scrollState) {
        snapshotFlow {
            val index = currentSelectedIndex
            val height = itemHeights[index]
            if (height == null || viewportHeight <= 0) return@snapshotFlow null
            val top = (0 until index).sumOf { itemHeights[it] ?: 0 }
            top to height
        }.filterNotNull().distinctUntilChanged().collect { (top, height) ->
            val offset = scrollState.value
            val target = when {
                top < offset -> top
                top + height > offset + viewportHeight -> top + height - viewportHeight
                else -> return@collect
            }
            scrollState.animateScrollTo(target.coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .heightIn(max = SuggestionListMaxHeight)
            .onSizeChanged { viewportHeight = it.height }
            .verticalScroll(scrollState),
    ) {
        for ((index, suggestion) in suggestions.withIndex()) {
            DropdownMenuItem(
                selected = index == selectedIndex,
                text = { itemContent(suggestion) },
                onClick = { onChooseSuggestion(suggestion) },
                modifier = Modifier.onSizeChanged { itemHeights[index] = it.height },
                shapes = MenuDefaults.itemShape(index, suggestions.size),
                colors = MenuDefaults.selectableItemColors(),
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
        }
    }
}
