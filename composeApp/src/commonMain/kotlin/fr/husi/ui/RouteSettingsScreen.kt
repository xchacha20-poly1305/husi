@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.BackHandler
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.DurationTextField
import fr.husi.compose.MapPreference
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.withNavigation
import fr.husi.database.ProfileManager
import fr.husi.database.RuleEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.contentOrUnset
import fr.husi.repository.repo
import fr.husi.resources.Res
import fr.husi.resources.add_road
import fr.husi.resources.apply
import fr.husi.resources.auto
import fr.husi.resources.cached
import fr.husi.resources.cag_dns
import fr.husi.resources.cancel
import fr.husi.resources.category
import fr.husi.resources.clash_mode
import fr.husi.resources.close
import fr.husi.resources.compare_arrows
import fr.husi.resources.custom_config
import fr.husi.resources.delete
import fr.husi.resources.delete_confirm_prompt
import fr.husi.resources.directions_boat
import fr.husi.resources.dns
import fr.husi.resources.dns_only
import fr.husi.resources.domain
import fr.husi.resources.done
import fr.husi.resources.emoji_symbols
import fr.husi.resources.empty_route
import fr.husi.resources.empty_route_notice
import fr.husi.resources.fiber_smart_record
import fr.husi.resources.fingerprint
import fr.husi.resources.home
import fr.husi.resources.hourglass_top
import fr.husi.resources.layers
import fr.husi.resources.local_airport
import fr.husi.resources.local_bar
import fr.husi.resources.location_on
import fr.husi.resources.menu_route
import fr.husi.resources.monetization_on
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.network_expensive
import fr.husi.resources.network_type
import fr.husi.resources.no
import fr.husi.resources.not_set
import fr.husi.resources.ok
import fr.husi.resources.outbound
import fr.husi.resources.override_address
import fr.husi.resources.override_port
import fr.husi.resources.pin_drop
import fr.husi.resources.public_icon
import fr.husi.resources.push_pin
import fr.husi.resources.question_mark
import fr.husi.resources.route_action
import fr.husi.resources.route_block
import fr.husi.resources.route_bypass
import fr.husi.resources.route_name
import fr.husi.resources.route_options
import fr.husi.resources.route_proxy
import fr.husi.resources.router
import fr.husi.resources.segment
import fr.husi.resources.select_profile
import fr.husi.resources.settings
import fr.husi.resources.shuffle
import fr.husi.resources.sniff
import fr.husi.resources.sniff_timeout
import fr.husi.resources.timelapse
import fr.husi.resources.timer
import fr.husi.resources.tls_fragment
import fr.husi.resources.tls_fragment_fallback_delay
import fr.husi.resources.tls_record_fragment
import fr.husi.resources.unsaved_changes_prompt
import fr.husi.resources.warning
import fr.husi.resources.warning_amber
import fr.husi.resources.wifi
import fr.husi.resources.wifi_find
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.MultiSelectListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

private const val KEY_ACTION_OPTIONS = "action_options"

