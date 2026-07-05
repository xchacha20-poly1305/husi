@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui.configuration

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarDuration
import fr.husi.compose.SwipeableSnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.compose.CapsuleActionButton
import fr.husi.compose.CapsuleSearchInputField
import fr.husi.compose.CapsuleSearchTopBar
import fr.husi.compose.ExpandableDropdownMenuItem
import fr.husi.compose.PlatformMenuIcon
import fr.husi.compose.QRCodeDialog
import fr.husi.compose.SagerFab
import fr.husi.compose.ScrollableDialog
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.StatsBar
import fr.husi.compose.TextButton
import fr.husi.compose.colorForUrlTestDelay
import fr.husi.compose.getPlainText
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.PrimaryScrollableTabRow
import fr.husi.compose.material3.Tab
import fr.husi.compose.material3.Text
import fr.husi.compose.paddingExceptBottom
import fr.husi.database.DataStore
import fr.husi.database.ProxyEntity
import fr.husi.database.displayType
import fr.husi.keyevent.isTypeControlPressed
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.ktx.showAndDismissOld
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.action_anytls
import fr.husi.resources.action_direct
import fr.husi.resources.action_http
import fr.husi.resources.action_hysteria
import fr.husi.resources.action_import
import fr.husi.resources.action_import_file
import fr.husi.resources.action_juicity
import fr.husi.resources.action_mieru
import fr.husi.resources.action_naive
import fr.husi.resources.action_shadowquic
import fr.husi.resources.action_shadowsocks
import fr.husi.resources.action_shadowtls
import fr.husi.resources.action_snell
import fr.husi.resources.action_socks
import fr.husi.resources.action_ssh
import fr.husi.resources.action_trojan
import fr.husi.resources.action_trusttunnel
import fr.husi.resources.action_tuic
import fr.husi.resources.action_vless
import fr.husi.resources.action_vmess
import fr.husi.resources.action_wireguard
import fr.husi.resources.add_profile
import fr.husi.resources.add_profile_methods_manual_settings
import fr.husi.resources.apply
import fr.husi.resources.cancel
import fr.husi.resources.clear_traffic_statistics
import fr.husi.resources.close
import fr.husi.resources.connection_test
import fr.husi.resources.connection_test_clear_results
import fr.husi.resources.connection_test_delete_unavailable
import fr.husi.resources.connection_test_domain_not_found
import fr.husi.resources.connection_test_error
import fr.husi.resources.connection_test_icmp_ping
import fr.husi.resources.connection_test_icmp_ping_unavailable
import fr.husi.resources.connection_test_refused
import fr.husi.resources.connection_test_tcp_ping
import fr.husi.resources.connection_test_tcp_ping_unavailable
import fr.husi.resources.connection_test_timeout
import fr.husi.resources.connection_test_unreachable
import fr.husi.resources.connection_test_url_test
import fr.husi.resources.copy_success
import fr.husi.resources.custom_config
import fr.husi.resources.delete_confirm_prompt
import fr.husi.resources.ecg
import fr.husi.resources.group_order_by_delay
import fr.husi.resources.group_order_by_name
import fr.husi.resources.group_order_origin
import fr.husi.resources.menu
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.need_reload
import fr.husi.resources.no_proxies_found_in_file
import fr.husi.resources.note_add
import fr.husi.resources.ok
import fr.husi.resources.plugin_unknown
import fr.husi.resources.proxy_chain
import fr.husi.resources.proxy_set
import fr.husi.resources.remove_duplicate
import fr.husi.resources.removed
import fr.husi.resources.search
import fr.husi.resources.search_go
import fr.husi.resources.sort_mode
import fr.husi.resources.undo
import fr.husi.ui.MainViewModel
import fr.husi.ui.MainViewModelAlertDialog
import fr.husi.ui.MainViewModelUiEvent
import fr.husi.ui.NavRoutes
import fr.husi.ui.getStringOrRes
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.reflect.KClass

