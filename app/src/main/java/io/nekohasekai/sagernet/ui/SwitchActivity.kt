package io.nekohasekai.sagernet.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

class SwitchActivity : ComposeActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                val sheetState = rememberStandardBottomSheetState(
                    initialValue = SheetValue.PartiallyExpanded,
                    confirmValueChange = {
                        if (it == SheetValue.Hidden) {
                            finish()
                            false
                        } else {
                            true
                        }
                    },
                )
                val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

                val density = LocalDensity.current
                val windowHeight = with(density) {
                    LocalWindowInfo.current.containerSize.height.toDp()
                }
                val statusBarHeight = with(density) {
                    WindowInsets.statusBars.getTop(this).toDp()
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

                val uiState by vm.uiState.collectAsStateWithLifecycle()
                val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle(DataStore.selectedGroup)
                val hasGroups = uiState.groups.isNotEmpty()
                val pagerState = rememberPagerState(
                    initialPage = uiState.groups
                        .indexOfFirst { it.id == selectedGroup }
                        .coerceIn(0, (uiState.groups.size - 1).coerceAtLeast(0)),
                    pageCount = { uiState.groups.size },
                )

                LaunchedEffect(selectedGroup, hasGroups) {
                    if (!hasGroups) return@LaunchedEffect
                    val index = uiState.groups.indexOfFirst { it.id == selectedGroup }
                    val target = index.coerceIn(0, pagerState.pageCount - 1)
                    if (target != pagerState.currentPage) {
                        pagerState.scrollToPage(target)
                    }
                }
                LaunchedEffect(pagerState.currentPage, hasGroups) {
                    if (!hasGroups || pagerState.currentPage >= uiState.groups.size) {
                        return@LaunchedEffect
                    }
                    val groupID = uiState.groups[pagerState.currentPage].id
                    DataStore.selectedGroup = groupID
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

                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = windowHeight * 0.6f,
                    containerColor = Color.Transparent,
                    sheetDragHandle = {
                        val topPadding = if (sheetState.currentValue == SheetValue.Expanded) {
                            statusBarHeight
                        } else {
                            0.dp
                        }
                        Box(modifier = Modifier.padding(top = topPadding)) {
                            BottomSheetDefaults.DragHandle()
                        }
                    },
                    sheetContent = {
                        ConfigurationContent(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(windowHeight),
                            vm = vm,
                            snackbarState = snackbarState,
                            pagerState = pagerState,
                            appBarContainerColor = appBarContainerColor,
                            preSelected = null,
                            selectCallback = ::returnProfile,
                        )
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = ::finish,
                            ),
                    )
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
