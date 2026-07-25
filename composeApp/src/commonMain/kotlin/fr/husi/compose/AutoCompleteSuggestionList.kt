package fr.husi.compose

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> AutoCompleteSuggestionList(
    suggestions: List<T>,
    selectedIndex: Int,
    onChooseSuggestion: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in suggestions.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.heightIn(max = 200.dp),
    ) {
        itemsIndexed(suggestions) { index, suggestion ->
            DropdownMenuItem(
                selected = index == selectedIndex,
                text = { itemContent(suggestion) },
                onClick = { onChooseSuggestion(suggestion) },
                shapes = MenuDefaults.itemShape(index, suggestions.size),
                colors = MenuDefaults.selectableItemColors(),
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
        }
    }
}
