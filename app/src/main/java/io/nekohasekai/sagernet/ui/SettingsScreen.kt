package io.nekohasekai.sagernet.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jakewharton.processphoenix.ProcessPhoenix
import io.nekohasekai.sagernet.CONNECTION_TEST_URL
import io.nekohasekai.sagernet.CertProvider
import io.nekohasekai.sagernet.DEFAULT_HTTP_BYPASS
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.NetworkInterfaceStrategy
import io.nekohasekai.sagernet.ProtocolProvider
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.RuleProvider
import io.nekohasekai.sagernet.TunImplementation
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.Executable
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.compose.DurationTextField
import io.nekohasekai.sagernet.compose.HostTextField
import io.nekohasekai.sagernet.compose.LinkOrContentTextField
import io.nekohasekai.sagernet.compose.PasswordPreference
import io.nekohasekai.sagernet.compose.PortTextField
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.compose.SagerFab
import io.nekohasekai.sagernet.compose.SimpleTopAppBar
import io.nekohasekai.sagernet.compose.StatsBar
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.UIntegerTextField
import io.nekohasekai.sagernet.compose.paddingWithNavigation
import io.nekohasekai.sagernet.compose.rememberScrollHideState
import io.nekohasekai.sagernet.compose.showAndDismissOld
import io.nekohasekai.sagernet.compose.theme.DEFAULT
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.contentOrUnset
import io.nekohasekai.sagernet.ktx.findActivity
import io.nekohasekai.sagernet.ktx.intListN
import io.nekohasekai.sagernet.ktx.isExpert
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.logLevelString
import io.nekohasekai.sagernet.repository.repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.MultiSelectListPreference
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

