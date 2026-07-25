@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.CONNECTION_TEST_URL
import fr.husi.CertProvider
import fr.husi.DEFAULT_HTTP_BYPASS
import fr.husi.Key
import fr.husi.NetworkInterfaceStrategy
import fr.husi.ProtocolProvider
import fr.husi.RuleProvider
import fr.husi.TunImplementation
import fr.husi.bg.BackendState
import fr.husi.bg.Executable
import fr.husi.bg.ServiceState
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.DurationTextField
import fr.husi.compose.HostTextField
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.LinkOrContentTextField
import fr.husi.compose.MaskedIcon
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PlatformMenuIcon
import fr.husi.compose.PortTextField
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.SagerFab
import fr.husi.compose.SimpleTopAppBar
import fr.husi.compose.StatsBar
import fr.husi.compose.SwipeableSnackbarHost
import fr.husi.compose.TextButton
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.fadingEdge
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Surface
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.compose.rememberScrollHideState
import fr.husi.compose.theme.DEFAULT
import fr.husi.compose.theme.themeString
import fr.husi.compose.theme.themes
import fr.husi.compose.withNavigation
import fr.husi.database.DataStore
import fr.husi.database.SagerDatabase
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.ktx.onIoDispatcher
import fr.husi.ktx.restartApplication
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.showAndDismissOld
import fr.husi.logLevelString
import fr.husi.platform.PlatformInfo
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.acquire_wake_lock
import fr.husi.resources.acquire_wake_lock_summary
import fr.husi.resources.allow_access
import fr.husi.resources.allow_access_sum
import fr.husi.resources.always_show_address
import fr.husi.resources.always_show_address_sum
import fr.husi.resources.app_registration
import fr.husi.resources.append_http_proxy
import fr.husi.resources.append_http_proxy_sum
import fr.husi.resources.apply
import fr.husi.resources.apps
import fr.husi.resources.auto
import fr.husi.resources.blurred_address
import fr.husi.resources.bug_report
import fr.husi.resources.cag_dns
import fr.husi.resources.cag_misc
import fr.husi.resources.cancel
import fr.husi.resources.cast_connected
import fr.husi.resources.center_focus_weak
import fr.husi.resources.cert_chrome
import fr.husi.resources.certificate_authority
import fr.husi.resources.check
import fr.husi.resources.color_lens
import fr.husi.resources.connection_test_ignore_handshake_time
import fr.husi.resources.connection_test_unified_delay
import fr.husi.resources.connection_test_url
import fr.husi.resources.construction
import fr.husi.resources.custom_rule_provider
import fr.husi.resources.data_usage
import fr.husi.resources.description
import fr.husi.resources.developer_board
import fr.husi.resources.developer_mode
import fr.husi.resources.direct_dns
import fr.husi.resources.directions_boat
import fr.husi.resources.disable
import fr.husi.resources.disable_tcp_keep_alive
import fr.husi.resources.dns
import fr.husi.resources.dns_hosts
import fr.husi.resources.domain
import fr.husi.resources.domain_strategy_for_direct
import fr.husi.resources.domain_strategy_for_server
import fr.husi.resources.download
import fr.husi.resources.ecg
import fr.husi.resources.emoji_emotions
import fr.husi.resources.enable
import fr.husi.resources.enable_ntp
import fr.husi.resources.fake_dns
import fr.husi.resources.fake_dns_for_all
import fr.husi.resources.fake_dns_for_all_sum
import fr.husi.resources.fake_ip_range_4
import fr.husi.resources.fake_ip_range_6
import fr.husi.resources.fakedns_message
import fr.husi.resources.fallback
import fr.husi.resources.fast_forward
import fr.husi.resources.file_upload
import fr.husi.resources.flight_takeoff
import fr.husi.resources.flip_camera_android
import fr.husi.resources.follow_system
import fr.husi.resources.general_settings
import fr.husi.resources.hourglass_top
import fr.husi.resources.http_proxy_bypass
import fr.husi.resources.hybrid
import fr.husi.resources.hysteria2_provider
import fr.husi.resources.hysteria_download_mbps
import fr.husi.resources.hysteria_upload_mbps
import fr.husi.resources.import_contacts
import fr.husi.resources.inbound_password
import fr.husi.resources.inbound_settings
import fr.husi.resources.inbound_username
import fr.husi.resources.insecure_warn
import fr.husi.resources.ipv4_only
import fr.husi.resources.ipv6_only
import fr.husi.resources.juicity_provider
import fr.husi.resources.keep_default
import fr.husi.resources.keyboard_tab
import fr.husi.resources.language
import fr.husi.resources.language_system_default
import fr.husi.resources.local_bar
import fr.husi.resources.lock
import fr.husi.resources.log_level
import fr.husi.resources.long_click_to_see_name
import fr.husi.resources.max_log_line
import fr.husi.resources.mdns_network_interfaces
import fr.husi.resources.menu
import fr.husi.resources.metered
import fr.husi.resources.metered_summary
import fr.husi.resources.mozilla
import fr.husi.resources.mtu
import fr.husi.resources.nat
import fr.husi.resources.need_reload
import fr.husi.resources.need_restart
import fr.husi.resources.network_interface_preference
import fr.husi.resources.network_interface_strategy
import fr.husi.resources.network_strategy
import fr.husi.resources.night_mode
import fr.husi.resources.not_set
import fr.husi.resources.ntp_category
import fr.husi.resources.ntp_server_address
import fr.husi.resources.ntp_server_port
import fr.husi.resources.ntp_sum
import fr.husi.resources.ntp_sync_interval
import fr.husi.resources.ok
import fr.husi.resources.optimistic_cache
import fr.husi.resources.person
import fr.husi.resources.plugin
import fr.husi.resources.port_local_dns
import fr.husi.resources.port_proxy
import fr.husi.resources.prefer_ipv4
import fr.husi.resources.prefer_ipv6
import fr.husi.resources.privacy
import fr.husi.resources.privacy_mode
import fr.husi.resources.privacy_mode_summary
import fr.husi.resources.profile_traffic_statistics
import fr.husi.resources.profile_traffic_statistics_summary
import fr.husi.resources.protocol_settings
import fr.husi.resources.provider_naive
import fr.husi.resources.proxied_apps
import fr.husi.resources.proxied_apps_summary
import fr.husi.resources.public_icon
import fr.husi.resources.push_pin
import fr.husi.resources.question_mark
import fr.husi.resources.remote_dns
import fr.husi.resources.route_options
import fr.husi.resources.route_rules_official
import fr.husi.resources.route_rules_provider
import fr.husi.resources.router
import fr.husi.resources.rule_folder
import fr.husi.resources.security
import fr.husi.resources.service_mode
import fr.husi.resources.service_mode_proxy
import fr.husi.resources.service_mode_vpn
import fr.husi.resources.settings
import fr.husi.resources.show_direct_speed
import fr.husi.resources.show_direct_speed_sum
import fr.husi.resources.shutter_speed
import fr.husi.resources.speed
import fr.husi.resources.speed_interval
import fr.husi.resources.system_and_user
import fr.husi.resources.tcp_keep_alive_idle
import fr.husi.resources.tcp_keep_alive_interval
import fr.husi.resources.test_concurrency
import fr.husi.resources.test_timeout
import fr.husi.resources.text_select_end
import fr.husi.resources.theme
import fr.husi.resources.timelapse
import fr.husi.resources.timer
import fr.husi.resources.traffic
import fr.husi.resources.transform
import fr.husi.resources.transgender
import fr.husi.resources.translate
import fr.husi.resources.tun_implementation
import fr.husi.resources.update_proxy_apps_when_install
import fr.husi.resources.wb_sunny
import fr.husi.resources.wifi
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.MultiSelectListPreference
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SliderPreference
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import me.zhanghai.compose.preference.TwoTargetSwitchPreference
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    onDrawerClick: () -> Unit,
    openAppManager: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollHideVisible by rememberScrollHideState(listState)
    val applyNightMode = rememberApplyNightMode()
    var showAlertDialog by remember { mutableStateOf<MainViewModelUiEvent.AlertDialog?>(null) }

    LaunchedEffect(Unit) {
        onIoDispatcher {
            DataStore.initGlobal()
        }
    }

    fun needReload() = scope.launch {
        if (!DataStore.serviceState.started) return@launch
        val result = snackbarState.showAndDismissOld(
            message = resolveRepository().getString(Res.string.need_reload),
            actionLabel = resolveRepository().getString(Res.string.apply),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.Dismissed) return@launch
        resolveRepository().reloadService()
    }

    fun needRestart() = scope.launch {
        val result = snackbarState.showAndDismissOld(
            message = resolveRepository().getString(Res.string.need_restart),
            actionLabel = resolveRepository().getString(Res.string.apply),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.Dismissed) return@launch
        resolveRepository().stopService()
        runOnDefaultDispatcher {
            delay(500.milliseconds)
            SagerDatabase.instance.close()
            Executable.killAll(true)
            restartApplication()
        }
    }

    // Dependency states for enable/visibility linking
    val isExpertState by DataStore.configurationStore
        .booleanFlow(Key.APP_EXPERT, false)
        .collectAsStateWithLifecycle(false)
    val speedIntervalState by DataStore.configurationStore
        .intFlow(Key.SPEED_INTERVAL, 1000)
        .collectAsStateWithLifecycle(1000)
    val alwaysShowAddressState by DataStore.configurationStore
        .booleanFlow(Key.ALWAYS_SHOW_ADDRESS, false)
        .collectAsStateWithLifecycle(false)
    val appendHttpProxyState by DataStore.configurationStore
        .booleanFlow(Key.APPEND_HTTP_PROXY, false)
        .collectAsStateWithLifecycle(false)
    val rulesProviderState by DataStore.configurationStore
        .intFlow(Key.RULES_PROVIDER, RuleProvider.OFFICIAL)
        .collectAsStateWithLifecycle(RuleProvider.OFFICIAL)
    val fakeDNSState by DataStore.configurationStore
        .booleanFlow(Key.ENABLE_FAKE_DNS, false)
        .collectAsStateWithLifecycle(false)
    val ntpEnableState by DataStore.configurationStore
        .booleanFlow(Key.ENABLE_NTP, false)
        .collectAsStateWithLifecycle(false)
    val serviceModeState by DataStore.configurationStore
        .stringFlow(Key.SERVICE_MODE, Key.MODE_VPN)
        .collectAsStateWithLifecycle(Key.MODE_VPN)
    val defaultDisableTcpKeepAlive = PlatformInfo.isAndroid
    val disableTcpKeepAliveState by DataStore.configurationStore
        .booleanFlow(Key.DISABLE_TCP_KEEP_ALIVE, defaultDisableTcpKeepAlive)
        .collectAsStateWithLifecycle(defaultDisableTcpKeepAlive)

    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
                navigationIcon = PlatformMenuIcon(
                    imageVector = vectorResource(Res.drawable.menu),
                    contentDescription = stringResource(Res.string.menu),
                    onClick = onDrawerClick,
                ),
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
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
        ProvidePreferenceLocals {
            val contentPadding = innerPadding.withNavigation()
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .fadingEdge(listState),
                    contentPadding = contentPadding,
                ) {
                    item { PreferenceCategory(text = { Text(stringResource(Res.string.general_settings)) }) }
                    preferenceGroup {
                        GeneralSettingsGroup(
                            needReload = { needReload() },
                            needRestart = { needRestart() },
                            applyNightMode = applyNightMode,
                            speedIntervalState = speedIntervalState,
                            alwaysShowAddressState = alwaysShowAddressState,
                            isExpertState = isExpertState,
                        )
                    }

                    item { PreferenceCategory(text = { Text(stringResource(Res.string.route_options)) }) }
                    preferenceGroup {
                        RouteSettingsGroup(
                            needReload = { needReload() },
                            serviceMode = serviceModeState,
                            disableTcpKeepAliveState = disableTcpKeepAliveState,
                            rulesProviderState = rulesProviderState,
                            appendHttpProxyState = appendHttpProxyState,
                            openAppManager = openAppManager,
                        )
                    }

                    item { PreferenceCategory(text = { Text(stringResource(Res.string.protocol_settings)) }) }
                    preferenceGroup {
                        ProtocolSettingsGroup(
                            needReload = { needReload() },
                            needRestart = { needRestart() },
                            isExpertState = isExpertState,
                        )
                    }

                    item { PreferenceCategory(text = { Text(stringResource(Res.string.cag_dns)) }) }
                    preferenceGroup {
                        DnsSettingsGroup(
                            needReload = { needReload() },
                            fakeDNSState = fakeDNSState,
                        )
                    }

                    item { PreferenceCategory(text = { Text(stringResource(Res.string.inbound_settings)) }) }
                    preferenceGroup {
                        InboundSettingsGroup(
                            needReload = { needReload() },
                            appendHttpProxyState = appendHttpProxyState,
                            isExpertState = isExpertState,
                        )
                    }

                    item { PreferenceCategory(text = { Text(stringResource(Res.string.cag_misc)) }) }
                    preferenceGroup {
                        MiscSettingsGroup(
                            needReload = { needReload() },
                            needRestart = { needRestart() },
                        )
                    }

                    item { PreferenceCategory(text = { Text(stringResource(Res.string.ntp_category)) }) }
                    preferenceGroup {
                        NtpSettingsGroup(
                            needReload = { needReload() },
                            ntpEnableState = ntpEnableState,
                        )
                    }
                }

                BoxedVerticalScrollbar(
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
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
private fun ColorPickerPreference(
    key: String,
    title: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    val currentTheme by DataStore.configurationStore
        .intFlow(key, DEFAULT)
        .collectAsStateWithLifecycle(DEFAULT)
    var showDialog by remember { mutableStateOf(false) }
    val extraColors = rememberThemeExtraColors()
    Preference(
        title = { title() },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        icon = {
            MaskedIcon(
                Res.drawable.color_lens,
                color = IconMaskColors.IconLightOrange,
            )
        },
        summary = { Text(stringResource(themeString(currentTheme))) },
        widgetContainer = {
            Box(modifier = Modifier.padding(end = 8.dp)) {
                Circle(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        },
        onClick = { showDialog = true },
    )

    if (showDialog) {
        val colors = themes + extraColors

        BasicAlertDialog(
            onDismissRequest = { showDialog = false },
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.theme),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (PlatformInfo.isAndroid) Text(
                        text = stringResource(Res.string.long_click_to_see_name),
                        modifier = Modifier.padding(bottom = 16.dp),
                        style = MaterialTheme.typography.labelSmallEmphasized,
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        items(
                            count = colors.size,
                            key = { index -> index },
                            contentType = { 0 },
                        ) { index ->
                            val theme = index + 1
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable {
                                        DataStore.configurationStore.putInt(key, theme)
                                        showDialog = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                        TooltipAnchorPosition.Above,
                                    ),
                                    tooltip = {
                                        PlainTooltip {
                                            Text(stringResource(themeString(theme)))
                                        }
                                    },
                                    state = rememberTooltipState(),
                                ) {
                                    Circle(
                                        modifier = Modifier.size(48.dp),
                                        color = colors[index],
                                        selected = currentTheme == theme,
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(stringResource(Res.string.cancel)) {
                            showDialog = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal expect fun AutoConnectPreference()

@Composable
private fun ProxyAppsPreferences(openAppManager: () -> Unit) {
    if (PlatformInfo.isAndroid) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.PROXY_APPS, false)
            .collectAsStateWithLifecycle(false)
        // Per-app proxy
        TwoTargetSwitchPreference(
            value = value,
            onValueChange = {
                DataStore.proxyApps = it
                if (it) {
                    openAppManager()
                }
            },
            title = { Text(stringResource(Res.string.proxied_apps)) },
            icon = {
                MaskedIcon(Res.drawable.apps, color = IconMaskColors.IconCyan)
            },
            summary = { Text(stringResource(Res.string.proxied_apps_summary)) },
            onClick = {
                if (!value) {
                    DataStore.proxyApps = true
                }
                openAppManager()
            },
        )
        val updateValue by DataStore.configurationStore
            .booleanFlow(Key.UPDATE_PROXY_APPS_WHEN_INSTALL, false)
            .collectAsStateWithLifecycle(false)
        PreferenceDivider()
        SwitchPreference(
            value = updateValue,
            onValueChange = { DataStore.updateProxyAppsWhenInstall = it },
            title = { Text(stringResource(Res.string.update_proxy_apps_when_install)) },
            icon = {
                MaskedIcon(
                    Res.drawable.keyboard_tab,
                    color = IconMaskColors.IconLavender,
                )
            },
        )
    }
}

@Composable
private fun GeneralSettingsGroup(
    needReload: () -> Unit,
    needRestart: () -> Unit,
    applyNightMode: (Int) -> Unit,
    speedIntervalState: Int,
    alwaysShowAddressState: Boolean,
    isExpertState: Boolean,
) {
    AutoConnectPreference()
    PreferenceDivider()

    ColorPickerPreference(
        key = Key.APP_THEME,
        title = { Text(stringResource(Res.string.theme)) },
    )
    PreferenceDivider()

    fun nightString(index: Int): StringResource = when (index) {
        0 -> Res.string.follow_system
        1 -> Res.string.enable
        2 -> Res.string.disable
        3 -> Res.string.auto
        else -> Res.string.follow_system
    }

    val nightValue by DataStore.configurationStore
        .intFlow(Key.NIGHT_THEME, 0)
        .collectAsStateWithLifecycle(0)
    ListPreference(
        value = nightValue,
        onValueChange = {
            DataStore.nightTheme = it
            applyNightMode(it)
        },
        values = intListN(4),
        title = { Text(stringResource(Res.string.night_mode)) },
        icon = {
            MaskedIcon(
                Res.drawable.wb_sunny,
                color = IconMaskColors.IconLightOrange,
            )
        },
        summary = { Text(stringResource(nightString(nightValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(nightString(it))) },
    )
    PreferenceDivider()

    fun getLanguageDisplayName(tag: String): String =
        AppLanguage.fromTag(tag)?.displayName ?: runBlocking {
            resolveRepository().getString(Res.string.language_system_default)
        }

    val languageValues = AppLanguage.entries.map { it.tag }
    val languageController = rememberAppLanguageController(defaultTag = "")
    val appLanguage by languageController.flow.collectAsStateWithLifecycle(languageController.value)
    val selectedLanguage = if (appLanguage in languageValues) appLanguage else ""
    ListPreference(
        value = selectedLanguage,
        onValueChange = { languageController.value = it },
        values = languageValues,
        title = { Text(stringResource(Res.string.language)) },
        icon = {
            MaskedIcon(Res.drawable.translate, color = IconMaskColors.IconLavender)
        },
        summary = { Text(getLanguageDisplayName(selectedLanguage)) },
        type = ListPreferenceType.ALERT_DIALOG,
        valueToText = { AnnotatedString(getLanguageDisplayName(it)) },
    )
    PreferenceDivider()

    fun serviceModeText(mode: String): StringResource = when (mode) {
        Key.MODE_VPN -> Res.string.service_mode_vpn
        Key.MODE_PROXY -> Res.string.service_mode_proxy
        else -> Res.string.service_mode_vpn
    }

    val serviceModeValue by DataStore.configurationStore
        .stringFlow(Key.SERVICE_MODE, Key.MODE_VPN)
        .collectAsStateWithLifecycle(Key.MODE_VPN)
    ListPreference(
        value = serviceModeValue,
        onValueChange = { DataStore.serviceMode = it },
        values = listOf(Key.MODE_VPN, Key.MODE_PROXY),
        title = { Text(stringResource(Res.string.service_mode)) },
        icon = {
            MaskedIcon(
                Res.drawable.developer_mode,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(stringResource(serviceModeText(serviceModeValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(serviceModeText(it))) },
    )
    PreferenceDivider()

    fun tunImplText(value: Int): String = when (value) {
        TunImplementation.GVISOR -> "gVisor"
        TunImplementation.SYSTEM -> "System"
        TunImplementation.MIXED -> "Mixed"
        else -> error("impossible")
    }

    val tunValue by DataStore.configurationStore
        .intFlow(Key.TUN_IMPLEMENTATION, TunImplementation.MIXED)
        .collectAsStateWithLifecycle(TunImplementation.MIXED)
    ListPreference(
        value = tunValue,
        onValueChange = {
            DataStore.tunImplementation = it
            needReload()
        },
        values = listOf(
            TunImplementation.GVISOR,
            TunImplementation.SYSTEM,
            TunImplementation.MIXED,
        ),
        title = { Text(stringResource(Res.string.tun_implementation)) },
        icon = {
            MaskedIcon(
                Res.drawable.flip_camera_android,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(tunImplText(tunValue)) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(tunImplText(it)) },
    )
    PreferenceDivider()

    val mtuValue by DataStore.configurationStore
        .intFlow(Key.MTU, 9000)
        .collectAsStateWithLifecycle(9000)
    TextFieldPreference(
        value = mtuValue,
        onValueChange = {
            DataStore.mtu = it
            needReload()
        },
        title = { Text(stringResource(Res.string.mtu)) },
        textToValue = { it.toIntOrNull() ?: 9000 },
        icon = {
            MaskedIcon(
                Res.drawable.public_icon,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(mtuValue.toString()) },
        valueToText = { it.toString() },
    )
    if (PlatformInfo.isAndroid) {
        PreferenceDivider()
        PlatformGeneralOptions(needReload)
    }
    PreferenceDivider()

    fun speedIntervalText(ms: Int): StringOrRes = when (ms) {
        0 -> StringOrRes.Res(Res.string.disable)
        500 -> StringOrRes.Direct("500ms")
        1000 -> StringOrRes.Direct("1s")
        3000 -> StringOrRes.Direct("3s")
        10000 -> StringOrRes.Direct("10s")
        else -> StringOrRes.Direct("1s")
    }

    val speedIntervalValue by DataStore.configurationStore
        .intFlow(Key.SPEED_INTERVAL, 1000)
        .collectAsStateWithLifecycle(1000)
    ListPreference(
        value = speedIntervalValue,
        onValueChange = { DataStore.speedInterval = it },
        values = listOf(0, 500, 1000, 3000, 10000),
        title = { Text(stringResource(Res.string.speed_interval)) },
        icon = {
            MaskedIcon(
                Res.drawable.shutter_speed,
                color = IconMaskColors.IconLightPink,
            )
        },
        summary = { Text(stringOrRes(speedIntervalText(speedIntervalValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = {
            val text = runBlocking { getStringOrRes(speedIntervalText(it)) }
            AnnotatedString(text)
        },
    )
    PreferenceDivider()

    val profileTrafficStatisticsValue by DataStore.configurationStore
        .booleanFlow(Key.PROFILE_TRAFFIC_STATISTICS, true)
        .collectAsStateWithLifecycle(true)
    SwitchPreference(
        value = profileTrafficStatisticsValue,
        onValueChange = { DataStore.profileTrafficStatistics = it },
        title = { Text(stringResource(Res.string.profile_traffic_statistics)) },
        icon = {
            MaskedIcon(
                Res.drawable.traffic,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringResource(Res.string.profile_traffic_statistics_summary)) },
        enabled = speedIntervalState != 0,
    )
    PreferenceDivider()

    val showDirectSpeedValue by DataStore.configurationStore
        .booleanFlow(Key.SHOW_DIRECT_SPEED, true)
        .collectAsStateWithLifecycle(true)
    SwitchPreference(
        value = showDirectSpeedValue,
        onValueChange = { DataStore.showDirectSpeed = it },
        title = { Text(stringResource(Res.string.show_direct_speed)) },
        icon = {
            MaskedIcon(Res.drawable.speed, color = IconMaskColors.IconLightPink)
        },
        summary = { Text(stringResource(Res.string.show_direct_speed_sum)) },
        enabled = speedIntervalState != 0,
    )
    PreferenceDivider()

    val alwaysShowAddressValue by DataStore.configurationStore
        .booleanFlow(Key.ALWAYS_SHOW_ADDRESS, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = alwaysShowAddressValue,
        onValueChange = { DataStore.alwaysShowAddress = it },
        title = { Text(stringResource(Res.string.always_show_address)) },
        icon = {
            MaskedIcon(
                Res.drawable.center_focus_weak,
                color = IconMaskColors.IconCoral,
            )
        },
        summary = { Text(stringResource(Res.string.always_show_address_sum)) },
    )
    PreferenceDivider()

    val blurredAddressValue by DataStore.configurationStore
        .booleanFlow(Key.BLURRED_ADDRESS, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = blurredAddressValue,
        onValueChange = { DataStore.blurredAddress = it },
        title = { Text(stringResource(Res.string.blurred_address)) },
        icon = {
            MaskedIcon(
                Res.drawable.transgender,
                color = IconMaskColors.IconLavender,
            )
        },
        enabled = alwaysShowAddressState,
    )
    PreferenceDivider()

    val securityAdvisoryValue by DataStore.configurationStore
        .booleanFlow(Key.SECURITY_ADVISORY, true)
        .collectAsStateWithLifecycle(true)
    SwitchPreference(
        value = securityAdvisoryValue,
        onValueChange = { DataStore.securityAdvisory = it },
        title = { Text(stringResource(Res.string.insecure_warn)) },
        icon = {
            MaskedIcon(
                Res.drawable.security,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.risk(),
            )
        },
    )
    if (PlatformInfo.isAndroid) {
        PreferenceDivider()
        PlatformSecurityOptions()
        PreferenceDivider()
        MeteredNetworkPreference(needReload)
    }
    PreferenceDivider()

    val logLevelValue by DataStore.configurationStore
        .intFlow(Key.LOG_LEVEL, 3)
        .collectAsStateWithLifecycle(3)
    ListPreference(
        value = logLevelValue,
        onValueChange = {
            DataStore.logLevel = it
            needRestart()
        },
        values = intListN(7),
        title = { Text(stringResource(Res.string.log_level)) },
        icon = {
            MaskedIcon(
                Res.drawable.bug_report,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(logLevelString(logLevelValue)) },
        type = ListPreferenceType.ALERT_DIALOG,
        valueToText = { AnnotatedString(logLevelString(it)) },
    )
    PreferenceDivider()

    val maxLogLineValue by DataStore.configurationStore
        .intFlow(Key.LOG_MAX_LINE, 1024)
        .collectAsStateWithLifecycle(1024)
    var previewValue by remember { mutableFloatStateOf(maxLogLineValue.toFloat()) }
    SliderPreference(
        value = maxLogLineValue.toFloat(),
        onValueChange = { DataStore.logMaxLine = it.toInt() },
        sliderValue = previewValue,
        onSliderValueChange = { previewValue = it },
        title = { Text(stringResource(Res.string.max_log_line)) },
        valueRange = 1024f..1024f * 64f,
        valueSteps = 128,
        icon = {
            MaskedIcon(
                Res.drawable.description,
                color = IconMaskColors.IconWarmGray,
            )
        },
        valueText = { Text(previewValue.toInt().toString()) },
    )
}

@Composable
private fun PlatformSecurityOptions() {
    if (!PlatformInfo.isAndroid) return
    val value by DataStore.configurationStore
        .booleanFlow(Key.PRIVACY_MODE, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = value,
        onValueChange = { DataStore.privacyMode = it },
        title = { Text(stringResource(Res.string.privacy_mode)) },
        icon = {
            MaskedIcon(Res.drawable.privacy, color = IconMaskColors.IconCoral)
        },
        summary = { Text(stringResource(Res.string.privacy_mode_summary)) },
    )
}

@Composable
private fun MeteredNetworkPreference(needReload: () -> Unit) {
    if (!PlatformInfo.isAndroid) return
    val value by DataStore.configurationStore
        .booleanFlow(Key.METERED_NETWORK, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = value,
        onValueChange = {
            DataStore.meteredNetwork = it
            needReload()
        },
        title = { Text(stringResource(Res.string.metered)) },
        icon = {
            MaskedIcon(
                Res.drawable.data_usage,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(stringResource(Res.string.metered_summary)) },
    )
}

@Composable
private fun RouteSettingsGroup(
    needReload: () -> Unit,
    serviceMode: String,
    disableTcpKeepAliveState: Boolean,
    rulesProviderState: Int,
    appendHttpProxyState: Boolean,
    openAppManager: () -> Unit,
) {
    if (PlatformInfo.isAndroid) {
        ProxyAppsPreferences(openAppManager)
        PreferenceDivider()
    }

    PlatformRouteOptions(
        needReload = needReload,
        isVpnMode = serviceMode == Key.MODE_VPN,
    )
    PreferenceDivider()

    fun networkStrategyTextRes(value: String): StringResource = when (value) {
        "" -> Res.string.auto
        "prefer_ipv6" -> Res.string.prefer_ipv6
        "prefer_ipv4" -> Res.string.prefer_ipv4
        "ipv4_only" -> Res.string.ipv4_only
        "ipv6_only" -> Res.string.ipv6_only
        else -> Res.string.auto
    }

    val networkStrategyValue by DataStore.configurationStore
        .stringFlow(Key.NETWORK_STRATEGY, "")
        .collectAsStateWithLifecycle("")
    ListPreference(
        value = networkStrategyValue,
        onValueChange = {
            DataStore.networkStrategy = it
            needReload()
        },
        values = listOf("", "prefer_ipv6", "prefer_ipv4", "ipv4_only", "ipv6_only"),
        title = { Text(stringResource(Res.string.network_strategy)) },
        icon = {
            MaskedIcon(Res.drawable.router, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(stringResource(networkStrategyTextRes(networkStrategyValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(networkStrategyTextRes(it))) },
    )
    PreferenceDivider()

    fun networkInterfaceStrategyTextRes(selection: Int): StringResource = when (selection) {
        NetworkInterfaceStrategy.DEFAULT -> Res.string.keep_default
        NetworkInterfaceStrategy.HYBRID -> Res.string.hybrid
        NetworkInterfaceStrategy.FALLBACK -> Res.string.fallback
        else -> Res.string.keep_default
    }

    val networkInterfaceValue by DataStore.configurationStore
        .intFlow(Key.NETWORK_INTERFACE_STRATEGY, NetworkInterfaceStrategy.DEFAULT)
        .collectAsStateWithLifecycle(NetworkInterfaceStrategy.DEFAULT)
    ListPreference(
        value = networkInterfaceValue,
        onValueChange = {
            DataStore.networkInterfaceType = it
            needReload()
        },
        values = listOf(
            NetworkInterfaceStrategy.DEFAULT,
            NetworkInterfaceStrategy.HYBRID,
            NetworkInterfaceStrategy.FALLBACK,
        ),
        title = { Text(stringResource(Res.string.network_interface_strategy)) },
        icon = {
            MaskedIcon(
                Res.drawable.construction,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = { Text(stringResource(networkInterfaceStrategyTextRes(networkInterfaceValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(networkInterfaceStrategyTextRes(it))) },
    )
    PreferenceDivider()

    val preferredInterfaces by DataStore.configurationStore
        .stringSetFlow(Key.NETWORK_PREFERRED_INTERFACES, emptySet())
        .collectAsStateWithLifecycle(emptySet())
    MultiSelectListPreference(
        value = preferredInterfaces,
        onValueChange = {
            DataStore.networkPreferredInterfaces = it
            needReload()
        },
        values = listOf("wifi", "cellular", "ethernet", "other"),
        title = { Text(stringResource(Res.string.network_interface_preference)) },
        icon = {
            MaskedIcon(
                Res.drawable.public_icon,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = {
            val text = if (preferredInterfaces.isEmpty()) {
                stringResource(Res.string.not_set)
            } else preferredInterfaces.joinToString("\n")
            Text(text)
        },
        valueToText = { AnnotatedString(it) },
    )
    PreferenceDivider()

    val disableTcpKeepAliveValue by DataStore.configurationStore
        .booleanFlow(Key.DISABLE_TCP_KEEP_ALIVE, PlatformInfo.isAndroid)
        .collectAsStateWithLifecycle(PlatformInfo.isAndroid)
    SwitchPreference(
        value = disableTcpKeepAliveValue,
        onValueChange = {
            DataStore.disableTcpKeepAlive = it
            needReload()
        },
        title = { Text(stringResource(Res.string.disable_tcp_keep_alive)) },
        icon = {
            MaskedIcon(Res.drawable.ecg, color = IconMaskColors.IconLightGreen)
        },
    )
    PreferenceDivider()

    val tcpKeepAliveIdleValue by DataStore.configurationStore
        .stringFlow(Key.TCP_KEEP_ALIVE_IDLE, "")
        .collectAsStateWithLifecycle("")
    TextFieldPreference(
        value = tcpKeepAliveIdleValue,
        onValueChange = {
            DataStore.tcpKeepAliveIdle = it
            needReload()
        },
        title = { Text(stringResource(Res.string.tcp_keep_alive_idle)) },
        textToValue = { it },
        enabled = !disableTcpKeepAliveState,
        icon = {
            MaskedIcon(
                Res.drawable.hourglass_top,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = { Text(contentOrUnset(tcpKeepAliveIdleValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        DurationTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    val tcpKeepAliveIntervalValue by DataStore.configurationStore
        .stringFlow(Key.TCP_KEEP_ALIVE_INTERVAL_0, "")
        .collectAsStateWithLifecycle("")
    TextFieldPreference(
        value = tcpKeepAliveIntervalValue,
        onValueChange = {
            DataStore.tcpKeepAliveInterval = it
            needReload()
        },
        title = { Text(stringResource(Res.string.tcp_keep_alive_interval)) },
        textToValue = { it },
        enabled = !disableTcpKeepAliveState,
        icon = {
            MaskedIcon(Res.drawable.timer, color = IconMaskColors.IconWarmGray)
        },
        summary = { Text(contentOrUnset(tcpKeepAliveIntervalValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        DurationTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    fun rulesProviderText(index: Int): StringOrRes = when (index) {
        RuleProvider.OFFICIAL -> StringOrRes.Res(Res.string.route_rules_official)
        RuleProvider.LOYALSOLDIER -> StringOrRes.Direct("Loyalsoldier (1715173329/sing-geo*)")
        RuleProvider.CHOCOLATE4U -> StringOrRes.Direct("Chocolate4U/Iran-sing-box-rules")
        RuleProvider.RUNETFREEDOM -> StringOrRes.Direct("runetfreedom/russia-v2ray-rules-dat")
        RuleProvider.CUSTOM -> StringOrRes.Res(Res.string.custom_rule_provider)
        else -> StringOrRes.Res(Res.string.route_rules_official)
    }

    val rulesProviderValue by DataStore.configurationStore
        .intFlow(Key.RULES_PROVIDER, RuleProvider.OFFICIAL)
        .collectAsStateWithLifecycle(RuleProvider.OFFICIAL)
    ListPreference(
        value = rulesProviderValue,
        onValueChange = { DataStore.rulesProvider = it },
        values = listOf(
            RuleProvider.OFFICIAL,
            RuleProvider.LOYALSOLDIER,
            RuleProvider.CHOCOLATE4U,
            RuleProvider.RUNETFREEDOM,
            RuleProvider.CUSTOM,
        ),
        title = { Text(stringResource(Res.string.route_rules_provider)) },
        icon = {
            MaskedIcon(
                Res.drawable.rule_folder,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringOrRes(rulesProviderText(rulesProviderValue))) },
        type = ListPreferenceType.ALERT_DIALOG,
        valueToText = { AnnotatedString(stringOrRes(rulesProviderText(it))) },
    )
    if (rulesProviderState == RuleProvider.CUSTOM) {
        PreferenceDivider()
        val defaultUrl =
            "https://codeload.github.com/SagerNet/sing-geosite/tar.gz/refs/heads/rule-set"
        val customRuleProviderValue by DataStore.configurationStore
            .stringFlow(Key.CUSTOM_RULE_PROVIDER, defaultUrl)
            .collectAsStateWithLifecycle(defaultUrl)
        TextFieldPreference(
            value = customRuleProviderValue,
            onValueChange = { DataStore.customRuleProvider = it },
            title = { Text(stringResource(Res.string.custom_rule_provider)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.import_contacts,
                    color = IconMaskColors.IconLightYellow,
                )
            },
            summary = { Text(contentOrUnset(customRuleProviderValue)) },
            valueToText = { it },
        ) { value, onValueChange, onOk ->
            LinkOrContentTextField(value, onValueChange, onOk)
        }
    }
}

@Composable
internal expect fun PlatformRouteOptions(needReload: () -> Unit, isVpnMode: Boolean)

@Composable
private fun HttpProxyBypassPreference(enabled: Boolean, needReload: () -> Unit) {
    if (!PlatformInfo.isAndroid) return
    val value by DataStore.configurationStore
        .stringFlow(Key.HTTP_PROXY_BYPASS, DEFAULT_HTTP_BYPASS)
        .collectAsStateWithLifecycle(DEFAULT_HTTP_BYPASS)
    TextFieldPreference(
        value = value,
        onValueChange = {
            DataStore.httpProxyBypass = it
            needReload()
        },
        title = { Text(stringResource(Res.string.http_proxy_bypass)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.domain, color = IconMaskColors.IconCyan)
        },
        valueToText = { it },
        enabled = enabled,
    ) { value, onValueChange, onOk ->
        HostTextField(value, onValueChange, onOk)
    }
}

@Composable
private fun PlatformMiscOptions(needReload: () -> Unit) {
    if (!PlatformInfo.isAndroid) return
    val value by DataStore.configurationStore
        .booleanFlow(Key.ACQUIRE_WAKE_LOCK, true)
        .collectAsStateWithLifecycle(true)
    SwitchPreference(
        value = value,
        onValueChange = {
            DataStore.acquireWakeLock = it
            needReload()
        },
        title = { Text(stringResource(Res.string.acquire_wake_lock)) },
        icon = {
            MaskedIcon(
                Res.drawable.developer_board,
                color = IconMaskColors.IconLightGreen,
            )
        },
        summary = { Text(stringResource(Res.string.acquire_wake_lock_summary)) },
    )
}

@Composable
internal expect fun DisableProcessTextPreference()

@Composable
private fun ProtocolSettingsGroup(
    needReload: () -> Unit,
    needRestart: () -> Unit,
    isExpertState: Boolean,
) {
    val uploadSpeedValue by DataStore.configurationStore
        .intFlow(Key.UPLOAD_SPEED, 0)
        .collectAsStateWithLifecycle(0)
    TextFieldPreference(
        value = uploadSpeedValue,
        onValueChange = {
            DataStore.uploadSpeed = it
            needReload()
        },
        title = { Text(stringResource(Res.string.hysteria_upload_mbps)) },
        textToValue = { it.toIntOrNull() ?: 0 },
        icon = {
            MaskedIcon(
                Res.drawable.file_upload,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(uploadSpeedValue.toString()) },
        valueToText = { it.toString() },
    ) { value, onValueChange, onOk ->
        UIntegerTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    val downloadSpeedValue by DataStore.configurationStore
        .intFlow(Key.DOWNLOAD_SPEED, 0)
        .collectAsStateWithLifecycle(0)
    TextFieldPreference(
        value = downloadSpeedValue,
        onValueChange = {
            DataStore.downloadSpeed = it
            needReload()
        },
        title = { Text(stringResource(Res.string.hysteria_download_mbps)) },
        textToValue = { it.toIntOrNull() ?: 0 },
        icon = {
            MaskedIcon(Res.drawable.download, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(downloadSpeedValue.toString()) },
        valueToText = { it.toString() },
    ) { value, onValueChange, onOk ->
        UIntegerTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    fun pluginProviderText(index: Int): StringOrRes = when (index) {
        ProtocolProvider.CORE -> StringOrRes.Direct("sing-box")
        ProtocolProvider.PLUGIN -> StringOrRes.Res(Res.string.plugin)
        else -> StringOrRes.Direct("sing-box")
    }

    val hysteria2ProviderValue by DataStore.configurationStore
        .intFlow(Key.PROVIDER_HYSTERIA2, ProtocolProvider.CORE)
        .collectAsStateWithLifecycle(ProtocolProvider.CORE)
    ListPreference(
        value = hysteria2ProviderValue,
        onValueChange = {
            DataStore.providerHysteria2 = it
            needReload()
        },
        values = listOf(ProtocolProvider.CORE, ProtocolProvider.PLUGIN),
        title = { Text(stringResource(Res.string.hysteria2_provider)) },
        icon = {
            MaskedIcon(
                Res.drawable.flight_takeoff,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringOrRes(pluginProviderText(hysteria2ProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringOrRes(pluginProviderText(it))) },
    )
    PreferenceDivider()

    val juicityProviderValue by DataStore.configurationStore
        .intFlow(Key.PROVIDER_JUICITY, ProtocolProvider.PLUGIN)
        .collectAsStateWithLifecycle(ProtocolProvider.PLUGIN)
    ListPreference(
        value = juicityProviderValue,
        onValueChange = {
            DataStore.providerJuicity = it
            needReload()
        },
        values = listOf(ProtocolProvider.CORE, ProtocolProvider.PLUGIN),
        title = { Text(stringResource(Res.string.juicity_provider)) },
        icon = {
            MaskedIcon(
                Res.drawable.flight_takeoff,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringOrRes(pluginProviderText(juicityProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringOrRes(pluginProviderText(it))) },
    )
    PreferenceDivider()

    val naiveProviderValue by DataStore.configurationStore
        .intFlow(Key.PROVIDER_NAIVE, ProtocolProvider.CORE)
        .collectAsStateWithLifecycle(ProtocolProvider.CORE)
    ListPreference(
        value = naiveProviderValue,
        onValueChange = {
            DataStore.providerNaive = it
            needReload()
        },
        values = listOf(ProtocolProvider.CORE, ProtocolProvider.PLUGIN),
        title = { Text(stringResource(Res.string.provider_naive)) },
        icon = {
            MaskedIcon(
                Res.drawable.flight_takeoff,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringOrRes(pluginProviderText(naiveProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringOrRes(pluginProviderText(it))) },
    )
    if (isExpertState) {
        PreferenceDivider()
        val versionValue by DataStore.configurationStore
            .stringFlow(Key.ANYTLS_CUSTOM_VERSION, "")
            .collectAsStateWithLifecycle("")
        TextFieldPreference(
            value = versionValue,
            onValueChange = {
                DataStore.anytlsCustomVersion = it
                needRestart()
            },
            title = { Text("AnyTLS version") },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.local_bar,
                    color = IconMaskColors.IconLightPink,
                )
            },
            summary = { Text(contentOrUnset(versionValue)) },
            valueToText = { it },
        )
    }
}

@Composable
private fun DnsSettingsGroup(
    needReload: () -> Unit,
    fakeDNSState: Boolean,
) {
    val remoteDnsValue by DataStore.configurationStore
        .stringFlow(Key.REMOTE_DNS, "tcp://dns.google")
        .collectAsStateWithLifecycle("tcp://dns.google")
    TextFieldPreference(
        value = remoteDnsValue,
        onValueChange = {
            DataStore.remoteDns = it
            needReload()
        },
        title = { Text(stringResource(Res.string.remote_dns)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.dns, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(contentOrUnset(remoteDnsValue)) },
        valueToText = { it },
    )
    PreferenceDivider()

    val directDnsValue by DataStore.configurationStore
        .stringFlow(Key.DIRECT_DNS, "local")
        .collectAsStateWithLifecycle("local")
    TextFieldPreference(
        value = directDnsValue,
        onValueChange = {
            DataStore.directDns = it
            needReload()
        },
        title = { Text(stringResource(Res.string.direct_dns)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.dns, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(contentOrUnset(directDnsValue)) },
        valueToText = { it },
    )
    PreferenceDivider()

    val mdnsValue by DataStore.configurationStore
        .stringFlow(Key.MDNS, "")
        .collectAsStateWithLifecycle("")
    TextFieldPreference(
        value = mdnsValue,
        onValueChange = {
            DataStore.mDNS = it
            needReload()
        },
        title = { Text(stringResource(Res.string.mdns_network_interfaces)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.wifi, color = IconMaskColors.IconWarmGray)
        },
        summary = { Text(contentOrUnset(mdnsValue)) },
        valueToText = { it },
    )
    PreferenceDivider()

    val optimisticCacheValue by DataStore.configurationStore
        .stringFlow(Key.DNS_OPTIMISTIC_CACHE, "")
        .collectAsStateWithLifecycle("")
    TextFieldPreference(
        value = optimisticCacheValue,
        onValueChange = {
            DataStore.dnsOptimisticCache = it
            needReload()
        },
        title = { Text(stringResource(Res.string.optimistic_cache)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.emoji_emotions,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(contentOrUnset(optimisticCacheValue)) },
        valueToText = { it },
        textField = { value, onValueChange, onOk ->
            DurationTextField(value, onValueChange, onOk)
        },
    )
    PreferenceDivider()

    val domainStrategyDirectValue by DataStore.configurationStore
        .stringFlow(Key.DOMAIN_STRATEGY_FOR_DIRECT, "auto")
        .collectAsStateWithLifecycle("auto")
    val domainStrategyValues =
        listOf("auto", "prefer_ipv6", "prefer_ipv4", "ipv4_only", "ipv6_only")
    val domainStrategyEntries = listOf(
        stringResource(Res.string.auto),
        stringResource(Res.string.prefer_ipv6),
        stringResource(Res.string.prefer_ipv4),
        stringResource(Res.string.ipv4_only),
        stringResource(Res.string.ipv6_only),
    )
    ListPreference(
        value = domainStrategyDirectValue,
        onValueChange = {
            DataStore.domainStrategyForDirect = it
            needReload()
        },
        values = domainStrategyValues,
        title = { Text(stringResource(Res.string.domain_strategy_for_direct)) },
        icon = { Spacer(Modifier.size(24.dp)) },
        summary = {
            val selectedIndex =
                domainStrategyValues.indexOf(domainStrategyDirectValue).takeIf { it >= 0 } ?: 0
            Text(domainStrategyEntries[selectedIndex])
        },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = {
            val selectedIndex = domainStrategyValues.indexOf(it).takeIf { index -> index >= 0 } ?: 0
            AnnotatedString(domainStrategyEntries[selectedIndex])
        },
    )
    PreferenceDivider()

    val domainStrategyServerValue by DataStore.configurationStore
        .stringFlow(Key.DOMAIN_STRATEGY_FOR_SERVER, "auto")
        .collectAsStateWithLifecycle("auto")
    ListPreference(
        value = domainStrategyServerValue,
        onValueChange = {
            DataStore.domainStrategyForServer = it
            needReload()
        },
        values = domainStrategyValues,
        title = { Text(stringResource(Res.string.domain_strategy_for_server)) },
        icon = { Spacer(Modifier.size(24.dp)) },
        summary = {
            val selectedIndex =
                domainStrategyValues.indexOf(domainStrategyServerValue).takeIf { it >= 0 } ?: 0
            Text(domainStrategyEntries[selectedIndex])
        },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = {
            val selectedIndex = domainStrategyValues.indexOf(it).takeIf { index -> index >= 0 } ?: 0
            AnnotatedString(domainStrategyEntries[selectedIndex])
        },
    )
    PreferenceDivider()

    val enableFakeDnsValue by DataStore.configurationStore
        .booleanFlow(Key.ENABLE_FAKE_DNS, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = enableFakeDnsValue,
        onValueChange = {
            DataStore.enableFakeDns = it
            needReload()
        },
        title = { Text(stringResource(Res.string.fake_dns)) },
        icon = {
            MaskedIcon(Res.drawable.lock, color = IconMaskColors.IconLightPink)
        },
        summary = { Text(stringResource(Res.string.fakedns_message)) },
    )
    PreferenceDivider()

    val fakeDnsForAllValue by DataStore.configurationStore
        .booleanFlow(Key.FAKE_DNS_FOR_ALL, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = fakeDnsForAllValue,
        onValueChange = {
            DataStore.fakeDNSForAll = it
            needReload()
        },
        title = { Text(stringResource(Res.string.fake_dns_for_all)) },
        enabled = fakeDNSState,
        icon = {
            MaskedIcon(
                Res.drawable.lock,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.risk(),
            )
        },
        summary = { Text(stringResource(Res.string.fake_dns_for_all_sum)) },
    )
    PreferenceDivider()

    val fakeDnsRange4Value by DataStore.configurationStore
        .stringFlow(Key.FAKE_DNS_RANGE_4, "198.51.100.0/24")
        .collectAsStateWithLifecycle("198.51.100.0/24")
    TextFieldPreference(
        value = fakeDnsRange4Value,
        onValueChange = {
            DataStore.fakeDNSRange4 = it
            needReload()
        },
        title = { Text(stringResource(Res.string.fake_ip_range_4)) },
        textToValue = { it },
        enabled = fakeDNSState,
        icon = {
            MaskedIcon(
                Res.drawable.text_select_end,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = { Text(contentOrUnset(fakeDnsRange4Value)) },
        valueToText = { it },
    )
    PreferenceDivider()

    val fakeDnsRange6Value by DataStore.configurationStore
        .stringFlow(Key.FAKE_DNS_RANGE_6, "2001:2::/48")
        .collectAsStateWithLifecycle("2001:2::/48")
    TextFieldPreference(
        value = fakeDnsRange6Value,
        onValueChange = {
            DataStore.fakeDNSRange6 = it
            needReload()
        },
        title = { Text(stringResource(Res.string.fake_ip_range_6)) },
        textToValue = { it },
        enabled = fakeDNSState,
        icon = {
            MaskedIcon(
                Res.drawable.text_select_end,
                color = IconMaskColors.IconWarmGray,
            )
        },
        summary = { Text(contentOrUnset(fakeDnsRange6Value)) },
        valueToText = { it },
    )
    PreferenceDivider()

    val dnsHostsValue by DataStore.configurationStore
        .stringFlow(Key.DNS_HOSTS, "")
        .collectAsStateWithLifecycle("")
    TextFieldPreference(
        value = dnsHostsValue,
        onValueChange = {
            DataStore.dnsHosts = it
            needReload()
        },
        title = { Text(stringResource(Res.string.dns_hosts)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.transform, color = IconMaskColors.IconCyan)
        },
        summary = { Text(contentOrUnset(dnsHostsValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        HostTextField(value, onValueChange, onOk)
    }
}

@Composable
private fun InboundSettingsGroup(
    needReload: () -> Unit,
    appendHttpProxyState: Boolean,
    isExpertState: Boolean,
) {
    val mixedPortValue by DataStore.configurationStore
        .stringFlow(Key.MIXED_PORT, "2080")
        .collectAsStateWithLifecycle("2080")
    TextFieldPreference(
        value = mixedPortValue,
        onValueChange = {
            DataStore.mixedPort = it.toIntOrNull() ?: 2080
            needReload()
        },
        title = { Text(stringResource(Res.string.port_proxy)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.directions_boat,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(contentOrUnset(mixedPortValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        PortTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    val localDnsPortValue by DataStore.configurationStore
        .stringFlow(Key.LOCAL_DNS_PORT, "0")
        .collectAsStateWithLifecycle("0")
    TextFieldPreference(
        value = localDnsPortValue,
        onValueChange = {
            DataStore.localDNSPort = it.toIntOrNull() ?: 0
            needReload()
        },
        title = { Text(stringResource(Res.string.port_local_dns)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.apps, color = IconMaskColors.IconWarmGray)
        },
        summary = { Text(contentOrUnset(localDnsPortValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        PortTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    val appendHttpProxyValue by DataStore.configurationStore
        .booleanFlow(Key.APPEND_HTTP_PROXY, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = appendHttpProxyValue,
        onValueChange = {
            DataStore.appendHttpProxy = it
            needReload()
        },
        title = { Text(stringResource(Res.string.append_http_proxy)) },
        icon = {
            MaskedIcon(
                Res.drawable.app_registration,
                color = IconMaskColors.IconLightGreen,
            )
        },
        summary = {
            if (PlatformInfo.isAndroid) {
                Text(stringResource(Res.string.append_http_proxy_sum))
            }
        },
    )
    PreferenceDivider()

    HttpProxyBypassPreference(appendHttpProxyState, needReload)
    PreferenceDivider()

    val allowAccessValue by DataStore.configurationStore
        .booleanFlow(Key.ALLOW_ACCESS, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = allowAccessValue,
        onValueChange = {
            DataStore.allowAccess = it
            needReload()
        },
        title = { Text(stringResource(Res.string.allow_access)) },
        icon = {
            MaskedIcon(Res.drawable.nat, color = IconMaskColors.IconCoral)
        },
        summary = { Text(stringResource(Res.string.allow_access_sum)) },
    )
    PreferenceDivider()

    val inboundUsernameValue by DataStore.configurationStore
        .stringFlow(Key.INBOUND_USERNAME, "")
        .collectAsStateWithLifecycle("")
    TextFieldPreference(
        value = inboundUsernameValue,
        onValueChange = {
            DataStore.inboundUsername = it
            needReload()
        },
        title = { Text(stringResource(Res.string.inbound_username)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.person, color = IconMaskColors.IconCyan)
        },
        summary = { Text(contentOrUnset(inboundUsernameValue)) },
        valueToText = { it },
    )
    PreferenceDivider()

    val inboundPasswordValue by DataStore.configurationStore
        .stringFlow(Key.INBOUND_PASSWORD, "")
        .collectAsStateWithLifecycle("")
    PasswordPreference(
        value = inboundPasswordValue,
        onValueChange = {
            DataStore.inboundPassword = it
            needReload()
        },
        title = { Text(stringResource(Res.string.inbound_password)) },
    )
    if (isExpertState) {
        PreferenceDivider()
        val anchorSSIDValue by DataStore.configurationStore
            .stringFlow(Key.ANCHOR_SSID, "")
            .collectAsStateWithLifecycle("")
        TextFieldPreference(
            value = anchorSSIDValue,
            onValueChange = {
                DataStore.anchorSSID = it
                needReload()
            },
            title = { Text("Anchor SSIDs") },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.wifi, color = IconMaskColors.IconCoral)
            },
            summary = { Text(contentOrUnset(anchorSSIDValue)) },
            valueToText = { it },
        )
    }
}

@Composable
private fun MiscSettingsGroup(
    needReload: () -> Unit,
    needRestart: () -> Unit,
) {
    val connectionTestUrlValue by DataStore.configurationStore
        .stringFlow(Key.CONNECTION_TEST_URL, CONNECTION_TEST_URL)
        .collectAsStateWithLifecycle(CONNECTION_TEST_URL)
    TextFieldPreference(
        value = connectionTestUrlValue,
        onValueChange = { DataStore.connectionTestURL = it },
        title = { Text(stringResource(Res.string.connection_test_url)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.cast_connected,
                color = IconMaskColors.IconCyan,
            )
        },
        summary = { Text(contentOrUnset(connectionTestUrlValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        LinkOrContentTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    val connectionTestConcurrentValue by DataStore.configurationStore
        .intFlow(Key.CONNECTION_TEST_CONCURRENT, 5)
        .collectAsStateWithLifecycle(5)
    var concurrentPreview by remember { mutableFloatStateOf(connectionTestConcurrentValue.toFloat()) }
    SliderPreference(
        value = connectionTestConcurrentValue.toFloat(),
        onValueChange = { DataStore.connectionTestConcurrent = it.toInt() },
        sliderValue = concurrentPreview,
        onSliderValueChange = { concurrentPreview = it },
        title = { Text(stringResource(Res.string.test_concurrency)) },
        valueRange = 1f..32f,
        valueSteps = 32,
        icon = {
            MaskedIcon(
                Res.drawable.fast_forward,
                color = IconMaskColors.IconLightGreen,
            )
        },
        valueText = { Text(concurrentPreview.toInt().toString()) },
    )
    PreferenceDivider()

    val connectionTestTimeoutValue by DataStore.configurationStore
        .intFlow(Key.CONNECTION_TEST_TIMEOUT, 3000)
        .collectAsStateWithLifecycle(3000)
    var timeoutPreview by remember { mutableFloatStateOf(connectionTestTimeoutValue.toFloat()) }
    SliderPreference(
        value = connectionTestTimeoutValue.toFloat(),
        onValueChange = { DataStore.connectionTestTimeout = it.toInt() },
        sliderValue = timeoutPreview,
        onSliderValueChange = { timeoutPreview = it },
        title = { Text(stringResource(Res.string.test_timeout)) },
        valueRange = 1024f..8192f,
        valueSteps = 20,
        icon = {
            MaskedIcon(Res.drawable.apps, color = IconMaskColors.IconWarmGray)
        },
        valueText = { Text(timeoutPreview.toInt().toString()) },
    )
    PreferenceDivider()

    val connectionTestUnifiedDelay by DataStore.configurationStore
        .booleanFlow(Key.CONNECTION_TEST_UNIFIED_DELAY, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = connectionTestUnifiedDelay,
        onValueChange = {
            DataStore.connectionTestUnifiedDelay = it
            needReload()
        },
        title = { Text(stringResource(Res.string.connection_test_unified_delay)) },
        icon = {
            MaskedIcon(Res.drawable.timer, IconMaskColors.IconLightGreen)
        },
    )
    PreferenceDivider()

    val connectionTestIgnoreHandshakeTime by DataStore.configurationStore
        .booleanFlow(Key.CONNECTION_TEST_IGNORE_HANDSHAKE_TIME, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = connectionTestIgnoreHandshakeTime,
        onValueChange = {
            DataStore.connectionTestIgnoreHandshakeTime = it
            needReload()
        },
        title = { Text(stringResource(Res.string.connection_test_ignore_handshake_time)) },
        icon = {
            MaskedIcon(Res.drawable.question_mark, IconMaskColors.IconLightGreen)
        },
    )
    if (PlatformInfo.isAndroid) {
        PreferenceDivider()
        PlatformMiscOptions(needReload)
    }
    PreferenceDivider()

    val certProviderValue by DataStore.configurationStore
        .intFlow(Key.CERT_PROVIDER, CertProvider.MOZILLA)
        .collectAsStateWithLifecycle(CertProvider.MOZILLA)

    fun certProviderTextRes(index: Int): StringResource = when (index) {
        CertProvider.SYSTEM -> Res.string.follow_system
        CertProvider.MOZILLA -> Res.string.mozilla
        CertProvider.SYSTEM_AND_USER -> Res.string.system_and_user
        CertProvider.CHROME -> Res.string.cert_chrome
        else -> Res.string.mozilla
    }
    ListPreference(
        value = certProviderValue,
        onValueChange = {
            DataStore.certProvider = it
            needRestart()
        },
        values = listOf(
            CertProvider.SYSTEM,
            CertProvider.MOZILLA,
            CertProvider.SYSTEM_AND_USER,
            CertProvider.CHROME,
        ),
        title = { Text(stringResource(Res.string.certificate_authority)) },
        icon = {
            MaskedIcon(
                Res.drawable.push_pin,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.credential(),
            )
        },
        summary = { Text(stringResource(certProviderTextRes(certProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(certProviderTextRes(it))) },
    )
    PreferenceDivider()

    DisableProcessTextPreference()
}

@Composable
private fun NtpSettingsGroup(
    needReload: () -> Unit,
    ntpEnableState: Boolean,
) {
    val enableNtpValue by DataStore.configurationStore
        .booleanFlow(Key.ENABLE_NTP, false)
        .collectAsStateWithLifecycle(false)
    SwitchPreference(
        value = enableNtpValue,
        onValueChange = {
            DataStore.ntpEnable = it
            needReload()
        },
        title = { Text(stringResource(Res.string.enable_ntp)) },
        icon = {
            MaskedIcon(
                Res.drawable.timelapse,
                color = IconMaskColors.IconLightPink,
            )
        },
        summary = { Text(stringResource(Res.string.ntp_sum)) },
    )
    PreferenceDivider()

    val ntpServerValue by DataStore.configurationStore
        .stringFlow(Key.NTP_SERVER, "time.apple.com")
        .collectAsStateWithLifecycle("time.apple.com")
    TextFieldPreference(
        value = ntpServerValue,
        onValueChange = {
            DataStore.ntpAddress = it
            needReload()
        },
        title = { Text(stringResource(Res.string.ntp_server_address)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.router, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(contentOrUnset(ntpServerValue)) },
        valueToText = { it },
        enabled = ntpEnableState,
    )
    PreferenceDivider()

    val ntpPortValue by DataStore.configurationStore
        .intFlow(Key.NTP_PORT, 123)
        .collectAsStateWithLifecycle(123)
    TextFieldPreference(
        value = ntpPortValue,
        onValueChange = {
            DataStore.ntpPort = it
            needReload()
        },
        title = { Text(stringResource(Res.string.ntp_server_port)) },
        textToValue = { it.toIntOrNull() ?: 123 },
        icon = {
            MaskedIcon(
                Res.drawable.directions_boat,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(ntpPortValue.toString()) },
        valueToText = { it.toString() },
        enabled = ntpEnableState,
    ) { value, onValueChange, onOk ->
        PortTextField(value, onValueChange, onOk)
    }
    PreferenceDivider()

    val ntpIntervalValue by DataStore.configurationStore
        .stringFlow(Key.NTP_INTERVAL, "30m")
        .collectAsStateWithLifecycle("30m")
    TextFieldPreference(
        value = ntpIntervalValue,
        onValueChange = {
            DataStore.ntpInterval = it
            needReload()
        },
        title = { Text(stringResource(Res.string.ntp_sync_interval)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.flip_camera_android,
                color = IconMaskColors.IconCyan,
            )
        },
        summary = { Text(contentOrUnset(ntpIntervalValue)) },
        valueToText = { it },
        enabled = ntpEnableState,
    ) { value, onValueChange, onOk ->
        DurationTextField(value, onValueChange, onOk)
    }
}

@Composable
internal expect fun PlatformGeneralOptions(needReload: () -> Unit)

@Composable
private fun Circle(
    modifier: Modifier = Modifier,
    color: Color,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier.background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = vectorResource(Res.drawable.check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
