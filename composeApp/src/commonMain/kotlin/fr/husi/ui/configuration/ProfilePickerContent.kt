@file:OptIn(ExperimentalMaterial3Api::class)

package fr.husi.ui.configuration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.CapsuleSearchInputField
import fr.husi.compose.CapsuleSearchTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.PrimaryScrollableTabRow
import fr.husi.compose.material3.Tab
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.ktx.onIoDispatcher
import fr.husi.resources.Res
import fr.husi.resources.cancel
import fr.husi.resources.close
import fr.husi.resources.search
import fr.husi.resources.search_go
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Stable
class ProfilePickerState internal constructor(
    val viewModel: ConfigurationScreenViewModel,
    val uiState: ConfigurationUiState,
    val pagerState: PagerState,
    val snackbarState: SnackbarHostState,
    val preSelected: Long?,
    private val onSelectGroup: (Long) -> Unit,
) {
    val hasGroups: Boolean
        get() = uiState.groups.isNotEmpty()

    val searchTextFieldState: TextFieldState
        get() = viewModel.searchTextFieldState

    fun selectGroup(groupId: Long) {
        onSelectGroup(groupId)
    }
}

@Composable
fun rememberProfilePickerState(
    preSelected: Long?,
    viewModel: ConfigurationScreenViewModel = viewModel { ConfigurationScreenViewModel() },
): ProfilePickerState {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val snackbarState = remember { SnackbarHostState() }

    var selectedGroup by rememberSaveable { mutableLongStateOf(DataStore.selectedGroup) }
    val pagerState = rememberPagerState(
        initialPage = uiState.groups
            .indexOfFirst { it.id == selectedGroup }
            .fastCoerceIn(0, (uiState.groups.size - 1).fastCoerceAtLeast(0)),
        pageCount = { uiState.groups.size },
    )
    var isPageRestored by remember { mutableStateOf(false) }
    var lastPage by remember { mutableIntStateOf(pagerState.currentPage) }

    LaunchedEffect(preSelected) {
        val initialProfile = preSelected ?: DataStore.selectedProxy
        val initialGroup = onIoDispatcher {
            initialProfile.takeIf { it > 0 }?.let { ProfileManager.getProfile(it)?.groupId }
        }
        selectedGroup = initialGroup ?: DataStore.selectedGroup
        viewModel.scrollToProxy(initialProfile)
    }
    LaunchedEffect(selectedGroup, uiState.groups) {
        if (uiState.groups.isEmpty()) return@LaunchedEffect
        val index = uiState.groups.indexOfFirst { it.id == selectedGroup }
        if (index < 0) return@LaunchedEffect
        if (index != pagerState.currentPage) {
            pagerState.scrollToPage(index)
        }
        isPageRestored = true
    }
    LaunchedEffect(pagerState.currentPage, uiState.groups, isPageRestored) {
        if (uiState.groups.isEmpty() || pagerState.currentPage >= uiState.groups.size) {
            return@LaunchedEffect
        }
        val currentPage = pagerState.currentPage
        if (lastPage != currentPage) {
            viewModel.clearSearchQuery()
            focusManager.clearFocus()
            lastPage = currentPage
        }
        val groupId = uiState.groups[currentPage].id
        if (isPageRestored) {
            selectedGroup = groupId
        }
        viewModel.requestFocusIfNotHave(groupId)
    }

    return ProfilePickerState(
        viewModel = viewModel,
        uiState = uiState,
        pagerState = pagerState,
        snackbarState = snackbarState,
        preSelected = preSelected,
        onSelectGroup = { selectedGroup = it },
    )
}

@Composable
fun ProfilePickerContent(
    state: ProfilePickerState,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp,
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val searchBarState = rememberSearchBarState()
    val windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val searchInputField: @Composable () -> Unit = {
        CapsuleSearchInputField(
            textFieldState = state.searchTextFieldState,
            searchBarState = searchBarState,
            onSearch = { focusManager.clearFocus() },
            placeholder = { Text(stringResource(Res.string.search_go)) },
            leadingIcon = {
                Icon(vectorResource(Res.drawable.search), null)
            },
            trailingIcon = if (searchBarState.currentValue == SearchBarValue.Expanded) {
                {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.cancel),
                        onClick = {
                            state.viewModel.clearSearchQuery()
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                    )
                }
            } else {
                null
            },
        )
    }

    Column(modifier = modifier) {
        Column {
            CapsuleSearchTopBar(
                inputField = searchInputField,
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                        onClick = onDismiss,
                    )
                },
                windowInsets = windowInsets,
            )

            if (state.hasGroups && state.uiState.groups.size > 1) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = state.pagerState.currentPage.fastCoerceIn(
                        0,
                        state.uiState.groups.size - 1,
                    ),
                    edgePadding = 0.dp,
                ) {
                    state.uiState.groups.forEachIndexed { index, group ->
                        Tab(
                            text = { Text(group.displayName()) },
                            selected = state.pagerState.currentPage == index,
                            onClick = {
                                state.selectGroup(group.id)
                            },
                        )
                    }
                }
            }
        }

        ConfigurationContent(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            vm = state.viewModel,
            snackbarState = state.snackbarState,
            pagerState = state.pagerState,
            preSelected = state.preSelected,
            showActions = false,
            onProfileSelect = onSelected,
            bottomPadding = bottomPadding,
        )
    }

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = searchInputField,
    ) {
        ConfigurationContent(
            modifier = Modifier.fillMaxSize(),
            vm = state.viewModel,
            snackbarState = state.snackbarState,
            pagerState = state.pagerState,
            preSelected = state.preSelected,
            showActions = false,
            onProfileSelect = { id ->
                scope.launch { searchBarState.animateToCollapsed() }
                onSelected(id)
            },
            bottomPadding = 0.dp,
        )
    }
}