private const val TYPE_PREFERENCE_CATEGORY = 0
private const val TYPE_SWITCH_PREFERENCE = 1
private const val TYPE_LIST_PREFERENCE = 2
private const val TYPE_TEXT_FIELD_PREFERENCE = 3
private const val TYPE_MULTI_SELECT_LIST_PREFERENCE = 4
private const val TYPE_COLOR_PICKER_PREFERENCE = 5

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    onDrawerClick: () -> Unit,
    connection: SagerConnection,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val windowInsets = WindowInsets.safeDrawing
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollHideVisible by rememberScrollHideState(listState)

    LaunchedEffect(Unit) {
        onIoDispatcher {
            DataStore.initGlobal()
        }
    }

    fun needReload() = scope.launch {
        if (!DataStore.serviceState.started) return@launch
        val result = snackbarState.showAndDismissOld(
            message = context.getString(R.string.need_reload),
            actionLabel = context.getString(R.string.apply),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.Dismissed) return@launch
        repo.reloadService()
    }

    fun needRestart() = scope.launch {
        val result = snackbarState.showAndDismissOld(
            message = context.getString(R.string.need_restart),
            actionLabel = context.getString(R.string.apply),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.Dismissed) return@launch
        repo.stopService()
        runOnDefaultDispatcher {
            delay(500)
            SagerDatabase.instance.close()
            Executable.killAll(true)
            ProcessPhoenix.triggerRebirth(
                context,
                Intent(context, MainActivity::class.java),
            )
        }
    }

    fun tryReload() {
        if (!DataStore.serviceState.started) return
        repo.reloadService()
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
    val bypassLanState by DataStore.configurationStore
        .booleanFlow(Key.BYPASS_LAN, true)
        .collectAsStateWithLifecycle(true)
    val rulesProviderState by DataStore.configurationStore
        .intFlow(Key.RULES_PROVIDER, RuleProvider.OFFICIAL)
        .collectAsStateWithLifecycle(RuleProvider.OFFICIAL)
    val fakeDNSState by DataStore.configurationStore
        .booleanFlow(Key.ENABLE_FAKE_DNS, false)
        .collectAsStateWithLifecycle(false)
    val ntpEnableState by DataStore.configurationStore
        .booleanFlow(Key.ENABLE_NTP, false)
        .collectAsStateWithLifecycle(false)

    val serviceStatus by connection.status.collectAsStateWithLifecycle()
    val service by connection.service.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = ImageVector.vectorResource(R.drawable.menu),
                navigationDescription = stringResource(R.string.menu),
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
                onNavigationClick = onDrawerClick,
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
                            message = context.getStringOrRes(message),
                            actionLabel = context.getString(android.R.string.ok),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (serviceStatus.state == BaseService.State.Connected) {
                StatsBar(
                    visible = scrollHideVisible,
                    mainViewModel = mainViewModel,
                    service = service,
                )
            }
        },
    ) { innerPadding ->
        ProvidePreferenceLocals {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding.paddingWithNavigation(),
            ) {
                item(Key.GENERAL_SETTINGS, TYPE_PREFERENCE_CATEGORY) {
                    PreferenceCategory(text = { Text(stringResource(R.string.general_settings)) })
                }
                item(Key.PERSIST_ACROSS_REBOOT, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.PERSIST_ACROSS_REBOOT, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = { DataStore.persistAcrossReboot = it },
                        title = { Text(stringResource(R.string.auto_connect)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.phonelink_ring),
                                null,
                            )
                        },
                        summary = { Text(stringResource(R.string.auto_connect_summary)) },
                    )
                }
                colorPickerPreference(
                    key = Key.APP_THEME,
                    title = { Text(stringResource(R.string.theme)) },
                    postChange = {
                        tryReload()
                        ActivityCompat.recreate(context.findActivity<Activity>()!!)
                    },
                )
                item(Key.NIGHT_THEME, TYPE_LIST_PREFERENCE) {
                    fun nightString(index: Int): Int = when (index) {
                        0 -> R.string.follow_system
                        1 -> R.string.enable
                        2 -> R.string.disable
                        3 -> R.string.auto
                        else -> R.string.follow_system
                    }

                    val context = LocalContext.current
                    val value by DataStore.configurationStore
                        .intFlow(Key.NIGHT_THEME, 0)
                        .collectAsStateWithLifecycle(0)
                    ListPreference(
                        value = value,
                        onValueChange = {
                            DataStore.nightTheme = it
                            AppCompatDelegate.setDefaultNightMode(
                                when (it) {
                                    0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                                    2 -> AppCompatDelegate.MODE_NIGHT_NO
                                    else -> AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                                },
                            )
                        },
                        values = intListN(4),
                        title = { Text(stringResource(R.string.night_mode)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.wb_sunny),
                                null,
                            )
                        },
                        summary = { Text(stringResource(nightString(value))) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(context.getString(nightString(it))) },
                    )
                }
                item(Key.APP_LANGUAGE, TYPE_LIST_PREFERENCE) {
                    fun getLanguageDisplayName(code: String): Int = when (code) {
                        "" -> R.string.language_system_default
                        "ar" -> R.string.language_ar_display_name
                        "en-US" -> R.string.language_en_display_name
                        "es" -> R.string.language_es_display_name
                        "fa" -> R.string.language_fa_display_name
                        "ru" -> R.string.language_ru_display_name
                        "zh-Hans-CN" -> R.string.language_zh_Hans_CN_display_name
                        "zh-Hant-TW" -> R.string.language_zh_Hant_TW_display_name
                        "zh-Hant-HK" -> R.string.language_zh_Hant_HK_display_name
                        else -> R.string.language_system_default
                    }

                    val context = LocalContext.current
                    val values = listOf(
                        "",
                        "ar",
                        "en-US",
                        "es",
                        "fa",
                        "ru",
                        "zh-Hans-CN",
                        "zh-Hant-TW",
                        "zh-Hant-HK",
                    )
                    val currentLocale = remember {
                        val locale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                        if (locale in values) locale else ""
                    }
                    var selectedValue by remember { mutableStateOf(currentLocale) }

                    ListPreference(
                        value = selectedValue,
                        onValueChange = { newValue ->
                            selectedValue = newValue
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(newValue),
                            )
                            needRestart()
                            ActivityCompat.recreate(context.findActivity<Activity>()!!)
                        },
                        values = values,
                        title = { Text(stringResource(R.string.language)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.translate),
                                null,
                            )
                        },
                        summary = { Text(stringResource(getLanguageDisplayName(selectedValue))) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(context.getString(getLanguageDisplayName(it))) },
                    )
                }
                item(Key.SERVICE_MODE, TYPE_LIST_PREFERENCE) {
                    val context = LocalContext.current
                    fun serviceModeText(mode: String): Int = when (mode) {
                        Key.MODE_VPN -> R.string.service_mode_vpn
                        Key.MODE_PROXY -> R.string.service_mode_proxy
                        else -> R.string.service_mode_vpn
                    }

                    val values = listOf(Key.MODE_VPN, Key.MODE_PROXY)
                    val stored by DataStore.configurationStore
                        .stringFlow(Key.SERVICE_MODE, Key.MODE_VPN)
                        .collectAsStateWithLifecycle(Key.MODE_VPN)

                    ListPreference(
                        value = stored,
                        onValueChange = {
                            DataStore.serviceMode = it
                            repo.stopService()
                        },
                        values = values,
                        title = { Text(stringResource(R.string.service_mode)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.developer_mode),
                                null,
                            )
                        },
                        summary = { Text(stringResource(serviceModeText(stored))) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(context.getString(serviceModeText(it))) },
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) item(
                    Key.MEMORY_LIMIT,
                    TYPE_SWITCH_PREFERENCE,
                ) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.MEMORY_LIMIT, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.memoryLimit = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.memory_limit)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.local_bar),
                                null,
                            )
                        },
                    )
                }
                item(Key.TUN_IMPLEMENTATION, TYPE_LIST_PREFERENCE) {
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
                        title = { Text(stringResource(R.string.tun_implementation)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.flip_camera_android),
                                null,
                            )
                        },
                        summary = { Text(tunImplText(value)) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(tunImplText(it)) },
                    )
                }
                item(Key.MTU, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .intFlow(Key.MTU, 9000)
                        .collectAsStateWithLifecycle(9000)
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.mtu = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.mtu)) },
                        textToValue = { it.toIntOrNull() ?: 9000 },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.public_icon),
                                null,
                            )
                        },
                        summary = { Text(value.toString()) },
                        valueToText = { it.toString() },
                    )
                }
                item(Key.ALLOW_APPS_BYPASS_VPN, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.ALLOW_APPS_BYPASS_VPN, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.allowAppsBypassVpn = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.allow_apps_bypass_vpn)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.transform),
                                null,
                            )
                        },
                    )
                }
                item(Key.SPEED_INTERVAL, TYPE_LIST_PREFERENCE) {
                    val values = listOf(0, 500, 1000, 3000, 10000)
                    val value by DataStore.configurationStore
                        .intFlow(Key.SPEED_INTERVAL, 1000)
                        .collectAsStateWithLifecycle(1000)
                    val context = LocalContext.current
                    fun speedIntervalText(ms: Int): String = when (ms) {
                        0 -> context.getString(R.string.disable)
                        500 -> "500ms"
                        1000 -> "1s"
                        3000 -> "3s"
                        10000 -> "10s"
                        else -> "1s"
                    }

                    ListPreference(
                        value = value,
                        onValueChange = {
                            DataStore.speedInterval = it
                            needReload()
                        },
                        values = values,
                        title = { Text(stringResource(R.string.speed_interval)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.shutter_speed),
                                null,
                            )
                        },
                        summary = { Text(speedIntervalText(value)) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(speedIntervalText(it)) },
                    )
                }
                item(Key.PROFILE_TRAFFIC_STATISTICS, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.PROFILE_TRAFFIC_STATISTICS, true)
                        .collectAsStateWithLifecycle(true)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.profileTrafficStatistics = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.profile_traffic_statistics)) },
                        icon = { Icon(ImageVector.vectorResource(R.drawable.traffic), null) },
                        summary = { Text(stringResource(R.string.profile_traffic_statistics_summary)) },
                        enabled = speedIntervalState != 0,
                    )
                }
                item(Key.SHOW_DIRECT_SPEED, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.SHOW_DIRECT_SPEED, true)
                        .collectAsStateWithLifecycle(true)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.showDirectSpeed = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.show_direct_speed)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.speed),
                                null,
                            )
                        },
                        summary = { Text(stringResource(R.string.show_direct_speed_sum)) },
                        enabled = speedIntervalState != 0,
                    )
                }
                item(Key.SHOW_GROUP_IN_NOTIFICATION, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.SHOW_GROUP_IN_NOTIFICATION, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.showGroupInNotification = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.show_group_in_notification)) },
                        icon = { Icon(ImageVector.vectorResource(R.drawable.label), null) },
                    )
                }
                item(Key.ALWAYS_SHOW_ADDRESS, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.ALWAYS_SHOW_ADDRESS, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = { DataStore.alwaysShowAddress = it },
                        title = { Text(stringResource(R.string.always_show_address)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.center_focus_weak),
                                null,
                            )
                        },
                        summary = { Text(stringResource(R.string.always_show_address_sum)) },
                    )
                }
                item(Key.BLURRED_ADDRESS, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.BLURRED_ADDRESS, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = { DataStore.blurredAddress = it },
                        title = { Text(stringResource(R.string.blurred_address)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.transgender),
                                null,
                            )
                        },
                        enabled = alwaysShowAddressState,
                    )
                }
                item(Key.SECURITY_ADVISORY, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.SECURITY_ADVISORY, true)
                        .collectAsStateWithLifecycle(true)
                    SwitchPreference(
                        value = value,
                        onValueChange = { DataStore.securityAdvisory = it },
                        title = { Text(stringResource(R.string.insecure_warn)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.security),
                                null,
                            )
                        },
                    )
                }
                if (Build.VERSION.SDK_INT >= 28) item(Key.METERED_NETWORK, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.METERED_NETWORK, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.meteredNetwork = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.metered)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.data_usage),
                                null,
                            )
                        },
                        summary = { Text(stringResource(R.string.metered_summary)) },
                    )
                }
                item(Key.LOG_LEVEL, TYPE_LIST_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .intFlow(Key.LOG_LEVEL, 2)
                        .collectAsStateWithLifecycle(2)
                    ListPreference(
                        value = value,
                        onValueChange = {
                            DataStore.logLevel = it
                            needRestart()
                        },
                        values = intListN(7),
                        title = { Text(stringResource(R.string.log_level)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.bug_report),
                                null,
                            )
                        },
                        summary = { Text(logLevelString(value)) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(logLevelString(it)) },
                    )
                }
                item(Key.LOG_MAX_SIZE, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .intFlow(Key.LOG_MAX_SIZE, 50)
                        .collectAsStateWithLifecycle(50)
                    TextFieldPreference(
                        value = value,
                        onValueChange = { DataStore.logMaxSize = it },
                        title = { Text(stringResource(R.string.max_log_size)) },
                        textToValue = { it.toIntOrNull() ?: 50 },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.description),
                                null,
                            )
                        },
                        summary = { Text(value.toString()) },
                        valueToText = { it.toString() },
                        textField = { value, onValueChange, onOk ->
                            UIntegerTextField(value, onValueChange, onOk)
                        },
                    )
                }

                item(Key.ROUTE_SETTINGS, TYPE_PREFERENCE_CATEGORY) {
                    PreferenceCategory(text = { Text(stringResource(R.string.route_options)) })
                }
                item(Key.PROXY_APPS, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.PROXY_APPS, false)
                        .collectAsStateWithLifecycle(false)
                    val context = LocalContext.current
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            context.startActivity(Intent(context, AppManagerActivity::class.java))
                        },
                        title = { Text(stringResource(R.string.proxied_apps)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.apps),
                                null,
                            )
                        },
                        summary = { Text(stringResource(R.string.proxied_apps_summary)) },
                    )
                }
                item(Key.UPDATE_PROXY_APPS_WHEN_INSTALL, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.UPDATE_PROXY_APPS_WHEN_INSTALL, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = { DataStore.updateProxyAppsWhenInstall = it },
                        title = { Text(stringResource(R.string.update_proxy_apps_when_install)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.keyboard_tab),
                                null,
                            )
                        },
                    )
                }
                item(Key.BYPASS_LAN, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.BYPASS_LAN, true)
                        .collectAsStateWithLifecycle(true)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.bypassLan = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.route_opt_bypass_lan)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.legend_toggle),
                                null,
                            )
                        },
                    )
                }
                item(Key.BYPASS_LAN_IN_CORE, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.BYPASS_LAN_IN_CORE, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.bypassLanInCore = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.bypass_lan_in_core)) },
                        enabled = bypassLanState,
                        icon = { Icon(ImageVector.vectorResource(R.drawable.arrow_outward), null) },
                    )
                }
                item(Key.NETWORK_STRATEGY, TYPE_LIST_PREFERENCE) {
                    val values = listOf("", "prefer_ipv6", "prefer_ipv4", "ipv4_only", "ipv6_only")
                    fun networkStrategyTextRes(value: String): Int = when (value) {
                        "" -> R.string.auto
                        "prefer_ipv6" -> R.string.prefer_ipv6
                        "prefer_ipv4" -> R.string.prefer_ipv4
                        "ipv4_only" -> R.string.ipv4_only
                        "ipv6_only" -> R.string.ipv6_only
                        else -> R.string.auto
                    }

                    val context = LocalContext.current
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
                        title = { Text(stringResource(R.string.network_strategy)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.router),
                                null,
                            )
                        },
                        summary = { Text(stringResource(networkStrategyTextRes(stored))) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(context.getString(networkStrategyTextRes(it))) },
                    )
                }
                item(Key.NETWORK_INTERFACE_STRATEGY, TYPE_LIST_PREFERENCE) {
                    fun networkInterfaceStrategyTextRes(selection: Int): Int = when (selection) {
                        NetworkInterfaceStrategy.DEFAULT -> R.string.keep_default
                        NetworkInterfaceStrategy.HYBRID -> R.string.hybrid
                        NetworkInterfaceStrategy.FALLBACK -> R.string.fallback
                        else -> R.string.keep_default
                    }

                    val context = LocalContext.current
                    val value by DataStore.configurationStore
                        .intFlow(Key.NETWORK_INTERFACE_STRATEGY, NetworkInterfaceStrategy.DEFAULT)
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
                        title = { Text(stringResource(R.string.network_interface_strategy)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.construction),
                                null,
                            )
                        },
                        summary = { Text(stringResource(networkInterfaceStrategyTextRes(value))) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = {
                            AnnotatedString(
                                context.getString(
                                    networkInterfaceStrategyTextRes(it),
                                ),
                            )
                        },
                    )
                }
                item(Key.NETWORK_PREFERRED_INTERFACES, TYPE_MULTI_SELECT_LIST_PREFERENCE) {
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
                        title = { Text(stringResource(R.string.network_interface_preference)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.public_icon),
                                null,
                            )
                        },
                        summary = {
                            val text = if (selected.isEmpty()) {
                                stringResource(R.string.not_set)
                            } else selected.joinToString("\n")
                            Text(text)
                        },
                        valueToText = { AnnotatedString(it) },
                    )
                }
                item(Key.FORCED_SEARCH_PROCESS, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.FORCED_SEARCH_PROCESS, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.forcedSearchProcess = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.forced_search_process)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.android),
                                null,
                            )
                        },
                    )
                }
                item(Key.RULES_PROVIDER, TYPE_LIST_PREFERENCE) {
                    val context = LocalContext.current
                    fun rulesProviderText(index: Int): String = when (index) {
                        RuleProvider.OFFICIAL -> context.getString(R.string.route_rules_official)
                        RuleProvider.LOYALSOLDIER -> "Loyalsoldier (xchacha20-poly1305/sing-geo*)"
                        RuleProvider.CHOCOLATE4U -> "Chocolate4U/Iran-sing-box-rules"
                        RuleProvider.CUSTOM -> context.getString(R.string.custom_rule_provider)
                        else -> context.getString(R.string.route_rules_official)
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
                        title = { Text(stringResource(R.string.route_rules_provider)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.rule_folder),
                                null,
                            )
                        },
                        summary = { Text(rulesProviderText(value)) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(rulesProviderText(it)) },
                    )
                }
                if (rulesProviderState == RuleProvider.CUSTOM) item(
                    Key.CUSTOM_RULE_PROVIDER,
                    TYPE_TEXT_FIELD_PREFERENCE,
                ) {
                    val defaultUrl =
                        "https://codeload.github.com/SagerNet/sing-geosite/tar.gz/refs/heads/rule-set"
                    val value by DataStore.configurationStore
                        .stringFlow(Key.CUSTOM_RULE_PROVIDER, defaultUrl)
                        .collectAsStateWithLifecycle(defaultUrl)

                    TextFieldPreference(
                        value = value,
                        onValueChange = { DataStore.customRuleProvider = it },
                        title = { Text(stringResource(R.string.custom_rule_provider)) },
                        textToValue = { it },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.import_contacts),
                                null,
                            )
                        },
                        summary = { Text(LocalContext.current.contentOrUnset(value)) },
                        valueToText = { it },
                    ) { value, onValueChange, onOk ->
                        LinkOrContentTextField(value, onValueChange, onOk)
                    }
                }

                item(Key.PROTOCOL_SETTINGS, TYPE_PREFERENCE_CATEGORY) {
                    PreferenceCategory(text = { Text(stringResource(R.string.protocol_settings)) })
                }
                item(Key.UPLOAD_SPEED, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .intFlow(Key.UPLOAD_SPEED, 0)
                        .collectAsStateWithLifecycle(0)
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.uploadSpeed = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.hysteria_upload_mbps)) },
                        textToValue = { it.toIntOrNull() ?: 0 },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.file_upload),
                                null,
                            )
                        },
                        summary = { Text(value.toString()) },
                        valueToText = { it.toString() },
                    ) { value, onValueChange, onOk ->
                        UIntegerTextField(value, onValueChange, onOk)
                    }
                }
                item(Key.DOWNLOAD_SPEED, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .intFlow(Key.DOWNLOAD_SPEED, 0)
                        .collectAsStateWithLifecycle(0)
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.downloadSpeed = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.hysteria_download_mbps)) },
                        textToValue = { it.toIntOrNull() ?: 0 },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.download),
                                null,
                            )
                        },
                        summary = { Text(value.toString()) },
                        valueToText = { it.toString() },
                    ) { value, onValueChange, onOk ->
                        UIntegerTextField(value, onValueChange, onOk)
                    }
                }
                item(Key.PROVIDER_HYSTERIA2, TYPE_LIST_PREFERENCE) {
                    val context = LocalContext.current
                    fun pluginProviderText(index: Int): String = when (index) {
                        ProtocolProvider.CORE -> "sing-box"
                        ProtocolProvider.PLUGIN -> context.getString(R.string.plugin)
                        else -> "sing-box"
                    }

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
                        title = { Text(stringResource(R.string.hysteria2_provider)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.flight_takeoff),
                                null,
                            )
                        },
                        summary = { Text(pluginProviderText(value)) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(pluginProviderText(it)) },
                    )
                }
                item(Key.PROVIDER_JUICITY, TYPE_LIST_PREFERENCE) {
                    val context = LocalContext.current
                    fun pluginProviderText(index: Int): String = when (index) {
                        ProtocolProvider.CORE -> "sing-box"
                        ProtocolProvider.PLUGIN -> context.getString(R.string.plugin)
                        else -> "sing-box"
                    }

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
                        title = { Text(stringResource(R.string.juicity_provider)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.flight_takeoff),
                                null,
                            )
                        },
                        summary = { Text(pluginProviderText(value)) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(pluginProviderText(it)) },
                    )
                }
                item(Key.CUSTOM_PLUGIN_PREFIX, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.CUSTOM_PLUGIN_PREFIX, "")
                        .collectAsStateWithLifecycle("")
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.customPluginPrefix = it
                            needRestart()
                        },
                        title = { Text(stringResource(R.string.custom_plugin_prefix)) },
                        textToValue = { it },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.copyright),
                                null,
                            )
                        },
                        summary = { Text(stringResource(R.string.custom_plugin_prefix_summary)) },
                        valueToText = { it },
                    )
                }

                item(Key.DNS_SETTINGS, TYPE_PREFERENCE_CATEGORY) {
                    PreferenceCategory(text = { Text(stringResource(R.string.cag_dns)) })
                }
                item(Key.REMOTE_DNS, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.REMOTE_DNS, "tcp://dns.google")
                        .collectAsStateWithLifecycle("tcp://dns.google")
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.remoteDns = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.remote_dns)) },
                        textToValue = { it },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.dns),
                                null,
                            )
                        },
                        summary = { Text(LocalContext.current.contentOrUnset(value)) },
                        valueToText = { it },
                    )
                }
                item(Key.DIRECT_DNS, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.DIRECT_DNS, "local")
                        .collectAsStateWithLifecycle("local")
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.directDns = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.direct_dns)) },
                        textToValue = { it },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.dns),
                                null,
                            )
                        },
                        summary = { Text(LocalContext.current.contentOrUnset(value)) },
                        valueToText = { it },
                    )
                }
                item(Key.DOMAIN_STRATEGY_FOR_DIRECT, TYPE_LIST_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.DOMAIN_STRATEGY_FOR_DIRECT, "auto")
                        .collectAsStateWithLifecycle("auto")
                    val values =
                        listOf("auto", "prefer_ipv6", "prefer_ipv4", "ipv4_only", "ipv6_only")
                    val entries = listOf(
                        stringResource(R.string.auto),
                        stringResource(R.string.prefer_ipv6),
                        stringResource(R.string.prefer_ipv4),
                        stringResource(R.string.ipv4_only),
                        stringResource(R.string.ipv6_only),
                    )

                    ListPreference(
                        value = value,
                        onValueChange = {
                            DataStore.domainStrategyForDirect = it
                            needReload()
                        },
                        values = values,
                        title = { Text(stringResource(R.string.domain_strategy_for_direct)) },
                        icon = { Spacer(Modifier.size(24.dp)) },
                        summary = {
                            val selectedIndex =
                                values.indexOf(value).takeIf { index -> index >= 0 } ?: 0
                            Text(entries[selectedIndex])
                        },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = {
                            val selectedIndex =
                                values.indexOf(it).takeIf { index -> index >= 0 } ?: 0
                            AnnotatedString(entries[selectedIndex])
                        },
                    )
                }
                item(Key.DOMAIN_STRATEGY_FOR_SERVER, TYPE_LIST_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.DOMAIN_STRATEGY_FOR_SERVER, "auto")
                        .collectAsStateWithLifecycle("auto")
                    val values =
                        listOf("auto", "prefer_ipv6", "prefer_ipv4", "ipv4_only", "ipv6_only")
                    val entries = listOf(
                        stringResource(R.string.auto),
                        stringResource(R.string.prefer_ipv6),
                        stringResource(R.string.prefer_ipv4),
                        stringResource(R.string.ipv4_only),
                        stringResource(R.string.ipv6_only),
                    )

                    ListPreference(
                        value = value,
                        onValueChange = {
                            DataStore.domainStrategyForServer = it
                            needReload()
                        },
                        values = values,
                        title = { Text(stringResource(R.string.domain_strategy_for_server)) },
                        icon = { Spacer(Modifier.size(24.dp)) },
                        summary = {
                            val selectedIndex =
                                values.indexOf(value).takeIf { index -> index >= 0 } ?: 0
                            Text(entries[selectedIndex])
                        },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = {
                            val selectedIndex =
                                values.indexOf(it).takeIf { index -> index >= 0 } ?: 0
                            AnnotatedString(entries[selectedIndex])
                        },
                    )
                }
                item(Key.ENABLE_FAKE_DNS, TYPE_SWITCH_PREFERENCE) {
                    SwitchPreference(
                        value = fakeDNSState,
                        onValueChange = {
                            DataStore.enableFakeDns = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.fake_dns)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.lock),
                                null,
                            )
                        },
                        summary = { Text(stringResource(R.string.fakedns_message)) },
                    )
                }
                item(Key.FAKE_DNS_FOR_ALL, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.FAKE_DNS_FOR_ALL, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.fakeDNSForAll = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.fake_dns_for_all)) },
                        enabled = fakeDNSState,
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.lock),
                                null,
                            )
                        },
                        summary = { Text(stringResource(R.string.fake_dns_for_all_sum)) },
                    )
                }
                item(Key.DNS_HOSTS, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.DNS_HOSTS, "")
                        .collectAsStateWithLifecycle("")
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.dnsHosts = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.dns_hosts)) },
                        textToValue = { it },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.transform),
                                null,
                            )
                        },
                        summary = { Text(LocalContext.current.contentOrUnset(value)) },
                        valueToText = { it },
                    ) { value, onValueChange, onOk ->
                        HostTextField(value, onValueChange, onOk)
                    }
                }

                item(Key.INBOUND_SETTINGS, TYPE_PREFERENCE_CATEGORY) {
                    PreferenceCategory(text = { Text(stringResource(R.string.inbound_settings)) })
                }
                item(Key.MIXED_PORT, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.MIXED_PORT, "2080")
                        .collectAsStateWithLifecycle("2080")
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.mixedPort = it.toIntOrNull() ?: 2080
                            needReload()
                        },
                        title = { Text(stringResource(R.string.port_proxy)) },
                        textToValue = { it },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.directions_boat),
                                null,
                            )
                        },
                        summary = { Text(LocalContext.current.contentOrUnset(value)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            PortTextField(value, onValueChange, onOk)
                        },
                    )
                }
                item(Key.LOCAL_DNS_PORT, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.LOCAL_DNS_PORT, "0")
                        .collectAsStateWithLifecycle("0")
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.localDNSPort = it.toIntOrNull() ?: 0
                            needReload()
                        },
                        title = { Text(stringResource(R.string.port_local_dns)) },
                        textToValue = { it },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.apps),
                                null,
                            )
                        },
                        summary = { Text(LocalContext.current.contentOrUnset(value)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            PortTextField(value, onValueChange, onOk)
                        },
                    )
                }
                item(Key.APPEND_HTTP_PROXY, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.APPEND_HTTP_PROXY, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.appendHttpProxy = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.append_http_proxy)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.app_registration),
                                null,
                            )
                        },
                        summary = { Text(stringResource(R.string.append_http_proxy_sum)) },
                    )
                }
                item(Key.HTTP_PROXY_BYPASS, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.HTTP_PROXY_BYPASS, DEFAULT_HTTP_BYPASS)
                        .collectAsStateWithLifecycle(DEFAULT_HTTP_BYPASS)
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.httpProxyBypass = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.http_proxy_bypass)) },
                        textToValue = { it },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.domain),
                                null,
                            )
                        },
                        valueToText = { it },
                        enabled = appendHttpProxyState,
                    ) { value, onValueChange, onOk ->
                        HostTextField(value, onValueChange, onOk)
                    }
                }
                item(Key.ALLOW_ACCESS, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.ALLOW_ACCESS, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.allowAccess = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.allow_access)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.nat),
                                null,
                            )
                        },
                        summary = { Text(stringResource(R.string.allow_access_sum)) },
                    )
                }
                item(Key.INBOUND_USERNAME, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.INBOUND_USERNAME, "")
                        .collectAsStateWithLifecycle("")
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.inboundUsername = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.inbound_username)) },
                        textToValue = { it },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.person),
                                null,
                            )
                        },
                        summary = { Text(LocalContext.current.contentOrUnset(value)) },
                        valueToText = { it },
                    )
                }
                item(Key.INBOUND_PASSWORD, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.INBOUND_PASSWORD, "")
                        .collectAsStateWithLifecycle("")
                    PasswordPreference(
                        value = value,
                        onValueChange = {
                            DataStore.inboundPassword = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.inbound_password)) },
                    )
                }
                if (isExpert) item(
                    Key.ANCHOR_SSID,
                    TYPE_TEXT_FIELD_PREFERENCE,
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
                                ImageVector.vectorResource(R.drawable.wifi),
                                null,
                            )
                        },
                        summary = { Text(LocalContext.current.contentOrUnset(value)) },
                        valueToText = { it },
                    )
                }

                item(Key.MISC_SETTINGS, TYPE_PREFERENCE_CATEGORY) {
                    PreferenceCategory(text = { Text(stringResource(R.string.cag_misc)) })
                }
                item(Key.CONNECTION_TEST_URL, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.CONNECTION_TEST_URL, CONNECTION_TEST_URL)
                        .collectAsStateWithLifecycle(CONNECTION_TEST_URL)
                    TextFieldPreference(
                        value = value,
                        onValueChange = { DataStore.connectionTestURL = it },
                        title = { Text(stringResource(R.string.connection_test_url)) },
                        textToValue = { it },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.cast_connected),
                                null,
                            )
                        },
                        summary = { Text(LocalContext.current.contentOrUnset(value)) },
                        valueToText = { it },
                    ) { value, onValueChange, onOk ->
                        LinkOrContentTextField(value, onValueChange, onOk)
                    }
                }
                item(Key.CONNECTION_TEST_CONCURRENT, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .intFlow(Key.CONNECTION_TEST_CONCURRENT, 5)
                        .collectAsStateWithLifecycle(5)
                    TextFieldPreference(
                        value = value,
                        onValueChange = { DataStore.connectionTestConcurrent = it },
                        title = { Text(stringResource(R.string.test_concurrency)) },
                        textToValue = { it.toIntOrNull() ?: 5 },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.fast_forward),
                                null,
                            )
                        },
                        summary = { Text(value.toString()) },
                        valueToText = { it.toString() },
                        textField = { value, onValueChange, onOk ->
                            UIntegerTextField(value, onValueChange, onOk, minValue = 5)
                        },
                    )
                }
                item(Key.CONNECTION_TEST_TIMEOUT, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .intFlow(Key.CONNECTION_TEST_TIMEOUT, 3000)
                        .collectAsStateWithLifecycle(3000)
                    TextFieldPreference(
                        value = value,
                        onValueChange = { DataStore.connectionTestTimeout = it },
                        title = { Text(stringResource(R.string.test_timeout)) },
                        textToValue = { it.toIntOrNull() ?: 3000 },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.apps),
                                null,
                            )
                        },
                        summary = { Text(value.toString()) },
                        valueToText = { it.toString() },
                        textField = { value, onValueChange, onOk ->
                            UIntegerTextField(
                                value,
                                onValueChange,
                                onOk,
                                minValue = 1000,
                                maxValue = 10000,
                            )
                        },
                    )
                }
                item(Key.ACQUIRE_WAKE_LOCK, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.ACQUIRE_WAKE_LOCK, true)
                        .collectAsStateWithLifecycle(true)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.acquireWakeLock = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.acquire_wake_lock)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.developer_board),
                                null,
                            )
                        },
                        summary = { Text(stringResource(R.string.acquire_wake_lock_summary)) },
                    )
                }
                item(Key.CERT_PROVIDER, TYPE_LIST_PREFERENCE) {
                    fun certProviderTextRes(index: Int): Int = when (index) {
                        CertProvider.SYSTEM -> R.string.follow_system
                        CertProvider.MOZILLA -> R.string.mozilla
                        CertProvider.SYSTEM_AND_USER -> R.string.system_and_user
                        else -> R.string.mozilla
                    }

                    val context = LocalContext.current
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
                        ),
                        title = { Text(stringResource(R.string.certificate_authority)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.push_pin),
                                null,
                            )
                        },
                        summary = { Text(stringResource(certProviderTextRes(value))) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(context.getString(certProviderTextRes(it))) },
                    )
                }
                item(Key.DISABLE_PROCESS_TEXT, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.DISABLE_PROCESS_TEXT, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.disableProcessText = it
                            context.packageManager.setComponentEnabledSetting(
                                ComponentName(
                                    context,
                                    "io.nekohasekai.sagernet.ui.ProcessTextActivityAlias",
                                ),
                                if (it) {
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                                } else {
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                },
                                PackageManager.DONT_KILL_APP,
                            )
                        },
                        title = { Text(stringResource(R.string.disable_process_text)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.format_align_left),
                                null,
                            )
                        },
                    )
                }

                item(Key.NTP_SETTINGS, TYPE_PREFERENCE_CATEGORY) {
                    PreferenceCategory(text = { Text(stringResource(R.string.ntp_category)) })
                }
                item(Key.ENABLE_NTP, TYPE_SWITCH_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .booleanFlow(Key.ENABLE_NTP, false)
                        .collectAsStateWithLifecycle(false)
                    SwitchPreference(
                        value = value,
                        onValueChange = {
                            DataStore.ntpEnable = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.enable_ntp)) },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.timelapse),
                                null,
                            )
                        },
                        summary = { Text(stringResource(R.string.ntp_sum)) },
                    )
                }
                item(Key.NTP_SERVER, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.NTP_SERVER, "time.apple.com")
                        .collectAsStateWithLifecycle("time.apple.com")
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.ntpAddress = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.ntp_server_address)) },
                        textToValue = { it },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.router),
                                null,
                            )
                        },
                        summary = { Text(LocalContext.current.contentOrUnset(value)) },
                        valueToText = { it },
                        enabled = ntpEnableState,
                    )
                }
                item(Key.NTP_PORT, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .intFlow(Key.NTP_PORT, 123)
                        .collectAsStateWithLifecycle(123)
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.ntpPort = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.ntp_server_port)) },
                        textToValue = { it.toIntOrNull() ?: 123 },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.directions_boat),
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
                item(Key.NTP_INTERVAL, TYPE_TEXT_FIELD_PREFERENCE) {
                    val value by DataStore.configurationStore
                        .stringFlow(Key.NTP_INTERVAL, "30m")
                        .collectAsStateWithLifecycle("30m")
                    TextFieldPreference(
                        value = value,
                        onValueChange = {
                            DataStore.ntpInterval = it
                            needReload()
                        },
                        title = { Text(stringResource(R.string.ntp_sync_interval)) },
                        textToValue = { it },
                        icon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.flip_camera_android),
                                null,
                            )
                        },
                        summary = { Text(LocalContext.current.contentOrUnset(value)) },
                        valueToText = { it },
                        enabled = ntpEnableState,
                    ) { value, onValueChange, onOk ->
                        DurationTextField(value, onValueChange, onOk)
                    }
                }
            }
        }
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

private inline fun LazyListScope.colorPickerPreference(
    modifier: Modifier = Modifier.fillMaxWidth(),
    key: String,
    crossinline title: @Composable () -> Unit,
    enabled: Boolean = true,
    crossinline postChange: () -> Unit,
) {
    item(key, TYPE_COLOR_PICKER_PREFERENCE) {
        val resources = LocalResources.current
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            title = { title() },
            modifier = modifier,
            enabled = enabled,
            icon = {
                Icon(
                    ImageVector.vectorResource(R.drawable.color_lens),
                    null,
                )
            },
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
            val colors = resources.getIntArray(R.array.material_colors)
            val currentTheme by DataStore.configurationStore
                .intFlow(key, DEFAULT)
                .collectAsStateWithLifecycle(DEFAULT)

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
                            text = stringResource(R.string.theme),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp),
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
                                            postChange()
                                        },
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    Circle(
                                        modifier = Modifier.size(48.dp),
                                        color = Color(colors[index]),
                                        selected = currentTheme == theme,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(stringResource(android.R.string.cancel)) {
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
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
