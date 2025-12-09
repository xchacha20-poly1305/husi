@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.nekohasekai.sagernet.ui.configuration

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.compose.ExpandableDropdownMenuItem
import io.nekohasekai.sagernet.compose.QRCodeDialog
import io.nekohasekai.sagernet.compose.SagerFab
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.StatsBar
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.colorForUrlTestDelay
import io.nekohasekai.sagernet.compose.paddingExceptBottom
import io.nekohasekai.sagernet.compose.showAndDismissOld
import io.nekohasekai.sagernet.compose.startFilesForResult
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.displayType
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.ui.MainViewModel
import io.nekohasekai.sagernet.ui.MainViewModelUiEvent
import io.nekohasekai.sagernet.ui.getStringOrRes
import io.nekohasekai.sagernet.ui.profile.AnyTLSSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ChainSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ConfigSettingActivity
import io.nekohasekai.sagernet.ui.profile.DirectSettingsActivity
import io.nekohasekai.sagernet.ui.profile.HttpSettingsActivity
import io.nekohasekai.sagernet.ui.profile.HysteriaSettingsActivity
import io.nekohasekai.sagernet.ui.profile.JuicitySettingsActivity
import io.nekohasekai.sagernet.ui.profile.MieruSettingsActivity
import io.nekohasekai.sagernet.ui.profile.NaiveSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ProxySetSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SSHSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowQUICSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowTLSSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowsocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TrojanSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TuicSettingsActivity
import io.nekohasekai.sagernet.ui.profile.VLESSSettingsActivity
import io.nekohasekai.sagernet.ui.profile.VMessSettingsActivity
import io.nekohasekai.sagernet.ui.profile.WireGuardSettingsActivity
import kotlinx.coroutines.launch

