@file:OptIn(ExperimentalLayoutApi::class)

package io.nekohasekai.sagernet.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.ui.configuration.ConfigurationContent
import io.nekohasekai.sagernet.ui.configuration.ConfigurationScreenViewModel
import kotlinx.coroutines.launch

class SwitchActivity : ComposeActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                val sheetState = rememberModalBottomSheetState()

                val density = LocalDensity.current
                val windowHeight = with(density) {
                    LocalWindowInfo.current.containerSize.height.toDp()
                }

                val vm: ConfigurationScreenViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ConfigurationScreenViewModel(::returnProfile) as T
                        }
                    },
                )
                val snackbarState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                val uiState by vm.uiState.collectAsStateWithLifecycle()
                val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle(DataStore.selectedGroup)
                val hasGroups = uiState.groups.isNotEmpty()
                val pagerState = rememberPagerState(
                    initialPage = uiState.groups
                        .indexOfFirst { it.id == selectedGroup }
                        .coerceIn(0, (uiState.groups.size - 1).coerceAtLeast(0)),
                    pageCount = { uiState.groups.size },
                )
                var isPageRestored by remember { mutableStateOf(false) }

                LaunchedEffect(selectedGroup, hasGroups) {
                    if (!hasGroups) return@LaunchedEffect
                    val index = uiState.groups.indexOfFirst { it.id == selectedGroup }
                    val target = index.coerceIn(0, pagerState.pageCount - 1)
                    if (target != pagerState.currentPage) {
                        pagerState.scrollToPage(target)
                    }
                    isPageRestored = true
                }
                LaunchedEffect(pagerState.currentPage, hasGroups, isPageRestored) {
                    if (!hasGroups || pagerState.currentPage >= uiState.groups.size) {
                        return@LaunchedEffect
                    }
                    val groupID = uiState.groups[pagerState.currentPage].id
                    if (isPageRestored) {
                        DataStore.selectedGroup = groupID
                    }
                    vm.requestFocusIfNotHave(groupID)
                }

                val topAppBarColors = TopAppBarDefaults.topAppBarColors()
                val appBarContainerColor by animateColorAsState(
                    targetValue = lerp(
                        topAppBarColors.containerColor,
                        topAppBarColors.scrolledContainerColor,
                        0f,
                    ),
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "appBarContainerColor",
                )

                LaunchedEffect(Unit) {
                    vm.scrollToProxy(DataStore.selectedProxy)
                }

                ModalBottomSheet(
                    onDismissRequest = ::finish,
                    sheetState = sheetState,
                    scrimColor = Color.Black.copy(alpha = 0.5f),
                ) {
                    val bottomPadding = WindowInsets.navigationBarsIgnoringVisibility
                        .asPaddingValues()
                        .calculateBottomPadding()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(windowHeight * 0.6f),
                    ) {
                        if (hasGroups && uiState.groups.size > 1) PrimaryScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage.coerceIn(0, uiState.groups.size - 1),
                            edgePadding = 0.dp,
                            containerColor = appBarContainerColor,
                        ) {
                            uiState.groups.forEachIndexed { index, group ->
                                Tab(
                                    text = { Text(group.displayName()) },
                                    selected = pagerState.currentPage == index,
                                    onClick = {
                                        vm.requestFocusIfNotHave(group.id)
                                        scope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    },
                                )
                            }
                        }

                        ConfigurationContent(
                            modifier = Modifier.fillMaxSize(),
                            vm = vm,
                            snackbarState = snackbarState,
                            pagerState = pagerState,
                            preSelected = null,
                            selectCallback = ::returnProfile,
                            bottomPadding = bottomPadding,
                        )
                    }
                }
            }
        }
    }

    private fun returnProfile(profileId: Long) {
        DataStore.selectedProxy = profileId
        repo.reloadService()
        finish()
    }
}
