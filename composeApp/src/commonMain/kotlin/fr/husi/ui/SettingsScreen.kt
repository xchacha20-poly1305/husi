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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import fr.husi.compose.LinkOrContentTextField
import fr.husi.compose.PasswordPreference
import fr.husi.compose.PlatformMenuIcon
import fr.husi.compose.PortTextField
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceType
import fr.husi.compose.SagerFab
import fr.husi.compose.SimpleTopAppBar
import fr.husi.compose.StatsBar
import fr.husi.compose.TextButton
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.rememberScrollHideState
import fr.husi.compose.theme.DEFAULT
import fr.husi.compose.theme.themeString
import fr.husi.compose.theme.themes
import fr.husi.compose.withNavigation
import fr.husi.database.DataStore
import fr.husi.database.SagerDatabase
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.ktx.isExpert
import fr.husi.ktx.onIoDispatcher
import fr.husi.ktx.restartApplication
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.showAndDismissOld
import fr.husi.logLevelString
import fr.husi.repository.repo
import fr.husi.resources.Res
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
import fr.husi.resources.connection_test_url
import fr.husi.resources.construction
import fr.husi.resources.custom_rule_provider
import fr.husi.resources.description
import fr.husi.resources.developer_mode
import fr.husi.resources.direct_dns
import fr.husi.resources.directions_boat
import fr.husi.resources.disable
import fr.husi.resources.dns
import fr.husi.resources.dns_hosts
import fr.husi.resources.domain_strategy_for_direct
import fr.husi.resources.domain_strategy_for_server
import fr.husi.resources.download
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
import fr.husi.resources.language
import fr.husi.resources.language_system_default
import fr.husi.resources.lock
import fr.husi.resources.log_level
import fr.husi.resources.long_click_to_see_name
import fr.husi.resources.max_log_line
import fr.husi.resources.menu
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
import fr.husi.resources.person
import fr.husi.resources.plugin
import fr.husi.resources.port_local_dns
import fr.husi.resources.port_proxy
import fr.husi.resources.prefer_ipv4
import fr.husi.resources.prefer_ipv6
import fr.husi.resources.profile_traffic_statistics
import fr.husi.resources.profile_traffic_statistics_summary
import fr.husi.resources.protocol_settings
import fr.husi.resources.provider_naive
import fr.husi.resources.public_icon
import fr.husi.resources.push_pin
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
import fr.husi.resources.speed
import fr.husi.resources.system_and_user
import fr.husi.resources.test_concurrency
import fr.husi.resources.test_timeout
import fr.husi.resources.text_select_end
import fr.husi.resources.theme
import fr.husi.resources.timelapse
import fr.husi.resources.traffic
import fr.husi.resources.transform
import fr.husi.resources.transgender
import fr.husi.resources.translate
import fr.husi.resources.tun_implementation
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

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

    LaunchedEffect(Unit) {
        onIoDispatcher {
            DataStore.initGlobal()
        }
    }

    fun needReload() = scope.launch {
        if (!DataStore.serviceState.started) return@launch
        val result = snackbarState.showAndDismissOld(
            message = repo.getString(Res.string.need_reload),
            actionLabel = repo.getString(Res.string.apply),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.Dismissed) return@launch
        repo.reloadService()
    }

    fun needRestart() = scope.launch {
        val result = snackbarState.showAndDismissOld(
            message = repo.getString(Res.string.need_restart),
            actionLabel = repo.getString(Res.string.apply),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.Dismissed) return@launch
        repo.stopService()
        runOnDefaultDispatcher {
            delay(500)
            SagerDatabase.instance.close()
            Executable.killAll(true)
            restartApplication()
        }
    }

    // Dependency states for enable/visibility linking
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

    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
                navigationIcon = {
                    PlatformMenuIcon(
                        imageVector = vectorResource(Res.drawable.menu),
                        contentDescription = stringResource(Res.string.menu),
                        onClick = onDrawerClick,
                    )
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            SagerFab(
                visible = scrollHideVisible,
                state = serviceStatus.state,
                showSnackbar = { message ->
                    scope.launch {
                        snackbarState.showSnackbar(
                            message = getStringOrRes(message),
                            actionLabel = repo.getString(Res.string.ok),
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
                        .fillMaxHeight(),
                    contentPadding = contentPadding,
                ) {
                    item(Key.GENERAL_SETTINGS, PreferenceType.CATEGORY) {
                        PreferenceCategory(text = { Text(stringResource(Res.string.general_settings)) })
                    }
                    autoConnect()
                    colorPickerPreference(
                        key = Key.APP_THEME,
                        title = { Text(stringResource(Res.string.theme)) },
                    )
                    item(Key.NIGHT_THEME, PreferenceType.LIST) {
                        fun nightString(index: Int): StringResource = when (index) {
                            0 -> Res.string.follow_system
                            1 -> Res.string.enable
                            2 -> Res.string.disable
                            3 -> Res.string.auto
                            else -> Res.string.follow_system
                        }

                        val value by DataStore.configurationStore
                            .intFlow(Key.NIGHT_THEME, 0)
                            .collectAsStateWithLifecycle(0)
                        ListPreference(
                            value = value,
                            onValueChange = {
                                DataStore.nightTheme = it
                                applyNightMode(it)
                            },
                            values = intListN(4),
                            title = { Text(stringResource(Res.string.night_mode)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.wb_sunny),
                                    null,
                                )
                            },
                            summary = { Text(stringResource(nightString(value))) },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = {
                                val text = runBlocking { repo.getString(nightString(it)) }
                                AnnotatedString(text)
                            },
                        )
                    }
                    item(Key.APP_LANGUAGE, PreferenceType.LIST) {
                        fun getLanguageDisplayName(tag: String): String =
                            AppLanguage.fromTag(tag)?.nativeName ?: runBlocking {
                                repo.getString(Res.string.language_system_default)
                            }

                        val values = AppLanguage.entries.map { it.tag }
                        val languageController = rememberAppLanguageController(defaultTag = "")
                        val appLanguage by languageController.flow
                            .collectAsStateWithLifecycle(languageController.value)
                        val selectedValue = if (appLanguage in values) {
                            appLanguage
                        } else {
                            ""
                        }

                        ListPreference(
                            value = selectedValue,
                            onValueChange = { newValue ->
                                languageController.value = newValue
                            },
                            values = values,
                            title = { Text(stringResource(Res.string.language)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.translate),
                                    null,
                                )
                            },
                            summary = { Text(getLanguageDisplayName(selectedValue)) },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = { AnnotatedString(getLanguageDisplayName(it)) },
                        )
                    }
                    item(Key.SERVICE_MODE, PreferenceType.LIST) {
                        fun serviceModeText(mode: String): StringResource = when (mode) {
                            Key.MODE_VPN -> Res.string.service_mode_vpn
                            Key.MODE_PROXY -> Res.string.service_mode_proxy
                            else -> Res.string.service_mode_vpn
                        }

                        val values = listOf(Key.MODE_VPN, Key.MODE_PROXY)
                        val stored by DataStore.configurationStore
                            .stringFlow(Key.SERVICE_MODE, Key.MODE_VPN)
                            .collectAsStateWithLifecycle(Key.MODE_VPN)

                        ListPreference(
                            value = stored,
                            onValueChange = {
                                DataStore.serviceMode = it
                                needReload()
                            },
                            values = values,
                            title = { Text(stringResource(Res.string.service_mode)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.developer_mode),
                                    null,
                                )
                            },
                            summary = { Text(stringResource(serviceModeText(stored))) },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = {
                                val text = runBlocking { repo.getString(serviceModeText(it)) }
                                AnnotatedString(text)
                            },
                        )
                    }
                    item(Key.TUN_IMPLEMENTATION, PreferenceType.LIST) {
                        fun tunImplText(value: Int): String = when (value) {
                            TunImplementation.GVISOR -> "gVisor"
                            TunImplementation.SYSTEM -> "System"
                            TunImplementation.MIXED -> "Mixed"
                            else -> error("impossible")
                        }

                        val value by DataStore.configurationStore
                            .intFlow(Key.TUN_IMPLEMENTATION, TunImplementation.MIXED)
                            .collectAsStateWithLifecycle(TunImplementation.MIXED)

                        ListPreference(
                            value = value,
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
                                Icon(
                                    vectorResource(Res.drawable.flip_camera_android),
                                    null,
                                )
                            },
                            summary = { Text(tunImplText(value)) },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = { AnnotatedString(tunImplText(it)) },
                        )
                    }
                    item(Key.MTU, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .intFlow(Key.MTU, 9000)
                            .collectAsStateWithLifecycle(9000)
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.mtu = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.mtu)) },
                            textToValue = { it.toIntOrNull() ?: 9000 },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.public_icon),
                                    null,
                                )
                            },
                            summary = { Text(value.toString()) },
                            valueToText = { it.toString() },
                        )
                    }
                    platformGeneralOptions { needReload() }
                    item(Key.PROFILE_TRAFFIC_STATISTICS, PreferenceType.SWITCH) {
                        val value by DataStore.configurationStore
                            .booleanFlow(Key.PROFILE_TRAFFIC_STATISTICS, true)
                            .collectAsStateWithLifecycle(true)
                        SwitchPreference(
                            value = value,
                            onValueChange = { DataStore.profileTrafficStatistics = it },
                            title = { Text(stringResource(Res.string.profile_traffic_statistics)) },
                            icon = { Icon(vectorResource(Res.drawable.traffic), null) },
                            summary = { Text(stringResource(Res.string.profile_traffic_statistics_summary)) },
                            enabled = speedIntervalState != 0,
                        )
                    }
                    item(Key.SHOW_DIRECT_SPEED, PreferenceType.SWITCH) {
                        val value by DataStore.configurationStore
                            .booleanFlow(Key.SHOW_DIRECT_SPEED, true)
                            .collectAsStateWithLifecycle(true)
                        SwitchPreference(
                            value = value,
                            onValueChange = { DataStore.showDirectSpeed = it },
                            title = { Text(stringResource(Res.string.show_direct_speed)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.speed),
                                    null,
                                )
                            },
                            summary = { Text(stringResource(Res.string.show_direct_speed_sum)) },
                            enabled = speedIntervalState != 0,
                        )
                    }
                    item(Key.ALWAYS_SHOW_ADDRESS, PreferenceType.SWITCH) {
                        val value by DataStore.configurationStore
                            .booleanFlow(Key.ALWAYS_SHOW_ADDRESS, false)
                            .collectAsStateWithLifecycle(false)
                        SwitchPreference(
                            value = value,
                            onValueChange = { DataStore.alwaysShowAddress = it },
                            title = { Text(stringResource(Res.string.always_show_address)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.center_focus_weak),
                                    null,
                                )
                            },
                            summary = { Text(stringResource(Res.string.always_show_address_sum)) },
                        )
                    }
                    item(Key.BLURRED_ADDRESS, PreferenceType.SWITCH) {
                        val value by DataStore.configurationStore
                            .booleanFlow(Key.BLURRED_ADDRESS, false)
                            .collectAsStateWithLifecycle(false)
                        SwitchPreference(
                            value = value,
                            onValueChange = { DataStore.blurredAddress = it },
                            title = { Text(stringResource(Res.string.blurred_address)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.transgender),
                                    null,
                                )
                            },
                            enabled = alwaysShowAddressState,
                        )
                    }
                    item(Key.SECURITY_ADVISORY, PreferenceType.SWITCH) {
                        val value by DataStore.configurationStore
                            .booleanFlow(Key.SECURITY_ADVISORY, true)
                            .collectAsStateWithLifecycle(true)
                        SwitchPreference(
                            value = value,
                            onValueChange = { DataStore.securityAdvisory = it },
                            title = { Text(stringResource(Res.string.insecure_warn)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.security),
                                    null,
                                )
                            },
                        )
                    }
                    meteredNetworkSetting { needReload() }
                    item(Key.LOG_LEVEL, PreferenceType.LIST) {
                        val value by DataStore.configurationStore
                            .intFlow(Key.LOG_LEVEL, 3)
                            .collectAsStateWithLifecycle(3)
                        ListPreference(
                            value = value,
                            onValueChange = {
                                DataStore.logLevel = it
                                needRestart()
                            },
                            values = intListN(7),
                            title = { Text(stringResource(Res.string.log_level)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.bug_report),
                                    null,
                                )
                            },
                            summary = { Text(logLevelString(value)) },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = { AnnotatedString(logLevelString(it)) },
                        )
                    }
                    item(Key.LOG_MAX_LINE, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .intFlow(Key.LOG_MAX_LINE, 1024)
                            .collectAsStateWithLifecycle(1024)
                        var previewValue by remember { mutableFloatStateOf(value.toFloat()) }
                        SliderPreference(
                            value = value.toFloat(),
                            onValueChange = { DataStore.logMaxLine = it.toInt() },
                            sliderValue = previewValue,
                            onSliderValueChange = { previewValue = it },
                            title = { Text(stringResource(Res.string.max_log_line)) },
                            valueRange = 1024f..1024f * 64f,
                            valueSteps = 128,
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.description),
                                    null,
                                )
                            },
                            valueText = { Text(previewValue.toInt().toString()) },
                        )
                    }

                    item(Key.ROUTE_SETTINGS, PreferenceType.CATEGORY) {
                        PreferenceCategory(text = { Text(stringResource(Res.string.route_options)) })
                    }
                    proxyAppsPreferences(openAppManager)
                    platformRouteOptions(
                        needReload = { needReload() },
                        isVpnMode = serviceModeState == Key.MODE_VPN,
                    )
                    item(Key.NETWORK_STRATEGY, PreferenceType.LIST) {
                        val values =
                            listOf("", "prefer_ipv6", "prefer_ipv4", "ipv4_only", "ipv6_only")

                        fun networkStrategyTextRes(value: String): StringResource = when (value) {
                            "" -> Res.string.auto
                            "prefer_ipv6" -> Res.string.prefer_ipv6
                            "prefer_ipv4" -> Res.string.prefer_ipv4
                            "ipv4_only" -> Res.string.ipv4_only
                            "ipv6_only" -> Res.string.ipv6_only
                            else -> Res.string.auto
                        }

                        val stored by DataStore.configurationStore
                            .stringFlow(Key.NETWORK_STRATEGY, "")
                            .collectAsStateWithLifecycle("")

                        ListPreference(
                            value = stored,
                            onValueChange = {
                                DataStore.networkStrategy = it
                                needReload()
                            },
                            values = values,
                            title = { Text(stringResource(Res.string.network_strategy)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.router),
                                    null,
                                )
                            },
                            summary = { Text(stringResource(networkStrategyTextRes(stored))) },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = {
                                val text =
                                    runBlocking { repo.getString(networkStrategyTextRes(it)) }
                                AnnotatedString(text)
                            },
                        )
                    }
                    item(Key.NETWORK_INTERFACE_STRATEGY, PreferenceType.LIST) {
                        fun networkInterfaceStrategyTextRes(selection: Int): StringResource =
                            when (selection) {
                                NetworkInterfaceStrategy.DEFAULT -> Res.string.keep_default
                                NetworkInterfaceStrategy.HYBRID -> Res.string.hybrid
                                NetworkInterfaceStrategy.FALLBACK -> Res.string.fallback
                                else -> Res.string.keep_default
                            }

                        val value by DataStore.configurationStore
                            .intFlow(
                                Key.NETWORK_INTERFACE_STRATEGY,
                                NetworkInterfaceStrategy.DEFAULT,
                            )
                            .collectAsStateWithLifecycle(NetworkInterfaceStrategy.DEFAULT)

                        ListPreference(
                            value = value,
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
                                Icon(
                                    vectorResource(Res.drawable.construction),
                                    null,
                                )
                            },
                            summary = { Text(stringResource(networkInterfaceStrategyTextRes(value))) },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = {
                                val text =
                                    runBlocking { repo.getString(networkInterfaceStrategyTextRes(it)) }
                                AnnotatedString(text)
                            },
                        )
                    }
                    item(
                        Key.NETWORK_PREFERRED_INTERFACES,
                        PreferenceType.MULTI_SELECT_LIST,
                    ) {
                        val values = listOf("wifi", "cellular", "ethernet", "other")
                        val selected by DataStore.configurationStore
                            .stringSetFlow(Key.NETWORK_PREFERRED_INTERFACES, emptySet())
                            .collectAsStateWithLifecycle(emptySet())
                        MultiSelectListPreference(
                            value = selected,
                            onValueChange = {
                                DataStore.networkPreferredInterfaces = it
                                needReload()
                            },
                            values = values,
                            title = { Text(stringResource(Res.string.network_interface_preference)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.public_icon),
                                    null,
                                )
                            },
                            summary = {
                                val text = if (selected.isEmpty()) {
                                    stringResource(Res.string.not_set)
                                } else selected.joinToString("\n")
                                Text(text)
                            },
                            valueToText = { AnnotatedString(it) },
                        )
                    }
                    /*item(Key.FORCED_SEARCH_PROCESS, PreferenceType.SWITCH) {
                        val value by DataStore.configurationStore
                            .booleanFlow(Key.FORCED_SEARCH_PROCESS, false)
                            .collectAsStateWithLifecycle(false)
                        SwitchPreference(
                            value = value,
                            onValueChange = {
                                DataStore.forcedSearchProcess = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.forced_search_process)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.android),
                                    null,
                                )
                            },
                        )
                    }*/
                    item(Key.RULES_PROVIDER, PreferenceType.LIST) {
                        fun rulesProviderText(index: Int): StringOrRes = when (index) {
                            RuleProvider.OFFICIAL -> StringOrRes.Res(Res.string.route_rules_official)
                            RuleProvider.LOYALSOLDIER -> {
                                StringOrRes.Direct("Loyalsoldier (xchacha20-poly1305/sing-geo*)")
                            }

                            RuleProvider.CHOCOLATE4U -> {
                                StringOrRes.Direct("Chocolate4U/Iran-sing-box-rules")
                            }

                            RuleProvider.CUSTOM -> StringOrRes.Res(Res.string.custom_rule_provider)
                            else -> StringOrRes.Res(Res.string.route_rules_official)
                        }

                        val value by DataStore.configurationStore
                            .intFlow(Key.RULES_PROVIDER, RuleProvider.OFFICIAL)
                            .collectAsStateWithLifecycle(RuleProvider.OFFICIAL)

                        ListPreference(
                            value = value,
                            onValueChange = { DataStore.rulesProvider = it },
                            values = listOf(
                                RuleProvider.OFFICIAL,
                                RuleProvider.LOYALSOLDIER,
                                RuleProvider.CHOCOLATE4U,
                                RuleProvider.CUSTOM,
                            ),
                            title = { Text(stringResource(Res.string.route_rules_provider)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.rule_folder),
                                    null,
                                )
                            },
                            summary = { Text(stringOrRes(rulesProviderText(value))) },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = {
                                val text = runBlocking { getStringOrRes(rulesProviderText(it)) }
                                AnnotatedString(text)
                            },
                        )
                    }
                    if (rulesProviderState == RuleProvider.CUSTOM) item(
                        Key.CUSTOM_RULE_PROVIDER,
                        PreferenceType.TEXT_FIELD,
                    ) {
                        val defaultUrl =
                            "https://codeload.github.com/SagerNet/sing-geosite/tar.gz/refs/heads/rule-set"
                        val value by DataStore.configurationStore
                            .stringFlow(Key.CUSTOM_RULE_PROVIDER, defaultUrl)
                            .collectAsStateWithLifecycle(defaultUrl)

                        TextFieldPreference(
                            value = value,
                            onValueChange = { DataStore.customRuleProvider = it },
                            title = { Text(stringResource(Res.string.custom_rule_provider)) },
                            textToValue = { it },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.import_contacts),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(value)) },
                            valueToText = { it },
                        ) { value, onValueChange, onOk ->
                            LinkOrContentTextField(value, onValueChange, onOk)
                        }
                    }

                    item(Key.PROTOCOL_SETTINGS, PreferenceType.CATEGORY) {
                        PreferenceCategory(text = { Text(stringResource(Res.string.protocol_settings)) })
                    }
                    item(Key.UPLOAD_SPEED, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .intFlow(Key.UPLOAD_SPEED, 0)
                            .collectAsStateWithLifecycle(0)
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.uploadSpeed = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.hysteria_upload_mbps)) },
                            textToValue = { it.toIntOrNull() ?: 0 },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.file_upload),
                                    null,
                                )
                            },
                            summary = { Text(value.toString()) },
                            valueToText = { it.toString() },
                        ) { value, onValueChange, onOk ->
                            UIntegerTextField(value, onValueChange, onOk)
                        }
                    }
                    item(Key.DOWNLOAD_SPEED, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .intFlow(Key.DOWNLOAD_SPEED, 0)
                            .collectAsStateWithLifecycle(0)
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.downloadSpeed = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.hysteria_download_mbps)) },
                            textToValue = { it.toIntOrNull() ?: 0 },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.download),
                                    null,
                                )
                            },
                            summary = { Text(value.toString()) },
                            valueToText = { it.toString() },
                        ) { value, onValueChange, onOk ->
                            UIntegerTextField(value, onValueChange, onOk)
                        }
                    }
                    fun pluginProviderText(index: Int): StringOrRes = when (index) {
                        ProtocolProvider.CORE -> StringOrRes.Direct("sing-box")
                        ProtocolProvider.PLUGIN -> StringOrRes.Res(Res.string.plugin)
                        else -> StringOrRes.Direct("sing-box")
                    }
                    item(Key.PROVIDER_HYSTERIA2, PreferenceType.LIST) {
                        val value by DataStore.configurationStore
                            .intFlow(Key.PROVIDER_HYSTERIA2, ProtocolProvider.CORE)
                            .collectAsStateWithLifecycle(ProtocolProvider.CORE)

                        ListPreference(
                            value = value,
                            onValueChange = {
                                DataStore.providerHysteria2 = it
                                needReload()
                            },
                            values = listOf(ProtocolProvider.CORE, ProtocolProvider.PLUGIN),
                            title = { Text(stringResource(Res.string.hysteria2_provider)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.flight_takeoff),
                                    null,
                                )
                            },
                            summary = { Text(stringOrRes(pluginProviderText(value))) },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = {
                                val text = runBlocking { getStringOrRes(pluginProviderText(it)) }
                                AnnotatedString(text)
                            },
                        )
                    }
                    item(Key.PROVIDER_JUICITY, PreferenceType.LIST) {
                        val value by DataStore.configurationStore
                            .intFlow(Key.PROVIDER_JUICITY, ProtocolProvider.PLUGIN)
                            .collectAsStateWithLifecycle(ProtocolProvider.PLUGIN)

                        ListPreference(
                            value = value,
                            onValueChange = {
                                DataStore.providerJuicity = it
                                needReload()
                            },
                            values = listOf(ProtocolProvider.CORE, ProtocolProvider.PLUGIN),
                            title = { Text(stringResource(Res.string.juicity_provider)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.flight_takeoff),
                                    null,
                                )
                            },
                            summary = { Text(stringOrRes(pluginProviderText(value))) },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = {
                                val text = runBlocking { getStringOrRes(pluginProviderText(it)) }
                                AnnotatedString(text)
                            },
                        )
                    }
                    item(Key.PROVIDER_NAIVE, PreferenceType.LIST) {
                        val value by DataStore.configurationStore
                            .intFlow(Key.PROVIDER_NAIVE, ProtocolProvider.CORE)
                            .collectAsStateWithLifecycle(ProtocolProvider.CORE)

                        ListPreference(
                            value = value,
                            onValueChange = {
                                DataStore.providerNaive = it
                                needReload()
                            },
                            values = listOf(ProtocolProvider.CORE, ProtocolProvider.PLUGIN),
                            title = { Text(stringResource(Res.string.provider_naive)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.flight_takeoff),
                                    null,
                                )
                            },
                            summary = { Text(stringOrRes(pluginProviderText(value))) },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = {
                                val text = runBlocking { getStringOrRes(pluginProviderText(it)) }
                                AnnotatedString(text)
                            },
                        )
                    }

                    item(Key.DNS_SETTINGS, PreferenceType.CATEGORY) {
                        PreferenceCategory(text = { Text(stringResource(Res.string.cag_dns)) })
                    }
                    item(Key.REMOTE_DNS, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.REMOTE_DNS, "tcp://dns.google")
                            .collectAsStateWithLifecycle("tcp://dns.google")
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.remoteDns = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.remote_dns)) },
                            textToValue = { it },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.dns),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(value)) },
                            valueToText = { it },
                        )
                    }
                    item(Key.DIRECT_DNS, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.DIRECT_DNS, "local")
                            .collectAsStateWithLifecycle("local")
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.directDns = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.direct_dns)) },
                            textToValue = { it },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.dns),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(value)) },
                            valueToText = { it },
                        )
                    }
                    item(Key.DOMAIN_STRATEGY_FOR_DIRECT, PreferenceType.LIST) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.DOMAIN_STRATEGY_FOR_DIRECT, "auto")
                            .collectAsStateWithLifecycle("auto")
                        val values =
                            listOf("auto", "prefer_ipv6", "prefer_ipv4", "ipv4_only", "ipv6_only")
                        val entries = listOf(
                            stringResource(Res.string.auto),
                            stringResource(Res.string.prefer_ipv6),
                            stringResource(Res.string.prefer_ipv4),
                            stringResource(Res.string.ipv4_only),
                            stringResource(Res.string.ipv6_only),
                        )

                        ListPreference(
                            value = value,
                            onValueChange = {
                                DataStore.domainStrategyForDirect = it
                                needReload()
                            },
                            values = values,
                            title = { Text(stringResource(Res.string.domain_strategy_for_direct)) },
                            icon = { Spacer(Modifier.size(24.dp)) },
                            summary = {
                                val selectedIndex =
                                    values.indexOf(value).takeIf { index -> index >= 0 } ?: 0
                                Text(entries[selectedIndex])
                            },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = {
                                val selectedIndex = values.indexOf(it).takeIf { index ->
                                    index >= 0
                                } ?: 0
                                AnnotatedString(entries[selectedIndex])
                            },
                        )
                    }
                    item(Key.DOMAIN_STRATEGY_FOR_SERVER, PreferenceType.LIST) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.DOMAIN_STRATEGY_FOR_SERVER, "auto")
                            .collectAsStateWithLifecycle("auto")
                        val values =
                            listOf("auto", "prefer_ipv6", "prefer_ipv4", "ipv4_only", "ipv6_only")
                        val entries = listOf(
                            stringResource(Res.string.auto),
                            stringResource(Res.string.prefer_ipv6),
                            stringResource(Res.string.prefer_ipv4),
                            stringResource(Res.string.ipv4_only),
                            stringResource(Res.string.ipv6_only),
                        )

                        ListPreference(
                            value = value,
                            onValueChange = {
                                DataStore.domainStrategyForServer = it
                                needReload()
                            },
                            values = values,
                            title = { Text(stringResource(Res.string.domain_strategy_for_server)) },
                            icon = { Spacer(Modifier.size(24.dp)) },
                            summary = {
                                val selectedIndex =
                                    values.indexOf(value).takeIf { index -> index >= 0 } ?: 0
                                Text(entries[selectedIndex])
                            },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = {
                                val selectedIndex = values.indexOf(it).takeIf { index ->
                                    index >= 0
                                } ?: 0
                                AnnotatedString(entries[selectedIndex])
                            },
                        )
                    }
                    item(Key.ENABLE_FAKE_DNS, PreferenceType.SWITCH) {
                        SwitchPreference(
                            value = fakeDNSState,
                            onValueChange = {
                                DataStore.enableFakeDns = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.fake_dns)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.lock),
                                    null,
                                )
                            },
                            summary = { Text(stringResource(Res.string.fakedns_message)) },
                        )
                    }
                    item(Key.FAKE_DNS_FOR_ALL, PreferenceType.SWITCH) {
                        val value by DataStore.configurationStore
                            .booleanFlow(Key.FAKE_DNS_FOR_ALL, false)
                            .collectAsStateWithLifecycle(false)
                        SwitchPreference(
                            value = value,
                            onValueChange = {
                                DataStore.fakeDNSForAll = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.fake_dns_for_all)) },
                            enabled = fakeDNSState,
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.lock),
                                    null,
                                )
                            },
                            summary = { Text(stringResource(Res.string.fake_dns_for_all_sum)) },
                        )
                    }
                    item(Key.FAKE_DNS_RANGE_4, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.FAKE_DNS_RANGE_4, "198.51.100.0/24")
                            .collectAsStateWithLifecycle("198.51.100.0/24")
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.fakeDNSRange4 = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.fake_ip_range_4)) },
                            textToValue = { it },
                            enabled = fakeDNSState,
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.text_select_end),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(value)) },
                            valueToText = { it },
                        )
                    }
                    item(Key.FAKE_DNS_RANGE_6, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.FAKE_DNS_RANGE_6, "2001:2::/48")
                            .collectAsStateWithLifecycle("2001:2::/48")
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.fakeDNSRange6 = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.fake_ip_range_6)) },
                            textToValue = { it },
                            enabled = fakeDNSState,
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.text_select_end),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(value)) },
                            valueToText = { it },
                        )
                    }
                    item(Key.DNS_HOSTS, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.DNS_HOSTS, "")
                            .collectAsStateWithLifecycle("")
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.dnsHosts = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.dns_hosts)) },
                            textToValue = { it },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.transform),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(value)) },
                            valueToText = { it },
                        ) { value, onValueChange, onOk ->
                            HostTextField(value, onValueChange, onOk)
                        }
                    }

                    item(Key.INBOUND_SETTINGS, PreferenceType.CATEGORY) {
                        PreferenceCategory(text = { Text(stringResource(Res.string.inbound_settings)) })
                    }
                    item(Key.MIXED_PORT, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.MIXED_PORT, "2080")
                            .collectAsStateWithLifecycle("2080")
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.mixedPort = it.toIntOrNull() ?: 2080
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.port_proxy)) },
                            textToValue = { it },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.directions_boat),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(value)) },
                            valueToText = { it },
                            textField = { value, onValueChange, onOk ->
                                PortTextField(value, onValueChange, onOk)
                            },
                        )
                    }
                    item(Key.LOCAL_DNS_PORT, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.LOCAL_DNS_PORT, "0")
                            .collectAsStateWithLifecycle("0")
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.localDNSPort = it.toIntOrNull() ?: 0
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.port_local_dns)) },
                            textToValue = { it },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.apps),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(value)) },
                            valueToText = { it },
                            textField = { value, onValueChange, onOk ->
                                PortTextField(value, onValueChange, onOk)
                            },
                        )
                    }
                    item(Key.APPEND_HTTP_PROXY, PreferenceType.SWITCH) {
                        val value by DataStore.configurationStore
                            .booleanFlow(Key.APPEND_HTTP_PROXY, false)
                            .collectAsStateWithLifecycle(false)
                        SwitchPreference(
                            value = value,
                            onValueChange = {
                                DataStore.appendHttpProxy = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.append_http_proxy)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.app_registration),
                                    null,
                                )
                            },
                            summary = {
                                if (repo.isAndroid) {
                                    Text(stringResource(Res.string.append_http_proxy_sum))
                                }
                            },
                        )
                    }
                    httpProxyBypass(appendHttpProxyState, ::needReload)
                    item(Key.ALLOW_ACCESS, PreferenceType.SWITCH) {
                        val value by DataStore.configurationStore
                            .booleanFlow(Key.ALLOW_ACCESS, false)
                            .collectAsStateWithLifecycle(false)
                        SwitchPreference(
                            value = value,
                            onValueChange = {
                                DataStore.allowAccess = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.allow_access)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.nat),
                                    null,
                                )
                            },
                            summary = { Text(stringResource(Res.string.allow_access_sum)) },
                        )
                    }
                    item(Key.INBOUND_USERNAME, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.INBOUND_USERNAME, "")
                            .collectAsStateWithLifecycle("")
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.inboundUsername = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.inbound_username)) },
                            textToValue = { it },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.person),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(value)) },
                            valueToText = { it },
                        )
                    }
                    item(Key.INBOUND_PASSWORD, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.INBOUND_PASSWORD, "")
                            .collectAsStateWithLifecycle("")
                        PasswordPreference(
                            value = value,
                            onValueChange = {
                                DataStore.inboundPassword = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.inbound_password)) },
                        )
                    }
                    if (isExpert) item(
                        Key.ANCHOR_SSID,
                        PreferenceType.TEXT_FIELD,
                    ) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.ANCHOR_SSID, "")
                            .collectAsStateWithLifecycle("")
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.anchorSSID = it
                                needReload()
                            },
                            title = { Text("Anchor SSIDs") },
                            textToValue = { it },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.wifi),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(value)) },
                            valueToText = { it },
                        )
                    }

                    item(Key.MISC_SETTINGS, PreferenceType.CATEGORY) {
                        PreferenceCategory(text = { Text(stringResource(Res.string.cag_misc)) })
                    }
                    item(Key.CONNECTION_TEST_URL, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.CONNECTION_TEST_URL, CONNECTION_TEST_URL)
                            .collectAsStateWithLifecycle(CONNECTION_TEST_URL)
                        TextFieldPreference(
                            value = value,
                            onValueChange = { DataStore.connectionTestURL = it },
                            title = { Text(stringResource(Res.string.connection_test_url)) },
                            textToValue = { it },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.cast_connected),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(value)) },
                            valueToText = { it },
                        ) { value, onValueChange, onOk ->
                            LinkOrContentTextField(value, onValueChange, onOk)
                        }
                    }
                    item(Key.CONNECTION_TEST_CONCURRENT, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .intFlow(Key.CONNECTION_TEST_CONCURRENT, 5)
                            .collectAsStateWithLifecycle(5)
                        var previewValue by remember { mutableFloatStateOf(value.toFloat()) }
                        SliderPreference(
                            value = value.toFloat(),
                            onValueChange = { DataStore.connectionTestConcurrent = it.toInt() },
                            sliderValue = previewValue,
                            onSliderValueChange = { previewValue = it },
                            title = { Text(stringResource(Res.string.test_concurrency)) },
                            valueRange = 1f..32f,
                            valueSteps = 32,
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.fast_forward),
                                    null,
                                )
                            },
                            valueText = { Text(previewValue.toInt().toString()) },
                        )
                    }
                    item(Key.CONNECTION_TEST_TIMEOUT, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .intFlow(Key.CONNECTION_TEST_TIMEOUT, 3000)
                            .collectAsStateWithLifecycle(3000)
                        var previewValue by remember { mutableFloatStateOf(value.toFloat()) }
                        SliderPreference(
                            value = value.toFloat(),
                            onValueChange = { DataStore.connectionTestTimeout = it.toInt() },
                            sliderValue = previewValue,
                            onSliderValueChange = { previewValue = it },
                            title = { Text(stringResource(Res.string.test_timeout)) },
                            valueRange = 1024f..8192f,
                            valueSteps = 20,
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.apps),
                                    null,
                                )
                            },
                            valueText = { Text(previewValue.toInt().toString()) },
                        )
                    }
                    platformMiscOptions { needReload() }
                    item(Key.CERT_PROVIDER, PreferenceType.LIST) {
                        fun certProviderTextRes(index: Int): StringResource = when (index) {
                            CertProvider.SYSTEM -> Res.string.follow_system
                            CertProvider.MOZILLA -> Res.string.mozilla
                            CertProvider.SYSTEM_AND_USER -> Res.string.system_and_user
                            CertProvider.CHROME -> Res.string.cert_chrome
                            else -> Res.string.mozilla
                        }

                        val value by DataStore.configurationStore
                            .intFlow(Key.CERT_PROVIDER, CertProvider.MOZILLA)
                            .collectAsStateWithLifecycle(CertProvider.MOZILLA)

                        ListPreference(
                            value = value,
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
                                Icon(
                                    vectorResource(Res.drawable.push_pin),
                                    null,
                                )
                            },
                            summary = { Text(stringResource(certProviderTextRes(value))) },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = {
                                val text = runBlocking { repo.getString(certProviderTextRes(it)) }
                                AnnotatedString(text)
                            },
                        )
                    }
                    disableProcessText()
                    item(Key.NTP_SETTINGS, PreferenceType.CATEGORY) {
                        PreferenceCategory(text = { Text(stringResource(Res.string.ntp_category)) })
                    }
                    item(Key.ENABLE_NTP, PreferenceType.SWITCH) {
                        val value by DataStore.configurationStore
                            .booleanFlow(Key.ENABLE_NTP, false)
                            .collectAsStateWithLifecycle(false)
                        SwitchPreference(
                            value = value,
                            onValueChange = {
                                DataStore.ntpEnable = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.enable_ntp)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.timelapse),
                                    null,
                                )
                            },
                            summary = { Text(stringResource(Res.string.ntp_sum)) },
                        )
                    }
                    item(Key.NTP_SERVER, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.NTP_SERVER, "time.apple.com")
                            .collectAsStateWithLifecycle("time.apple.com")
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.ntpAddress = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.ntp_server_address)) },
                            textToValue = { it },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.router),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(value)) },
                            valueToText = { it },
                            enabled = ntpEnableState,
                        )
                    }
                    item(Key.NTP_PORT, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .intFlow(Key.NTP_PORT, 123)
                            .collectAsStateWithLifecycle(123)
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.ntpPort = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.ntp_server_port)) },
                            textToValue = { it.toIntOrNull() ?: 123 },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.directions_boat),
                                    null,
                                )
                            },
                            summary = { Text(value.toString()) },
                            valueToText = { it.toString() },
                            enabled = ntpEnableState,
                            textField = { value, onValueChange, onOk ->
                                PortTextField(value, onValueChange, onOk)
                            },
                        )
                    }
                    item(Key.NTP_INTERVAL, PreferenceType.TEXT_FIELD) {
                        val value by DataStore.configurationStore
                            .stringFlow(Key.NTP_INTERVAL, "30m")
                            .collectAsStateWithLifecycle("30m")
                        TextFieldPreference(
                            value = value,
                            onValueChange = {
                                DataStore.ntpInterval = it
                                needReload()
                            },
                            title = { Text(stringResource(Res.string.ntp_sync_interval)) },
                            textToValue = { it },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.flip_camera_android),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(value)) },
                            valueToText = { it },
                            enabled = ntpEnableState,
                        ) { value, onValueChange, onOk ->
                            DurationTextField(value, onValueChange, onOk)
                        }
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
                        actionLabel = repo.getString(Res.string.ok),
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

                else -> {}
            }
        }
    }
}

private inline fun LazyListScope.colorPickerPreference(
    modifier: Modifier = Modifier.fillMaxWidth(),
    key: String,
    crossinline title: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    item(key, PreferenceType.COLOR_PICKER) {
        val currentTheme by DataStore.configurationStore
            .intFlow(key, DEFAULT)
            .collectAsStateWithLifecycle(DEFAULT)
        var showDialog by remember { mutableStateOf(false) }
        val extraColors = rememberThemeExtraColors()
        Preference(
            title = { title() },
            modifier = modifier,
            enabled = enabled,
            icon = {
                Icon(
                    vectorResource(Res.drawable.color_lens),
                    null,
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
                        Text(
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
}

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