@ExperimentalMaterial3Api
@Composable
internal fun RouteSettingsScreen(
    routeId: Long,
    initialState: RouteSettingsUiState?,
    onBackPress: () -> Unit,
    onSaved: () -> Unit,
    onOpenProfileSelect: (preSelected: Long?, onSelected: (Long) -> Unit) -> Unit,
    onOpenAppList: (Set<String>, (Set<String>) -> Unit) -> Unit,
    onOpenConfigEditor: (String, (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteSettingsViewModel = viewModel { RouteSettingsViewModel() },
) {
    LaunchedEffect(viewModel, routeId, initialState) {
        viewModel.initialize(routeId, initialState)
    }

    val isDirty by viewModel.isDirty.collectAsState()
    var showBackAlert by remember { mutableStateOf(false) }
    BackHandler(enabled = isDirty) { showBackAlert = true }

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showExpandedMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUnchangedAlert by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    fun saveAndExit() {
        viewModel.save()
        onSaved()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.menu_route)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                    ) {
                        if (isDirty) {
                            showBackAlert = true
                        } else {
                            onBackPress()
                        }
                    }
                },
                actions = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.delete),
                        contentDescription = stringResource(Res.string.delete),
                        onClick = { showDeleteConfirm = true },
                    )
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.done),
                        contentDescription = stringResource(Res.string.apply),
                    ) {
                        if (isDirty) {
                            saveAndExit()
                        } else {
                            showUnchangedAlert = true
                        }
                    }

                    Box {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.more_vert),
                            contentDescription = stringResource(Res.string.more),
                        ) {
                            showExpandedMenu = true
                        }
                        DropdownMenuPopup(
                            expanded = showExpandedMenu,
                            onDismissRequest = { showExpandedMenu = false },
                        ) {
                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShape(0, 2),
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        MenuDefaults.Label {
                                            Text(
                                                text = stringResource(Res.string.custom_config),
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                        }
                                    },
                                    onClick = {},
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.menu_route)) },
                                    onClick = {
                                        showExpandedMenu = false
                                        onOpenConfigEditor(
                                            uiState.customConfig,
                                            viewModel::setCustomConfig,
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.cag_dns)) },
                                    onClick = {
                                        showExpandedMenu = false
                                        onOpenConfigEditor(
                                            uiState.customDnsConfig,
                                            viewModel::setCustomDnsConfig,
                                        )
                                    },
                                )
                            }
                            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShape(1, 2),
                            ) {
                                DropdownMenuItem(
                                    selected = uiState.dnsOnly,
                                    onClick = {
                                        showExpandedMenu = false
                                        viewModel.setDnsOnly(!uiState.dnsOnly)
                                    },
                                    text = { Text(stringResource(Res.string.dns_only)) },
                                    shapes = MenuDefaults.itemShape(0, 1),
                                )
                            }
                        }
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        ProvidePreferenceLocals {
            RouteSettings(
                paddings = innerPadding,
                uiState = uiState,
                viewModel = viewModel,
                onSelectOutboundProfile = { selected ->
                    onOpenProfileSelect(selected.takeIf { it > 0 }) { id ->
                        val profile = ProfileManager.getProfile(id) ?: return@onOpenProfileSelect
                        viewModel.setOutbound(profile.id)
                    }
                },
                onSelectApps = { packages ->
                    if (repo.isAndroid) {
                        onOpenAppList(packages) { selected ->
                            viewModel.setPackages(selected)
                        }
                    } else {
                        viewModel.setPackages(packages)
                    }
                },
            )
        }
    }

    if (showBackAlert) {
        AlertDialog(
            onDismissRequest = { showBackAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    saveAndExit()
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.no)) {
                    onBackPress()
                }
            },
            icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
            title = { Text(stringResource(Res.string.unsaved_changes_prompt)) },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    viewModel.deleteRule()
                    onBackPress()
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    showDeleteConfirm = false
                }
            },
            icon = { Icon(vectorResource(Res.drawable.warning), null) },
            title = { Text(stringResource(Res.string.delete_confirm_prompt)) },
        )
    }
    if (showUnchangedAlert) {
        AlertDialog(
            onDismissRequest = { showUnchangedAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    showUnchangedAlert = false
                }
            },
            icon = { Icon(vectorResource(Res.drawable.warning_amber), null) },
            title = { Text(stringResource(Res.string.empty_route)) },
            text = { Text(stringResource(Res.string.empty_route_notice)) },
        )
    }
}

