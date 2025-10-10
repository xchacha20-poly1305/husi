package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Segment
import androidx.compose.material.icons.filled.AddRoad
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.FiberSmartRecord
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LegendToggle
import androidx.compose.material.icons.filled.LocalAirport
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.DurationTextField
import io.nekohasekai.sagernet.compose.MapPreference
import io.nekohasekai.sagernet.compose.MultilineTextField
import io.nekohasekai.sagernet.compose.PreferenceCategory
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.UIntegerTextField
import io.nekohasekai.sagernet.compose.paddingExceptBottom
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.contentOrUnset
import io.nekohasekai.sagernet.ui.configuration.ProfileSelectActivity
import io.nekohasekai.sagernet.ui.profile.ConfigEditActivity
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.MultiSelectListPreference
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@ExperimentalMaterial3Api
class RouteSettingsActivity : ComposeActivity() {

    companion object {
        const val EXTRA_ROUTE_ID = "id"
        private const val KEY_ACTION_OPTIONS = "action_options"
    }

    private val viewModel by viewModels<RouteSettingsActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val editingId = intent.getLongExtra(EXTRA_ROUTE_ID, -1L)
        viewModel.loadRule(editingId)

        setContent {
            val isDirty by viewModel.isDirty.collectAsState()
            var showBackAlert by remember { mutableStateOf(false) }
            BackHandler(enabled = isDirty) { showBackAlert = true }

            AppTheme {
                val windowInsets = WindowInsets.safeDrawing
                val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

                var showExpandedMenu by remember { mutableStateOf(false) }
                var showDeleteConfirm by remember { mutableStateOf(false) }
                var showUnchangedAlert by remember { mutableStateOf(false) }

                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.menu_route)) },
                            navigationIcon = {
                                SimpleIconButton(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.close),
                                ) {
                                    onBackPressedDispatcher.onBackPressed()
                                }
                            },
                            actions = {
                                SimpleIconButton(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    onClick = { showDeleteConfirm = true },
                                )
                                SimpleIconButton(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = stringResource(R.string.apply),
                                ) {
                                    if (isDirty) {
                                        saveAndExit()
                                    } else {
                                        showUnchangedAlert = true
                                    }
                                }

                                Box {
                                    SimpleIconButton(Icons.Filled.MoreVert) {
                                        showExpandedMenu = true
                                    }
                                    DropdownMenu(
                                        expanded = showExpandedMenu,
                                        onDismissRequest = { showExpandedMenu = false },
                                    ) {
                                        Text(stringResource(R.string.custom_config))
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_route)) },
                                            onClick = {
                                                editCustomConfig.launch(
                                                    Intent(
                                                        this@RouteSettingsActivity,
                                                        ConfigEditActivity::class.java,
                                                    ).putExtra(
                                                        ConfigEditActivity.EXTRA_CUSTOM_CONFIG,
                                                        uiState.customConfig,
                                                    )
                                                )
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.cag_dns)) },
                                            onClick = {
                                                editCustomDnsConfig.launch(
                                                    Intent(
                                                        this@RouteSettingsActivity,
                                                        ConfigEditActivity::class.java,
                                                    ).putExtra(
                                                        ConfigEditActivity.EXTRA_CUSTOM_CONFIG,
                                                        uiState.customDnsConfig,
                                                    )
                                                )
                                            },
                                        )
                                    }
                                }
                            },
                            windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { innerPadding ->
                    RouteSettings(Modifier.paddingExceptBottom(innerPadding), uiState)
                }

                if (showBackAlert) AlertDialog(
                    onDismissRequest = { showBackAlert = false },
                    confirmButton = {
                        TextButton(stringResource(android.R.string.ok)) {
                            saveAndExit()
                        }
                    },
                    dismissButton = {
                        TextButton(stringResource(R.string.no)) {
                            finish()
                        }
                    },
                    icon = { Icon(Icons.Filled.QuestionMark, null) },
                    title = { Text(stringResource(R.string.unsaved_changes_prompt)) },
                )
                if (showDeleteConfirm) AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    confirmButton = {
                        TextButton(stringResource(android.R.string.ok)) {
                            viewModel.deleteRule()
                            finish()
                        }
                    },
                    dismissButton = {
                        TextButton(stringResource(android.R.string.cancel)) {
                            showDeleteConfirm = false
                        }
                    },
                    icon = { Icon(Icons.Filled.Warning, null) },
                    title = { Text(stringResource(R.string.delete_confirm_prompt)) },
                )
                if (showUnchangedAlert) AlertDialog(
                    onDismissRequest = { showUnchangedAlert = false },
                    confirmButton = {
                        TextButton(stringResource(android.R.string.ok)) {
                            showUnchangedAlert = false
                        }
                    },
                    icon = { Icon(Icons.Filled.WarningAmber, null) },
                    title = { Text(stringResource(R.string.empty_route)) },
                    text = { Text(stringResource(R.string.empty_route_notice)) },
                )
            }
        }

    }

    @Composable
    private fun RouteSettings(
        modifier: Modifier,
        uiState: RouteSettingsActivityUiState,
    ) {
        ProvidePreferenceLocals {
            LazyColumn(modifier = modifier) {
                item("name") {
                    TextFieldPreference(
                        value = uiState.name,
                        onValueChange = { viewModel.setName(it) },
                        title = { Text(stringResource(R.string.route_name)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.EmojiSymbols, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(uiState.name)) },
                        valueToText = { it },
                    )
                }
                item("apps") {
                    Preference(
                        title = { Text(stringResource(R.string.apps)) },
                        icon = { Icon(Icons.Filled.LegendToggle, null) },
                        summary = {
                            val text = when (val size = uiState.packages.size) {
                                0 -> stringResource(androidx.preference.R.string.not_set)
                                in 1..5 -> uiState.packages.joinToString("\n")
                                else -> stringResource(R.string.apps_message, size)
                            }
                            Text(text)
                        },
                        onClick = {
                            selectAppList.launch(
                                Intent(this@RouteSettingsActivity, AppListActivity::class.java)
                                    .putStringArrayListExtra(
                                        AppListActivity.EXTRA_APP_LIST,
                                        ArrayList(uiState.packages),
                                    )
                            )
                        },
                    )
                }
                item("network_type") {
                    MultiSelectListPreference(
                        value = uiState.networkType,
                        onValueChange = { viewModel.setNetworkType(it) },
                        values = networkTypes,
                        title = { Text(stringResource(R.string.network_type)) },
                        icon = { Icon(Icons.Filled.Public, null) },
                        summary = {
                            val text = if (uiState.networkType.isEmpty()) {
                                stringResource(androidx.preference.R.string.not_set)
                            } else {
                                uiState.networkType.joinToString("\n")
                            }
                            Text(text)
                        },
                        valueToText = { AnnotatedString(it) },
                    )
                }
                item("action") {
                    ListPreference(
                        value = uiState.action,
                        onValueChange = { viewModel.setAction(it) },
                        values = listOf(
                            SingBoxOptions.ACTION_ROUTE,
                            SingBoxOptions.ACTION_ROUTE_OPTIONS,
                            SingBoxOptions.ACTION_SNIFF,
                            SingBoxOptions.ACTION_RESOLVE,
                            SingBoxOptions.ACTION_HIJACK_DNS,
                            SingBoxOptions.ACTION_REJECT,
                        ),
                        title = { Text(stringResource(R.string.route_action)) },
                        icon = { Icon(Icons.Filled.Shuffle, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(uiState.action)) },
                        type = ListPreferenceType.DROPDOWN_MENU,
                        valueToText = { AnnotatedString(it) },
                    )
                }

                item("category_settings") {
                    PreferenceCategory(text = { Text(stringResource(R.string.settings)) })
                }
                item("domains") {
                    TextFieldPreference(
                        value = uiState.domains,
                        onValueChange = { viewModel.setDomains(it) },
                        title = { Text("domain") },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Domain, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(uiState.domains)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                }
                item("ip") {
                    TextFieldPreference(
                        value = uiState.ip,
                        onValueChange = { viewModel.setIp(it) },
                        title = { Text("ip") },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.AddRoad, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(uiState.ip)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                }
                item("port") {
                    TextFieldPreference(
                        value = uiState.port,
                        onValueChange = { viewModel.setPort(it) },
                        title = { Text("port") },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.DirectionsBoat, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(uiState.port)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                }
                item("source_port") {
                    TextFieldPreference(
                        value = uiState.sourcePort,
                        onValueChange = { viewModel.setSourcePort(it) },
                        title = { Text("sourcePort") },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Home, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(uiState.sourcePort)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                }
                item("network") {
                    MultiSelectListPreference(
                        value = uiState.network,
                        onValueChange = { viewModel.setNetwork(it) },
                        values = listOf(
                            SingBoxOptions.NetworkTCP,
                            SingBoxOptions.NetworkUDP,
                            SingBoxOptions.NetworkICMP,
                        ),
                        title = { Text("network") },
                        icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, null) },
                        summary = {
                            val text = if (uiState.network.isEmpty()) {
                                stringResource(androidx.preference.R.string.not_set)
                            } else {
                                uiState.network.joinToString("\n")
                            }
                            Text(text)
                        },
                        valueToText = { AnnotatedString(it) },
                    )
                }
                item("source") {
                    TextFieldPreference(
                        value = uiState.source,
                        onValueChange = { viewModel.setSource(it) },
                        title = { Text("source") },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.LocalBar, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(uiState.source)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                }
                item("sniff_protocol") {
                    MultiSelectListPreference(
                        value = uiState.protocol,
                        onValueChange = { viewModel.setProtocol(it) },
                        values = sniffers,
                        title = { Text("protocol") },
                        icon = { Icon(Icons.Filled.Layers, null) },
                        summary = {
                            val text = if (uiState.protocol.isEmpty()) {
                                stringResource(androidx.preference.R.string.not_set)
                            } else {
                                uiState.protocol.joinToString("\n")
                            }
                            Text(text)
                        },
                        valueToText = { AnnotatedString(it) },
                    )
                }
                item("client") {
                    TextFieldPreference(
                        value = uiState.client,
                        onValueChange = { viewModel.setClient(it) },
                        title = { Text("client") },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Fingerprint, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(uiState.client)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                }

                val showWifi =
                    uiState.networkType.contains(SingBoxOptions.NETWORK_TYPE_WIFI)
                if (showWifi) {
                    item("ssid") {
                        TextFieldPreference(
                            value = uiState.ssid,
                            onValueChange = { viewModel.setSsid(it) },
                            title = { Text("SSID") },
                            textToValue = { it },
                            icon = { Icon(Icons.Filled.Wifi, null) },
                            summary = { Text(LocalContext.current.contentOrUnset(uiState.ssid)) },
                            valueToText = { it },
                            textField = { value, onValueChange, onOk ->
                                MultilineTextField(value, onValueChange, onOk)
                            },
                        )
                    }
                    item("bssid") {
                        TextFieldPreference(
                            value = uiState.bssid,
                            onValueChange = { viewModel.setBssid(it) },
                            title = { Text("BSSID") },
                            textToValue = { it },
                            icon = { Icon(Icons.Filled.WifiFind, null) },
                            summary = { Text(LocalContext.current.contentOrUnset(uiState.bssid)) },
                            valueToText = { it },
                            textField = { value, onValueChange, onOk ->
                                MultilineTextField(value, onValueChange, onOk)
                            },
                        )
                    }
                }

                item("clash_mode") {
                    TextFieldPreference(
                        value = uiState.clashMode,
                        onValueChange = { viewModel.setClashMode(it) },
                        title = { Text(stringResource(R.string.clash_mode)) },
                        textToValue = { it },
                        icon = { Icon(Icons.Filled.Category, null) },
                        summary = { Text(LocalContext.current.contentOrUnset(uiState.clashMode)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                }
                item("network_is_expensive") {
                    SwitchPreference(
                        value = uiState.networkIsExpensive,
                        onValueChange = { viewModel.setNetworkIsExpensive(it) },
                        title = { Text(stringResource(R.string.network_expensive)) },
                        icon = { Icon(Icons.Filled.MonetizationOn, null) },
                    )
                }
                item("network_interface_address") {
                    MapPreference(
                        value = uiState.networkInterfaceAddress,
                        keys = LinkedHashSet(networkTypes),
                        onValueChange = { viewModel.setNetworkInterfaceAddress(it) },
                        displayKey = { it },
                        valueToText = { it },
                        textToValue = { it },
                        title = { Text("networkInterfaceAddress") },
                        icon = { Icon(Icons.Filled.LocalAirport, null) },
                        summary = { Text(uiState.networkInterfaceAddress.toString()) },
                    )
                }

                when (uiState.action) {
                    "", SingBoxOptions.ACTION_ROUTE -> {
                        item(KEY_ACTION_OPTIONS) {
                            PreferenceCategory(text = { Text(stringResource(R.string.menu_route)) })
                        }
                        item("outbound") {
                            ListPreference(
                                value = uiState.outbound,
                                onValueChange = {
                                    when (it) {
                                        RuleEntity.OUTBOUND_PROXY,
                                        RuleEntity.OUTBOUND_DIRECT,
                                        RuleEntity.OUTBOUND_BLOCK,
                                            -> viewModel.setOutbound(it)

                                        else -> selectProfileForAdd.launch(
                                            Intent(
                                                this@RouteSettingsActivity,
                                                ProfileSelectActivity::class.java,
                                            ).putExtra(
                                                ProfileSelectActivity.EXTRA_SELECTED,
                                                uiState.outbound,
                                            )
                                        )
                                    }
                                },
                                values = listOf(
                                    RuleEntity.OUTBOUND_PROXY,
                                    RuleEntity.OUTBOUND_DIRECT,
                                    RuleEntity.OUTBOUND_BLOCK,
                                    -4L, // Custom
                                ),
                                title = { Text(stringResource(R.string.outbound)) },
                                icon = { Icon(Icons.Filled.Router, null) },
                                summary = {
                                    val text = when (uiState.outbound) {
                                        RuleEntity.OUTBOUND_PROXY -> stringResource(R.string.route_proxy)
                                        RuleEntity.OUTBOUND_DIRECT -> stringResource(R.string.route_bypass)
                                        RuleEntity.OUTBOUND_BLOCK -> stringResource(R.string.route_block)
                                        else -> SagerDatabase.proxyDao.getById(uiState.outbound)
                                            ?.displayName()
                                            ?: stringResource(androidx.preference.R.string.not_set)
                                    }
                                    Text(text)
                                },
                                type = ListPreferenceType.DROPDOWN_MENU,
                                valueToText = {
                                    val id = when (it) {
                                        RuleEntity.OUTBOUND_PROXY -> R.string.route_proxy
                                        RuleEntity.OUTBOUND_DIRECT -> R.string.route_bypass
                                        RuleEntity.OUTBOUND_BLOCK -> R.string.route_block
                                        else -> R.string.select_profile
                                    }
                                    AnnotatedString(getString(id))
                                },
                            )
                        }
                    }

                    SingBoxOptions.ACTION_ROUTE_OPTIONS -> {
                        item(KEY_ACTION_OPTIONS) {
                            PreferenceCategory(text = { Text(stringResource(R.string.route_options)) })
                        }
                        item("override_address") {
                            TextFieldPreference(
                                value = uiState.overrideAddress,
                                onValueChange = { viewModel.setOverrideAddress(it) },
                                title = { Text(stringResource(R.string.override_address)) },
                                textToValue = { it },
                                icon = { Icon(Icons.Filled.LocationOn, null) },
                                summary = { Text(LocalContext.current.contentOrUnset(uiState.overrideAddress)) },
                                valueToText = { it },
                            )
                        }
                        item("override_port") {
                            TextFieldPreference(
                                value = uiState.overridePort,
                                onValueChange = { viewModel.setOverridePort(it) },
                                title = { Text(stringResource(R.string.override_port)) },
                                textToValue = { it.toIntOrNull() ?: 0 },
                                icon = { Icon(Icons.Filled.PinDrop, null) },
                                summary = { Text(LocalContext.current.contentOrUnset(uiState.overridePort)) },
                                textField = { value, onValueChange, onOk ->
                                    UIntegerTextField(value, onValueChange, onOk)
                                },
                            )
                        }
                        item("tls_fragment") {
                            SwitchPreference(
                                value = uiState.tlsFragment,
                                onValueChange = { viewModel.setTlsFragment(it) },
                                title = { Text(stringResource(R.string.tls_fragment)) },
                                icon = { Icon(Icons.AutoMirrored.Filled.Segment, null) },
                            )
                        }
                        item("tls_fragment_fallback_delay") {
                            TextFieldPreference(
                                value = uiState.tlsFragmentFallbackDelay,
                                onValueChange = { viewModel.setTlsFragmentFallbackDelay(it) },
                                title = { Text(stringResource(R.string.tls_fragment_fallback_delay)) },
                                textToValue = { it },
                                enabled = uiState.tlsFragment,
                                icon = { Icon(Icons.Filled.HourglassTop, null) },
                                summary = { Text(LocalContext.current.contentOrUnset(uiState.tlsFragmentFallbackDelay)) },
                                textField = { value, onValueChange, onOk ->
                                    DurationTextField(value, onValueChange, onOk)
                                },
                            )
                        }
                        item("tls_record_fragment") {
                            SwitchPreference(
                                value = uiState.tlsRecordFragment,
                                onValueChange = { viewModel.setTlsRecordFragment(it) },
                                title = { Text(stringResource(R.string.tls_record_fragment)) },
                                icon = { Icon(Icons.Filled.FiberSmartRecord, null) },
                            )
                        }
                    }

                    SingBoxOptions.ACTION_RESOLVE -> {
                        item(KEY_ACTION_OPTIONS) {
                            PreferenceCategory(text = { Text("Resolve") })
                        }
                        item("resolve_strategy") {
                            ListPreference(
                                value = uiState.resolveStrategy,
                                onValueChange = { viewModel.setResolveStrategy(it) },
                                values = listOf(
                                    "",
                                    SingBoxOptions.STRATEGY_PREFER_IPV6,
                                    SingBoxOptions.STRATEGY_PREFER_IPV4,
                                    SingBoxOptions.STRATEGY_IPV4_ONLY,
                                    SingBoxOptions.STRATEGY_IPV6_ONLY,
                                ),
                                title = { Text("Resolve Strategy") },
                                icon = { Icon(Icons.Filled.Dns, null) },
                                summary = {
                                    val text = uiState.resolveStrategy.blankAsNull()
                                        ?: stringResource(R.string.auto)
                                    Text(text)
                                },
                                type = ListPreferenceType.DROPDOWN_MENU,
                                valueToText = { AnnotatedString(it) },
                            )
                        }
                        item("disable_cache") {
                            SwitchPreference(
                                value = uiState.resolveDisableCache,
                                onValueChange = { viewModel.setResolveDisableCache(it) },
                                title = { Text("Disable Cache") },
                                icon = { Icon(Icons.Filled.Cached, null) },
                            )
                        }
                        item("rewrite_ttl") {
                            TextFieldPreference(
                                value = uiState.resolveRewriteTTL,
                                onValueChange = { viewModel.setResolveRewriteTTL(it) },
                                title = { Text("Rewrite TTL") },
                                textToValue = { it.toIntOrNull() ?: 0 },
                                icon = { Icon(Icons.Filled.Timer, null) },
                                summary = {
                                    val text =
                                        uiState.resolveRewriteTTL.takeIf { it > 0 }?.toString()
                                            ?: stringResource(androidx.preference.R.string.not_set)
                                    Text(text)
                                },
                                textField = { value, onValueChange, onOk ->
                                    UIntegerTextField(value, onValueChange, onOk)
                                },
                            )
                        }
                        item("client_subnet") {
                            TextFieldPreference(
                                value = uiState.resolveClientSubnet,
                                onValueChange = { viewModel.setResolveClientSubnet(it) },
                                title = { Text("Client Subnet") },
                                textToValue = { it },
                                icon = { Icon(Icons.Filled.PushPin, null) },
                                summary = { Text(LocalContext.current.contentOrUnset(uiState.resolveClientSubnet)) },
                                valueToText = { it },
                            )
                        }
                    }

                    SingBoxOptions.ACTION_SNIFF -> {
                        item(KEY_ACTION_OPTIONS) {
                            PreferenceCategory(text = { Text(stringResource(R.string.sniff)) })
                        }
                        item("sniff_timeout") {
                            TextFieldPreference(
                                value = uiState.sniffTimeout,
                                onValueChange = { viewModel.setSniffTimeout(it) },
                                title = { Text(stringResource(R.string.sniff_timeout)) },
                                textToValue = { it },
                                icon = { Icon(Icons.Filled.Timelapse, null) },
                                summary = { Text(LocalContext.current.contentOrUnset(uiState.sniffTimeout)) },
                                textField = { value, onValueChange, onOk ->
                                    DurationTextField(value, onValueChange, onOk)
                                },
                            )
                        }
                        item("sniffers") {
                            MultiSelectListPreference(
                                value = uiState.sniffers,
                                onValueChange = { viewModel.setSniffers(it) },
                                values = sniffers,
                                title = { Text("Sniffers") },
                                icon = { Icon(Icons.Filled.Layers, null) },
                                summary = {
                                    val text = if (uiState.sniffers.isEmpty()) {
                                        stringResource(androidx.preference.R.string.not_set)
                                    } else {
                                        uiState.sniffers.joinToString("\n")
                                    }
                                    Text(text)
                                },
                                valueToText = { AnnotatedString(it) },
                            )
                        }
                    }
                }

                item("bottom_padding") {
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }

    private fun saveAndExit() {
        viewModel.save()
        setResult(RESULT_OK)
        finish()
    }

    private val selectProfileForAdd = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            val profile = ProfileManager.getProfile(
                it.data!!.getLongExtra(
                    ProfileSelectActivity.EXTRA_PROFILE_ID, 0L
                )
            ) ?: return@registerForActivityResult
            viewModel.setOutbound(profile.id)
        }
    }

    private val selectAppList = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            val packages = it.data?.getStringArrayListExtra(AppListActivity.EXTRA_APP_LIST)
                ?: ArrayList()
            viewModel.setPackages(packages.toSet())
        }
    }

    private val editCustomConfig = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.setCustomConfig(it.data!!.getStringExtra(ConfigEditActivity.EXTRA_CUSTOM_CONFIG)!!)
        }
    }

    private val editCustomDnsConfig = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.setCustomDnsConfig(it.data!!.getStringExtra(ConfigEditActivity.EXTRA_CUSTOM_CONFIG)!!)
        }
    }

    private val networkTypes = listOf(
        SingBoxOptions.NETWORK_TYPE_WIFI,
        SingBoxOptions.NETWORK_TYPE_CELLULAR,
        SingBoxOptions.NETWORK_TYPE_ETHERNET,
        SingBoxOptions.NETWORK_TYPE_OTHER,
    )

    private val sniffers = listOf(
        SingBoxOptions.SNIFF_HTTP,
        SingBoxOptions.SNIFF_TLS,
        SingBoxOptions.SNIFF_QUIC,
        SingBoxOptions.SNIFF_STUN,
        SingBoxOptions.SNIFF_DNS,
        SingBoxOptions.SNIFF_BITTORRENT,
        SingBoxOptions.SNIFF_DTLS,
        SingBoxOptions.SNIFF_SSH,
        SingBoxOptions.SNIFF_RDP,
        SingBoxOptions.SNIFF_NTP,
    )

}