@Composable
fun ConfigurationScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    onNavigationClick: () -> Unit,
    vm: ConfigurationScreenViewModel = viewModel { ConfigurationScreenViewModel() },
    onOpenProfileEditor: ((NavRoutes.ProfileEditor) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    var scrollHideVisible by remember { mutableStateOf(true) }
    var fabHeight by remember { mutableIntStateOf(0) }
    var showAlertDialog by remember { mutableStateOf<MainViewModelUiEvent.AlertDialog?>(null) }
    val clipboard = LocalClipboard.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current

    val importFile = rememberFilePickerLauncher { file ->
        if (file != null) {
            vm.importFile(
                file = file,
                onProxiesFound = { proxies ->
                    runOnIoDispatcher {
                        mainViewModel.importProfile(proxies)
                    }
                },
                onSubscriptionFound = { uri ->
                    mainViewModel.importSubscription(uri)
                },
                onNoProxies = {
                    scope.launch {
                        snackbarState.showSnackbar(
                            message = getString(Res.string.no_proxies_found_in_file),
                            actionLabel = getString(Res.string.ok),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
                onError = { message ->
                    scope.launch {
                        snackbarState.showSnackbar(
                            message = message,
                            actionLabel = getString(Res.string.ok),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            )
        }
    }

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val hasGroups = uiState.groups.isNotEmpty()
    val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle(DataStore.selectedGroup)
    val pagerState = rememberPagerState(
        initialPage = uiState.groups
            .indexOfFirst { it.id == selectedGroup }
            .fastCoerceIn(0, (uiState.groups.size - 1).fastCoerceAtLeast(0)),
        pageCount = { uiState.groups.size },
    )
    var isPageRestored by remember { mutableStateOf(false) }
    var lastPage by remember { mutableIntStateOf(pagerState.currentPage) }
    LaunchedEffect(selectedGroup, hasGroups, uiState.groups) {
        if (!hasGroups) return@LaunchedEffect
        val index = uiState.groups.indexOfFirst { it.id == selectedGroup }
        if (index < 0) return@LaunchedEffect
        if (index != pagerState.currentPage) {
            pagerState.scrollToPage(index)
        }
        isPageRestored = true
    }
    LaunchedEffect(pagerState.currentPage, hasGroups, isPageRestored) {
        if (!hasGroups || pagerState.currentPage >= uiState.groups.size) {
            return@LaunchedEffect
        }
        val currentPage = pagerState.currentPage
        if (lastPage != currentPage) {
            vm.clearSearchQuery()
            focusManager.clearFocus()
            lastPage = currentPage
        }
        val groupID = uiState.groups[currentPage].id
        if (isPageRestored) {
            DataStore.selectedGroup = groupID
        }
        vm.requestFocusIfNotHave(groupID)
        scrollHideVisible = true
    }

    var showAddMenu by remember { mutableStateOf(false) }
    var showAddManualMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showConnectionTestMenu by remember { mutableStateOf(false) }
    var showOrderMenu by remember { mutableStateOf(false) }
    val searchBarState = rememberSearchBarState()
    val searchTextFieldState = vm.searchTextFieldState
    val searchInputField: @Composable () -> Unit = {
        CapsuleSearchInputField(
            textFieldState = searchTextFieldState,
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
                            vm.clearSearchQuery()
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                    )
                }
            } else {
                null
            },
        )
    }

    val currentOrder =
        if (pagerState.pageCount > 0 && pagerState.currentPage < uiState.groups.size) {
            uiState.groups[pagerState.currentPage].order
        } else {
            0
        }

    val windowInsets = WindowInsets.safeDrawing

    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val topAppBarColors = TopAppBarDefaults.topAppBarColors()
    val appBarContainerColor by animateColorAsState(
        targetValue = lerp(
            topAppBarColors.containerColor,
            topAppBarColors.scrolledContainerColor,
            scrollBehavior.state.overlappedFraction.fastCoerceIn(0f, 1f),
        ),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "appBarContainerColor",
    )

    LaunchedEffect(Unit) {
        vm.scrollToProxy(DataStore.selectedProxy)
    }

    val manualProfileEntries = remember {
        listOf(
            Res.string.action_socks to ProxyEntity.TYPE_SOCKS,
            Res.string.action_http to ProxyEntity.TYPE_HTTP,
            Res.string.action_shadowsocks to ProxyEntity.TYPE_SS,
            Res.string.action_vmess to ProxyEntity.TYPE_VMESS,
            Res.string.action_vless to ProxyEntity.TYPE_VLESS,
            Res.string.action_trojan to ProxyEntity.TYPE_TROJAN,
            Res.string.action_mieru to ProxyEntity.TYPE_MIERU,
            Res.string.action_naive to ProxyEntity.TYPE_NAIVE,
            Res.string.action_hysteria to ProxyEntity.TYPE_HYSTERIA,
            Res.string.action_tuic to ProxyEntity.TYPE_TUIC,
            Res.string.action_juicity to ProxyEntity.TYPE_JUICITY,
            Res.string.action_direct to ProxyEntity.TYPE_DIRECT,
            Res.string.action_ssh to ProxyEntity.TYPE_SSH,
            Res.string.action_wireguard to ProxyEntity.TYPE_WG,
            Res.string.action_shadowtls to ProxyEntity.TYPE_SHADOWTLS,
            Res.string.action_anytls to ProxyEntity.TYPE_ANYTLS,
            Res.string.action_shadowquic to ProxyEntity.TYPE_SHADOWQUIC,
            Res.string.action_trusttunnel to ProxyEntity.TYPE_TRUST_TUNNEL,
            Res.string.action_snell to ProxyEntity.TYPE_SNELL,
            Res.string.proxy_set to ProxyEntity.TYPE_PROXY_SET,
            Res.string.custom_config to ProxyEntity.TYPE_CONFIG,
            Res.string.proxy_chain to ProxyEntity.TYPE_CHAIN,
        )
    }

    fun openProfileEditor(type: Int, id: Long = -1L, isSubscription: Boolean = false) {
        showAddManualMenu = false
        onOpenProfileEditor?.invoke(
            NavRoutes.ProfileEditor(
                type = type,
                id = id,
                subscription = isSubscription,
            ),
        )
    }

    fun importFromClipboard() {
        lifecycleOwner.lifecycleScope.launch {
            val text = clipboard.getPlainText()
            mainViewModel.parseProxy(text)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                if (!keyEvent.isTypeControlPressed || keyEvent.key != Key.V) {
                    return@onPreviewKeyEvent false
                }
                importFromClipboard()
                true
            },
        topBar = {
            Surface(color = appBarContainerColor) {
                Column {
                    CapsuleSearchTopBar(
                        inputField = searchInputField,
                        navigationIcon = PlatformMenuIcon(
                            imageVector = vectorResource(Res.drawable.menu),
                            contentDescription = stringResource(Res.string.menu),
                            onClick = onNavigationClick,
                        ),
                        actions = {
                        CapsuleActionButton {
                            Box {
                                SimpleIconButton(
                                    imageVector = vectorResource(Res.drawable.note_add),
                                    contentDescription = stringResource(Res.string.add_profile),
                                    onClick = { showAddMenu = true },
                                )
                                DropdownMenu(
                                    expanded = showAddMenu,
                                    onDismissRequest = { showAddMenu = false },
                                    containerColor = MenuDefaults.groupStandardContainerColor,
                                    shape = MenuDefaults.standaloneGroupShape,
                                ) {
                                    ScannerDropdownMenuItem()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.action_import)) },
                                        onClick = {
                                            showAddMenu = false
                                            importFromClipboard()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.action_import_file)) },
                                        onClick = {
                                            showAddMenu = false
                                            importFile.launch()
                                        },
                                    )
                                    ExpandableDropdownMenuItem(
                                        text = stringResource(Res.string.add_profile_methods_manual_settings),
                                        onClick = {
                                            showAddMenu = false
                                            showAddManualMenu = true
                                        },
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAddManualMenu,
                                    onDismissRequest = { showAddManualMenu = false },
                                    containerColor = MenuDefaults.groupStandardContainerColor,
                                    shape = MenuDefaults.standaloneGroupShape,
                                ) {
                                    manualProfileEntries.forEach { (title, type) ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(title)) },
                                            onClick = {
                                                openProfileEditor(type)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        CapsuleActionButton {
                            Box {
                                SimpleIconButton(
                                    imageVector = vectorResource(Res.drawable.more_vert),
                                    contentDescription = stringResource(Res.string.more),
                                    onClick = { showOverflowMenu = true },
                                )
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    containerColor = MenuDefaults.groupStandardContainerColor,
                                    shape = MenuDefaults.standaloneGroupShape,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.clear_traffic_statistics)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            vm.clearTrafficStatistics(DataStore.selectedGroup)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.remove_duplicate)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            vm.removeDuplicate(DataStore.selectedGroup)
                                        },
                                    )
                                    ExpandableDropdownMenuItem(stringResource(Res.string.connection_test)) {
                                        showOverflowMenu = false
                                        showConnectionTestMenu = true
                                    }
                                    ExpandableDropdownMenuItem(stringResource(Res.string.sort_mode)) {
                                        showOverflowMenu = false
                                        showOrderMenu = true
                                    }
                                }
                                DropdownMenu(
                                    expanded = showConnectionTestMenu,
                                    onDismissRequest = { showConnectionTestMenu = false },
                                    containerColor = MenuDefaults.groupStandardContainerColor,
                                    shape = MenuDefaults.standaloneGroupShape,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.connection_test_icmp_ping)) },
                                        onClick = {
                                            showConnectionTestMenu = false
                                            vm.doTest(
                                                DataStore.currentGroupId(),
                                                TestType.ICMPPing,
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.connection_test_tcp_ping)) },
                                        onClick = {
                                            showConnectionTestMenu = false
                                            vm.doTest(
                                                DataStore.currentGroupId(),
                                                TestType.TCPPing,
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.connection_test_url_test)) },
                                        onClick = {
                                            showConnectionTestMenu = false
                                            vm.doTest(
                                                DataStore.currentGroupId(),
                                                TestType.URLTest,
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.connection_test_delete_unavailable)) },
                                        onClick = {
                                            showConnectionTestMenu = false
                                            vm.deleteUnavailable(DataStore.selectedGroup)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.connection_test_clear_results)) },
                                        onClick = {
                                            showConnectionTestMenu = false
                                            vm.clearResults(DataStore.selectedGroup)
                                        },
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOrderMenu,
                                    onDismissRequest = { showOrderMenu = false },
                                    containerColor = MenuDefaults.groupStandardContainerColor,
                                    shape = MenuDefaults.standaloneGroupShape,
                                ) {
                                    val orders = listOf(
                                        stringResource(Res.string.group_order_origin),
                                        stringResource(Res.string.group_order_by_name),
                                        stringResource(Res.string.group_order_by_delay),
                                    )
                                    orders.forEachIndexed { i, option ->
                                        DropdownMenuItem(
                                            selected = currentOrder == i,
                                            onClick = {
                                                showOrderMenu = false
                                                vm.updateOrder(DataStore.selectedGroup, i)
                                            },
                                            text = { Text(text = option) },
                                            shapes = MenuDefaults.itemShape(i, orders.size),
                                        )
                                    }
                                }
                            }
                        }
                    },
                    windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    scrollBehavior = scrollBehavior,
                )

                if (hasGroups && uiState.groups.size > 1) PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage.fastCoerceIn(
                        0,
                        uiState.groups.size - 1,
                    ),
                    edgePadding = 0.dp,
                    containerColor = appBarContainerColor,
                ) {
                    uiState.groups.forEachIndexed { index, group ->
                        Tab(
                            text = { Text(group.displayName()) },
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                        )
                    }
                }
            }
            }
        },
        snackbarHost = { SwipeableSnackbarHost(snackbarState) },
        floatingActionButton = {
            SagerFab(
                visible = scrollHideVisible,
                state = serviceStatus.state,
                showSnackbar = { message ->
                    scope.launch {
                        snackbarState.showSnackbar(
                            message = getStringOrRes(message),
                            actionLabel = resolveRepository().getString(Res.string.ok),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
                onSizeChanged = { fabHeight = it },
            )
        },
        bottomBar = {
            if (serviceStatus.state == ServiceState.Connected) {
                StatsBar(
                    status = serviceStatus,
                    visible = scrollHideVisible,
                    mainViewModel = mainViewModel,
                )
            }
        },
    ) { innerPadding ->
        val density = LocalDensity.current
        val bottomPadding =
            innerPadding.calculateBottomPadding() + with(density) { fabHeight.toDp() }
        ConfigurationContent(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding),
            vm = vm,
            snackbarState = snackbarState,
            pagerState = pagerState,
            preSelected = null,
            showActions = true,
            onProfileSelect = vm::onProfileSelect,
            bottomPadding = bottomPadding,
            onScrollHideChange = { scrollHideVisible = it },
            onOpenProfileEditor = onOpenProfileEditor,
        )
    }

    ConfigurationDialogs(
        vm = vm,
        uiState = uiState,
    )

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = searchInputField,
    ) {
        val currentGroup = uiState.groups.getOrNull(pagerState.currentPage)
        val childVm = currentGroup?.let { vm.childViewModels[it.id] }
        if (childVm != null) {
            val expandedScope = rememberCoroutineScope()
            val expandedSnackbarState = remember { SnackbarHostState() }
            Box(modifier = Modifier.fillMaxSize()) {
                GroupHolderScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = childVm,
                    showActions = true,
                    bottomPadding = 0.dp,
                    onProfileSelect = { id ->
                        vm.onProfileSelect(id)
                        expandedScope.launch { searchBarState.animateToCollapsed() }
                    },
                    onOpenProfileEditor = onOpenProfileEditor?.let { callback ->
                        { route ->
                            expandedScope.launch { searchBarState.animateToCollapsed() }
                            callback(route)
                        }
                    },
                    needReload = {
                        expandedScope.launch {
                            if (!DataStore.serviceState.started) return@launch
                            val result = expandedSnackbarState.showSnackbar(
                                message = resolveRepository().getString(Res.string.need_reload),
                                actionLabel = resolveRepository().getString(Res.string.apply),
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                resolveRepository().reloadService()
                            }
                        }
                    },
                    showQR = { name, url ->
                        expandedScope.launch { searchBarState.animateToCollapsed() }
                        // QR dialog will be shown in the parent composition
                    },
                    onCopySuccess = {
                        expandedScope.launch {
                            expandedSnackbarState.showSnackbar(
                                message = resolveRepository().getString(Res.string.copy_success),
                                actionLabel = resolveRepository().getString(Res.string.ok),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    showSnackbar = { message ->
                        expandedScope.launch {
                            expandedSnackbarState.showSnackbar(
                                message = getStringOrRes(message),
                                actionLabel = resolveRepository().getString(Res.string.ok),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    showUndoSnackbar = { count, onUndo ->
                        expandedScope.launch {
                            val result = expandedSnackbarState.showAndDismissOld(
                                message = resolveRepository().getPluralString(
                                    Res.plurals.removed,
                                    count,
                                    count,
                                ),
                                actionLabel = resolveRepository().getString(Res.string.undo),
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                onUndo()
                            }
                        }
                    },
                )
                SwipeableSnackbarHost(
                    expandedSnackbarState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.uiEvent.collect { event ->
            when (event) {
                is MainViewModelUiEvent.Snackbar -> scope.launch {
                    snackbarState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = resolveRepository().getString(Res.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                is MainViewModelUiEvent.SnackbarWithAction -> scope.launch {
                    val result = snackbarState.showSnackbar(
                        message = getStringOrRes(event.message),
                        actionLabel = getStringOrRes(event.actionLabel),
                        duration = SnackbarDuration.Short,
                    )
                    event.callback(result)
                }

                is MainViewModelUiEvent.AlertDialog -> showAlertDialog = event
            }
        }
    }

    showAlertDialog?.let { dialog ->
        MainViewModelAlertDialog(dialog) {
            showAlertDialog = null
        }
    }
}

@Composable
fun ConfigurationContent(
    modifier: Modifier = Modifier,
    vm: ConfigurationScreenViewModel,
    snackbarState: SnackbarHostState,
    pagerState: androidx.compose.foundation.pager.PagerState,
    preSelected: Long?,
    showActions: Boolean,
    onProfileSelect: (Long) -> Unit,
    bottomPadding: Dp,
    onOpenProfileEditor: ((NavRoutes.ProfileEditor) -> Unit)? = null,
    onScrollHideChange: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val hasGroups = uiState.groups.isNotEmpty()
    var qrCodeInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(modifier = modifier) {
        if (hasGroups) {
            val parentLifecycleOwner = LocalLifecycleOwner.current
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                val group = uiState.groups[page]
                val pageLifecycleOwner = rememberLifecycleOwner(
                    maxLifecycle = if (pagerState.currentPage == page) {
                        Lifecycle.State.RESUMED
                    } else {
                        Lifecycle.State.CREATED
                    },
                    parent = parentLifecycleOwner,
                )
                val pageViewModel = viewModel<GroupProfilesHolderViewModel>(
                    key = "group-holder-${group.id}",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(
                            modelClass: KClass<T>,
                            extras: CreationExtras,
                        ): T {
                            return GroupProfilesHolderViewModel(group, preSelected) as T
                        }
                    },
                )

                DisposableEffect(group.id) {
                    vm.registerChild(group.id, pageViewModel)
                    onDispose {
                        vm.unregisterChild(group.id)
                    }
                }

                CompositionLocalProvider(LocalLifecycleOwner provides pageLifecycleOwner) {
                    GroupProfilesHolderLifecycle(pageViewModel, pageLifecycleOwner)
                    GroupHolderScreen(
                        viewModel = pageViewModel,
                        showActions = showActions,
                        bottomPadding = bottomPadding,
                        onProfileSelect = onProfileSelect,
                        onOpenProfileEditor = onOpenProfileEditor,
                        needReload = {
                            scope.launch {
                                if (!DataStore.serviceState.started) return@launch
                                val result = snackbarState.showSnackbar(
                                    message = resolveRepository().getString(Res.string.need_reload),
                                    actionLabel = resolveRepository().getString(Res.string.apply),
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    resolveRepository().reloadService()
                                }
                            }
                        },
                        showQR = { name, url ->
                            qrCodeInfo = name to url
                        },
                        onCopySuccess = {
                            scope.launch {
                                snackbarState.showSnackbar(
                                    message = resolveRepository().getString(Res.string.copy_success),
                                    actionLabel = resolveRepository().getString(Res.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                        showSnackbar = { message ->
                            scope.launch {
                                snackbarState.showSnackbar(
                                    message = getStringOrRes(message),
                                    actionLabel = resolveRepository().getString(Res.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                        showUndoSnackbar = { count, onUndo ->
                            scope.launch {
                                val result = snackbarState.showAndDismissOld(
                                    message = resolveRepository().getPluralString(
                                        Res.plurals.removed,
                                        count,
                                        count,
                                    ),
                                    actionLabel = resolveRepository().getString(Res.string.undo),
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    onUndo()
                                }
                            }
                        },
                        onScrollHideChange = { visible ->
                            if (pagerState.currentPage == page) {
                                onScrollHideChange(visible)
                            }
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    qrCodeInfo?.let {
        QRCodeDialog(
            url = it.second,
            name = it.first,
            onDismiss = { qrCodeInfo = null },
            showSnackbar = { message ->
                snackbarState.showSnackbar(
                    message = message,
                    actionLabel = resolveRepository().getString(Res.string.ok),
                    duration = SnackbarDuration.Short,
                )
            },
        )
    }
}

@Composable
private fun GroupProfilesHolderLifecycle(
    viewModel: GroupProfilesHolderViewModel,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    LifecycleStartEffect(viewModel, lifecycleOwner = lifecycleOwner) {
        viewModel.startObserving()
        onStopOrDispose {
            viewModel.stopObserving()
        }
    }
}

@Composable
private fun ConfigurationDialogs(
    vm: ConfigurationScreenViewModel,
    uiState: ConfigurationUiState,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var dialogKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                dialogKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    uiState.alertForDelete?.let { alert ->
        ScrollableDialog(
            onDismissRequest = { vm.dismissAlert() },
            title = {
                Text(
                    stringResource(
                        Res.string.delete_confirm_prompt,
                        alert.size,
                    ),
                )
            },
            text = { Text(text = alert.summary) },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    alert.confirm()
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    vm.dismissAlert()
                }
            },
        )
    }

    uiState.testState?.let { testState ->
        key(dialogKey) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {
                    TextButton(stringResource(Res.string.cancel)) {
                        vm.cancelTest()
                    }
                },
                icon = {
                    Icon(vectorResource(Res.drawable.ecg), null)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularWavyProgressIndicator(
                            progress = { (testState.processedCount.toDouble() / testState.total.toDouble()).toFloat() },
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        testState.latestResult?.let { result ->
                            val profile = result.profile
                            val (statusText, statusColor) = when (val testResult = result.result) {
                                is TestResult.Success -> {
                                    "${testResult.ping}ms" to colorForUrlTestDelay(testResult.ping)
                                }

                                is TestResult.Failure -> {
                                    val text = when (val reason = testResult.reason) {
                                        FailureReason.InvalidConfig ->
                                            stringResource(
                                                Res.string.connection_test_error,
                                                "Invalid Config",
                                            )

                                        FailureReason.DomainNotFound -> {
                                            stringResource(Res.string.connection_test_domain_not_found)
                                        }

                                        FailureReason.IcmpUnavailable -> {
                                            stringResource(Res.string.connection_test_icmp_ping_unavailable)
                                        }

                                        FailureReason.TcpUnavailable -> {
                                            stringResource(Res.string.connection_test_tcp_ping_unavailable)
                                        }

                                        FailureReason.ConnectionRefused -> {
                                            stringResource(Res.string.connection_test_refused)
                                        }

                                        FailureReason.NetworkUnreachable -> {
                                            stringResource(Res.string.connection_test_unreachable)
                                        }

                                        FailureReason.Timeout -> {
                                            stringResource(Res.string.connection_test_timeout)
                                        }

                                        is FailureReason.Generic -> reason.message ?: "Unknown"

                                        is FailureReason.PluginNotFound -> {
                                            stringResource(Res.string.plugin_unknown, reason.plugin)
                                        }
                                    }
                                    text to MaterialTheme.colorScheme.error
                                }
                            }

                            LaunchedEffect(profile.id, statusText, result.result) {
                                vm.rememberDisplayedError(
                                    profile.id,
                                    statusText.takeIf { result.result is TestResult.Failure },
                                )
                            }

                            Text(profile.displayName())
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = profile.displayType(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                color = statusColor,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${testState.processedCount} / ${testState.total}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
            )
        }
    }
}