@Composable
fun ConfigurationScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    onNavigationClick: () -> Unit,
    @StringRes titleRes: Int = R.string.app_name,
    selectCallback: ((id: Long) -> Unit)?,
    connection: SagerConnection? = null,
    vm: ConfigurationScreenViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConfigurationScreenViewModel(selectCallback) as T
            }
        },
    ),
    preSelected: Long?,
    fillMaxSize: Boolean = true,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    var scrollHideVisible by remember { mutableStateOf(true) }
    val clipboard = LocalClipboard.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val importFile =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { file ->
            if (file != null) {
                vm.importFile(
                    contentResolver = context.contentResolver,
                    uri = file,
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
                                message = context.getString(R.string.no_proxies_found_in_file),
                                actionLabel = context.getString(android.R.string.ok),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    onError = { message ->
                        scope.launch {
                            snackbarState.showSnackbar(
                                message = message,
                                actionLabel = context.getString(android.R.string.ok),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                )
            }
        }

    val isChinese = Locale.current.language == "zh"

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val hasGroups = uiState.groups.isNotEmpty()
    val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle(DataStore.selectedGroup)
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
        scrollHideVisible = true
    }

    var qrCodeInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    var showAddMenu by remember { mutableStateOf(false) }
    var showAddManualMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showConnectionTestMenu by remember { mutableStateOf(false) }
    var showOrderMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val currentOrder =
        if (pagerState.pageCount > 0 && pagerState.currentPage < uiState.groups.size) {
            uiState.groups[pagerState.currentPage].order
        } else {
            0
        }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val topAppBarColors = TopAppBarDefaults.topAppBarColors()
    val appBarContainerColor by animateColorAsState(
        targetValue = lerp(
            topAppBarColors.containerColor,
            topAppBarColors.scrolledContainerColor,
            scrollBehavior.state.overlappedFraction.coerceIn(0f, 1f),
        ),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "appBarContainerColor",
    )
    val windowInsets = WindowInsets.safeDrawing

    val serviceStatus by connection?.status?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    val service by connection?.service?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        vm.scrollToProxy(preSelected ?: DataStore.selectedProxy)
    }

    Scaffold(
        modifier = modifier
            .then(if (fillMaxSize) Modifier.fillMaxSize() else Modifier)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (fillMaxSize) TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { vm.setSearchQuery(it) },
                            placeholder = { Text(stringResource(android.R.string.search_go)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    vm.setSearchQuery(searchQuery)
                                },
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.5f,
                                ),
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.2f,
                                ),
                            ),
                        )
                    } else {
                        Text(
                            text = stringResource(
                                if (selectCallback != null) {
                                    titleRes
                                } else {
                                    R.string.app_name
                                },
                            ),
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    vm.scrollToProxy(
                                        selectedGroup,
                                        preSelected ?: DataStore.selectedProxy,
                                        fallbackToTop = true,
                                    )
                                },
                                onLongClick = {
                                    vm.scrollToProxy(preSelected ?: DataStore.selectedProxy)
                                },
                            ),
                            style = if (isChinese) {
                                // 说文小篆（虎兕）
                                // Copyright: https://www.zdic.net/aboutus/copyright/ (A copy of CC0 1.0 was embed in the font file)
                                MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = remember { FontFamily(Font(R.font.shuowenxiaozhuan_husi)) },
                                )
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                        )
                    }
                },
                navigationIcon = {
                    if (selectCallback != null) SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.close),
                        contentDescription = stringResource(R.string.close),
                        onClick = onNavigationClick,
                    ) else SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.menu),
                        contentDescription = stringResource(R.string.menu),
                        onClick = onNavigationClick,
                    )
                },
                actions = {
                    if (selectCallback != null) return@TopAppBar
                    if (isSearchActive) SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.close),
                        contentDescription = stringResource(R.string.close),
                    ) {
                        isSearchActive = false
                        vm.setSearchQuery("")
                    } else {
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.search),
                            contentDescription = stringResource(android.R.string.search_go),
                        ) {
                            isSearchActive = true
                        }

                        Box {
                            SimpleIconButton(
                                imageVector = ImageVector.vectorResource(R.drawable.note_add),
                                contentDescription = stringResource(R.string.add_profile),
                            ) {
                                showAddMenu = true
                            }
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.add_profile_methods_scan_qr_code)) },
                                    onClick = {
                                        context.startActivity(
                                            Intent(
                                                context,
                                                ScannerActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_import)) },
                                    onClick = {
                                        showAddMenu = false
                                        lifecycleOwner.lifecycleScope.launch {
                                            runOnDefaultDispatcher {
                                                val text = clipboard.getClipEntry()
                                                    ?.clipData?.getItemAt(0)
                                                    ?.text?.toString().orEmpty()
                                                mainViewModel.parseProxy(text)
                                            }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_import_file)) },
                                    onClick = {
                                        showAddMenu = false
                                        startFilesForResult(importFile, "*/*") { message ->
                                            scope.launch {
                                                snackbarState.showSnackbar(
                                                    message = context.getString(message),
                                                    actionLabel = context.getString(android.R.string.ok),
                                                    duration = SnackbarDuration.Short,
                                                )
                                            }
                                        }
                                    },
                                )
                                ExpandableDropdownMenuItem(stringResource(R.string.add_profile_methods_manual_settings)) {
                                    showAddMenu = false
                                    showAddManualMenu = true
                                }
                            }
                            DropdownMenu(
                                expanded = showAddManualMenu,
                                onDismissRequest = { showAddManualMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_socks)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                SocksSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_http)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                HttpSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_shadowsocks)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                ShadowsocksSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_vmess)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                VMessSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_vless)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                VLESSSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_trojan)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                TrojanSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_mieru)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                MieruSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_naive)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                NaiveSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_hysteria)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                HysteriaSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_tuic)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                TuicSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_juicity)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                JuicitySettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_direct)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                DirectSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_ssh)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                SSHSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_wireguard)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                WireGuardSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_shadowtls)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                ShadowTLSSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_anytls)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                AnyTLSSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_shadowquic)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                ShadowQUICSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.proxy_set)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                ProxySetSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.custom_config)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                ConfigSettingActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.proxy_chain)) },
                                    onClick = {
                                        showAddManualMenu = false
                                        context.startActivity(
                                            Intent(
                                                context,
                                                ChainSettingsActivity::class.java,
                                            ),
                                        )
                                    },
                                )
                            }
                        }

                        Box {
                            SimpleIconButton(
                                imageVector = ImageVector.vectorResource(R.drawable.more_vert),
                                contentDescription = stringResource(R.string.more),
                            ) {
                                showOverflowMenu = true
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.clear_traffic_statistics)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        vm.clearTrafficStatistics(DataStore.selectedGroup)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.remove_duplicate)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        vm.removeDuplicate(DataStore.selectedGroup)
                                    },
                                )
                                ExpandableDropdownMenuItem(stringResource(R.string.connection_test)) {
                                    showOverflowMenu = false
                                    showConnectionTestMenu = true
                                }
                                ExpandableDropdownMenuItem(stringResource(R.string.sort_mode)) {
                                    showOverflowMenu = false
                                    showOrderMenu = true
                                }
                            }
                            DropdownMenu(
                                expanded = showConnectionTestMenu,
                                onDismissRequest = { showConnectionTestMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.connection_test_icmp_ping)) },
                                    onClick = {
                                        showConnectionTestMenu = false
                                        vm.doTest(
                                            DataStore.currentGroupId(),
                                            TestType.ICMPPing,
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.connection_test_tcp_ping)) },
                                    onClick = {
                                        showConnectionTestMenu = false
                                        vm.doTest(
                                            DataStore.currentGroupId(),
                                            TestType.TCPPing,
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.connection_test_url_test)) },
                                    onClick = {
                                        showConnectionTestMenu = false
                                        vm.doTest(
                                            DataStore.currentGroupId(),
                                            TestType.URLTest,
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.connection_test_delete_unavailable)) },
                                    onClick = {
                                        showConnectionTestMenu = false
                                        vm.deleteUnavailable(DataStore.selectedGroup)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.connection_test_clear_results)) },
                                    onClick = {
                                        showConnectionTestMenu = false
                                        vm.clearResults(DataStore.selectedGroup)
                                    },
                                )
                            }
                            DropdownMenu(
                                expanded = showOrderMenu,
                                onDismissRequest = { showOrderMenu = false },
                            ) {
                                val orders = listOf(
                                    stringResource(R.string.group_order_origin),
                                    stringResource(R.string.group_order_by_name),
                                    stringResource(R.string.group_order_by_delay),
                                )
                                orders.forEachIndexed { i, option ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    selected = (currentOrder == i),
                                                    onClick = null,
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(text = option)
                                            }
                                        },
                                        onClick = {
                                            showOrderMenu = false
                                            vm.updateOrder(DataStore.selectedGroup, i)
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = topAppBarColors.copy(
                    containerColor = appBarContainerColor,
                    scrolledContainerColor = appBarContainerColor,
                ),
                scrollBehavior = scrollBehavior,
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            if (selectCallback == null && serviceStatus != null) {
                SagerFab(
                    visible = scrollHideVisible,
                    state = serviceStatus!!.state,
                    showSnackbar = { message ->
                        scope.launch {
                            snackbarState.showSnackbar(
                                message = context.getStringOrRes(message),
                                actionLabel = context.getString(android.R.string.ok),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (selectCallback == null && serviceStatus?.state == BaseService.State.Connected) {
                StatsBar(
                    status = serviceStatus!!,
                    visible = scrollHideVisible,
                    mainViewModel = mainViewModel,
                    service = service,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding),
        ) {
            if (hasGroups) {
                if (uiState.groups.size > 1) PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage.coerceIn(0, uiState.groups.size - 1),
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

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val group = uiState.groups[page]
                    val pageViewModel = viewModel<GroupProfilesHolderViewModel>(
                        key = "group-holder-${group.id}",
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
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

                    LaunchedEffect(searchQuery) {
                        pageViewModel.query = searchQuery
                    }

                    GroupHolderScreen(
                        viewModel = pageViewModel,
                        showActions = selectCallback == null,
                        onProfileSelect = { profileId -> vm.onProfileSelect(profileId) },
                        needReload = {
                            scope.launch {
                                if (!DataStore.serviceState.started) return@launch
                                val result = snackbarState.showSnackbar(
                                    message = context.getString(R.string.need_reload),
                                    actionLabel = context.getString(R.string.apply),
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    repo.reloadService()
                                }
                            }
                        },
                        showQR = { name, url ->
                            qrCodeInfo = name to url
                        },
                        onCopySuccess = {
                            scope.launch {
                                snackbarState.showSnackbar(
                                    message = context.getString(R.string.copy_success),
                                    actionLabel = context.getString(android.R.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                        showSnackbar = { message ->
                            scope.launch {
                                snackbarState.showSnackbar(
                                    message = context.getStringOrRes(message),
                                    actionLabel = context.getString(android.R.string.ok),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                        showUndoSnackbar = { count, onUndo ->
                            scope.launch {
                                val result = snackbarState.showAndDismissOld(
                                    message = resources.getQuantityString(
                                        R.plurals.removed,
                                        count,
                                        count,
                                    ),
                                    actionLabel = context.getString(R.string.undo),
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    onUndo()
                                }
                            }
                        },
                        onScrollHideChange = { visible ->
                            if (pagerState.currentPage == page) {
                                scrollHideVisible = visible
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    qrCodeInfo?.let {
        QRCodeDialog(
            url = it.second,
            name = it.first,
            onDismiss = { qrCodeInfo = null },
            showSnackbar = { message ->
                snackbarState.showSnackbar(
                    message = message,
                    actionLabel = context.getString(android.R.string.ok),
                    duration = SnackbarDuration.Short,
                )
            },
        )
    }

    uiState.alertForDelete?.let { alert ->
        AlertDialog(
            onDismissRequest = { vm.dismissAlert() },
            title = {
                Text(
                    stringResource(
                        R.string.delete_confirm_prompt,
                        alert.size,
                    ),
                )
            },
            text = {
                val scrollState = rememberScrollState()
                Text(
                    text = alert.summary,
                    modifier = Modifier.verticalScroll(scrollState),
                )
            },
            confirmButton = {
                TextButton(stringResource(android.R.string.ok)) {
                    alert.confirm()
                }
            },
            dismissButton = {
                TextButton(stringResource(android.R.string.cancel)) {
                    vm.dismissAlert()
                }
            },
        )
    }

    uiState.testState?.let { testState ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(stringResource(android.R.string.cancel)) {
                    vm.cancelTest()
                }
            },
            icon = {
                Icon(ImageVector.vectorResource(R.drawable.ecg), null)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LoadingIndicator()
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
                                            R.string.connection_test_error,
                                            "Invalid Config",
                                        )

                                    FailureReason.DomainNotFound -> {
                                        stringResource(R.string.connection_test_domain_not_found)
                                    }

                                    FailureReason.IcmpUnavailable -> {
                                        stringResource(R.string.connection_test_icmp_ping_unavailable)
                                    }

                                    FailureReason.TcpUnavailable -> {
                                        stringResource(R.string.connection_test_tcp_ping_unavailable)
                                    }

                                    FailureReason.ConnectionRefused -> {
                                        stringResource(R.string.connection_test_refused)
                                    }

                                    FailureReason.NetworkUnreachable -> {
                                        stringResource(R.string.connection_test_unreachable)
                                    }

                                    FailureReason.Timeout -> {
                                        stringResource(R.string.connection_test_timeout)
                                    }

                                    is FailureReason.Generic -> reason.message ?: "Unknown"

                                    is FailureReason.PluginNotFound -> reason.message
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
                            text = profile.displayType(context),
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

    LaunchedEffect(Unit) {
        mainViewModel.uiEvent.collect { event ->
            when (event) {
                is MainViewModelUiEvent.Snackbar -> scope.launch {
                    snackbarState.showSnackbar(
                        message = context.getStringOrRes(event.message),
                        actionLabel = context.getString(android.R.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                is MainViewModelUiEvent.SnackbarWithAction -> scope.launch {
                    val result = snackbarState.showSnackbar(
                        message = context.getStringOrRes(event.message),
                        actionLabel = context.getStringOrRes(event.actionLabel),
                        duration = SnackbarDuration.Short,
                    )
                    event.callback(result)
                }

                else -> {}
            }
        }
    }
}
