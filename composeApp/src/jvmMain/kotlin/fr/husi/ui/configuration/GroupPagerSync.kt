package fr.husi.ui.configuration

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import fr.husi.database.ProxyGroup

/**
 * Two-way binding between [pagerState] and a stored group selection.
 *
 * Both directions use [PagerState.settledPage], never `currentPage`. An animated jump passes
 * through intermediate pages; reporting those would write a selection that arrives back after
 * the pager has moved on, and the restore direction would then pull the pager back to it.
 */
@Composable
internal fun GroupPagerSelectionSync(
    pagerState: PagerState,
    groups: List<ProxyGroup>,
    selectedGroup: Long,
    onSettledGroupChange: suspend (Long) -> Unit,
    onPageChange: () -> Unit,
) {
    val currentOnSettledGroupChange by rememberUpdatedState(onSettledGroupChange)
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    var isPageRestored by remember { mutableStateOf(false) }
    var lastSettledPage by remember { mutableIntStateOf(pagerState.settledPage) }

    LaunchedEffect(selectedGroup, groups) {
        val index = groups.indexOfFirst { it.id == selectedGroup }
        if (index < 0) return@LaunchedEffect
        if (index != pagerState.settledPage) {
            pagerState.scrollToPage(index)
        }
        isPageRestored = true
    }

    LaunchedEffect(pagerState.settledPage, groups, isPageRestored) {
        val settledPage = pagerState.settledPage
        val group = groups.getOrNull(settledPage) ?: return@LaunchedEffect
        if (lastSettledPage != settledPage) {
            currentOnPageChange()
            lastSettledPage = settledPage
        }
        if (isPageRestored) {
            currentOnSettledGroupChange(group.id)
        }
    }
}