@Composable
private fun RouteSettings(
    paddings: PaddingValues,
    uiState: RouteSettingsUiState,
    viewModel: RouteSettingsViewModel,
    onSelectOutboundProfile: (Long) -> Unit,
    onSelectApps: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val contentPadding = paddings.withNavigation()
    Row(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = contentPadding,
        ) {
            item("name") {
                TextFieldPreference(
                    value = uiState.name,
                    onValueChange = { viewModel.setName(it) },
                    title = { Text(stringResource(Res.string.route_name)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.emoji_symbols), null) },
                    summary = { Text(contentOrUnset(uiState.name)) },
                    valueToText = { it },
                )
            }
            appSelectPreference(uiState.packages, onSelectApps)
            item("network_type") {
                MultiSelectListPreference(
                    value = uiState.networkType,
                    onValueChange = { viewModel.setNetworkType(it) },
                    values = networkTypes,
                    title = { Text(stringResource(Res.string.network_type)) },
                    icon = { Icon(vectorResource(Res.drawable.public_icon), null) },
                    summary = {
                        val text = if (uiState.networkType.isEmpty()) {
                            stringResource(Res.string.not_set)
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
                    title = { Text(stringResource(Res.string.route_action)) },
                    icon = { Icon(vectorResource(Res.drawable.shuffle), null) },
                    summary = { Text(contentOrUnset(uiState.action)) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(it) },
                )
            }

            item("category_settings") {
                PreferenceCategory(text = { Text(stringResource(Res.string.settings)) })
            }
            item("domains") {
                TextFieldPreference(
                    value = uiState.domains,
                    onValueChange = { viewModel.setDomains(it) },
                    title = { Text("domain") },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.domain), null) },
                    summary = { Text(contentOrUnset(uiState.domains)) },
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
                    icon = { Icon(vectorResource(Res.drawable.add_road), null) },
                    summary = { Text(contentOrUnset(uiState.ip)) },
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
                    icon = { Icon(vectorResource(Res.drawable.directions_boat), null) },
                    summary = { Text(contentOrUnset(uiState.port)) },
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
                    icon = { Icon(vectorResource(Res.drawable.home), null) },
                    summary = { Text(contentOrUnset(uiState.sourcePort)) },
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
                    icon = { Icon(vectorResource(Res.drawable.compare_arrows), null) },
                    summary = {
                        val text = if (uiState.network.isEmpty()) {
                            stringResource(Res.string.not_set)
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
                    icon = { Icon(vectorResource(Res.drawable.local_bar), null) },
                    summary = { Text(contentOrUnset(uiState.source)) },
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
                    icon = { Icon(vectorResource(Res.drawable.layers), null) },
                    summary = {
                        val text = if (uiState.protocol.isEmpty()) {
                            stringResource(Res.string.not_set)
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
                    icon = { Icon(vectorResource(Res.drawable.fingerprint), null) },
                    summary = { Text(contentOrUnset(uiState.client)) },
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
                        icon = { Icon(vectorResource(Res.drawable.wifi), null) },
                        summary = { Text(contentOrUnset(uiState.ssid)) },
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
                        icon = { Icon(vectorResource(Res.drawable.wifi_find), null) },
                        summary = { Text(contentOrUnset(uiState.bssid)) },
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
                    title = { Text(stringResource(Res.string.clash_mode)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.category), null) },
                    summary = { Text(contentOrUnset(uiState.clashMode)) },
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
                    title = { Text(stringResource(Res.string.network_expensive)) },
                    icon = { Icon(vectorResource(Res.drawable.monetization_on), null) },
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
                    icon = { Icon(vectorResource(Res.drawable.local_airport), null) },
                    summary = { Text(uiState.networkInterfaceAddress.toString()) },
                )
            }

            when (uiState.action) {
                "", SingBoxOptions.ACTION_ROUTE -> {
                    item(KEY_ACTION_OPTIONS) {
                        PreferenceCategory(text = { Text(stringResource(Res.string.menu_route)) })
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

                                    else -> onSelectOutboundProfile(uiState.outbound)
                                }
                            },
                            values = listOf(
                                RuleEntity.OUTBOUND_PROXY,
                                RuleEntity.OUTBOUND_DIRECT,
                                RuleEntity.OUTBOUND_BLOCK,
                                -4L, // Custom
                            ),
                            title = { Text(stringResource(Res.string.outbound)) },
                            icon = { Icon(vectorResource(Res.drawable.router), null) },
                            summary = {
                                val text = when (uiState.outbound) {
                                    RuleEntity.OUTBOUND_PROXY -> stringResource(Res.string.route_proxy)
                                    RuleEntity.OUTBOUND_DIRECT -> stringResource(Res.string.route_bypass)
                                    RuleEntity.OUTBOUND_BLOCK -> stringResource(Res.string.route_block)
                                    else -> runBlocking { SagerDatabase.proxyDao.getById(uiState.outbound) }
                                        ?.displayName()
                                        ?: stringResource(Res.string.not_set)
                                }
                                Text(text)
                            },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = {
                                val id = when (it) {
                                    RuleEntity.OUTBOUND_PROXY -> Res.string.route_proxy
                                    RuleEntity.OUTBOUND_DIRECT -> Res.string.route_bypass
                                    RuleEntity.OUTBOUND_BLOCK -> Res.string.route_block
                                    else -> Res.string.select_profile
                                }
                                val text = runBlocking { repo.getString(id) }
                                AnnotatedString(text)
                            },
                        )
                    }
                }

                SingBoxOptions.ACTION_ROUTE_OPTIONS -> {
                    item(KEY_ACTION_OPTIONS) {
                        PreferenceCategory(text = { Text(stringResource(Res.string.route_options)) })
                    }
                    item("override_address") {
                        TextFieldPreference(
                            value = uiState.overrideAddress,
                            onValueChange = { viewModel.setOverrideAddress(it) },
                            title = { Text(stringResource(Res.string.override_address)) },
                            textToValue = { it },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.location_on),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(uiState.overrideAddress)) },
                            valueToText = { it },
                        )
                    }
                    item("override_port") {
                        TextFieldPreference(
                            value = uiState.overridePort,
                            onValueChange = { viewModel.setOverridePort(it) },
                            title = { Text(stringResource(Res.string.override_port)) },
                            textToValue = { it.toIntOrNull() ?: 0 },
                            icon = { Icon(vectorResource(Res.drawable.pin_drop), null) },
                            summary = { Text(contentOrUnset(uiState.overridePort)) },
                            textField = { value, onValueChange, onOk ->
                                UIntegerTextField(value, onValueChange, onOk)
                            },
                        )
                    }
                    item("tls_fragment") {
                        SwitchPreference(
                            value = uiState.tlsFragment,
                            onValueChange = { viewModel.setTlsFragment(it) },
                            title = { Text(stringResource(Res.string.tls_fragment)) },
                            icon = { Icon(vectorResource(Res.drawable.segment), null) },
                        )
                    }
                    item("tls_fragment_fallback_delay") {
                        TextFieldPreference(
                            value = uiState.tlsFragmentFallbackDelay,
                            onValueChange = { viewModel.setTlsFragmentFallbackDelay(it) },
                            title = { Text(stringResource(Res.string.tls_fragment_fallback_delay)) },
                            textToValue = { it },
                            enabled = uiState.tlsFragment,
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.hourglass_top),
                                    null,
                                )
                            },
                            summary = { Text(contentOrUnset(uiState.tlsFragmentFallbackDelay)) },
                            textField = { value, onValueChange, onOk ->
                                DurationTextField(value, onValueChange, onOk)
                            },
                        )
                    }
                    item("tls_record_fragment") {
                        SwitchPreference(
                            value = uiState.tlsRecordFragment,
                            onValueChange = { viewModel.setTlsRecordFragment(it) },
                            title = { Text(stringResource(Res.string.tls_record_fragment)) },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.fiber_smart_record),
                                    null,
                                )
                            },
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
                            icon = { Icon(vectorResource(Res.drawable.dns), null) },
                            summary = {
                                val text = uiState.resolveStrategy.blankAsNull()
                                    ?: stringResource(Res.string.auto)
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
                            icon = { Icon(vectorResource(Res.drawable.cached), null) },
                        )
                    }
                    item("rewrite_ttl") {
                        TextFieldPreference(
                            value = uiState.resolveRewriteTTL,
                            onValueChange = { viewModel.setResolveRewriteTTL(it) },
                            title = { Text("Rewrite TTL") },
                            textToValue = { it.toIntOrNull() ?: 0 },
                            icon = { Icon(vectorResource(Res.drawable.timer), null) },
                            summary = {
                                val text =
                                    uiState.resolveRewriteTTL.takeIf { it > 0 }?.toString()
                                        ?: stringResource(Res.string.not_set)
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
                            icon = { Icon(vectorResource(Res.drawable.push_pin), null) },
                            summary = { Text(contentOrUnset(uiState.resolveClientSubnet)) },
                            valueToText = { it },
                        )
                    }
                }

                SingBoxOptions.ACTION_SNIFF -> {
                    item(KEY_ACTION_OPTIONS) {
                        PreferenceCategory(text = { Text(stringResource(Res.string.sniff)) })
                    }
                    item("sniff_timeout") {
                        TextFieldPreference(
                            value = uiState.sniffTimeout,
                            onValueChange = { viewModel.setSniffTimeout(it) },
                            title = { Text(stringResource(Res.string.sniff_timeout)) },
                            textToValue = { it },
                            icon = { Icon(vectorResource(Res.drawable.timelapse), null) },
                            summary = { Text(contentOrUnset(uiState.sniffTimeout)) },
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
                            icon = { Icon(vectorResource(Res.drawable.layers), null) },
                            summary = {
                                val text = if (uiState.sniffers.isEmpty()) {
                                    stringResource(Res.string.not_set)
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
