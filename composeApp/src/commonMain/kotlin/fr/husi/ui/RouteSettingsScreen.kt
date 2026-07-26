@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.AutoCompleteTextField
import fr.husi.compose.BackHandler
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.CapsuleActionButton
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.DropdownMenuSectionHeader
import fr.husi.compose.DurationTextField
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MapPreference
import fr.husi.compose.MaskedIcon
import fr.husi.compose.MultilineTextField
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.SwipeableSnackbarHost
import fr.husi.compose.TextButton
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.fadingEdge
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.compose.withNavigation
import fr.husi.database.RuleEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.RuleItem
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.onIoDispatcher
import fr.husi.platform.PlatformInfo
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.action_direct
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
import fr.husi.resources.computer_cancel
import fr.husi.resources.copy_success
import fr.husi.resources.custom_config
import fr.husi.resources.delete
import fr.husi.resources.delete_confirm_prompt
import fr.husi.resources.directions_boat
import fr.husi.resources.dns
import fr.husi.resources.dns_only
import fr.husi.resources.domain
import fr.husi.resources.domino_mask
import fr.husi.resources.done
import fr.husi.resources.emoji_symbols
import fr.husi.resources.empty_route
import fr.husi.resources.empty_route_notice
import fr.husi.resources.fallback_outbound
import fr.husi.resources.fiber_smart_record
import fr.husi.resources.fingerprint
import fr.husi.resources.home
import fr.husi.resources.hourglass_top
import fr.husi.resources.layers
import fr.husi.resources.local_airport
import fr.husi.resources.local_bar
import fr.husi.resources.location_on
import fr.husi.resources.manage_search
import fr.husi.resources.menu_route
import fr.husi.resources.monetization_on
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.network_expensive
import fr.husi.resources.network_type
import fr.husi.resources.no
import fr.husi.resources.no_changes
import fr.husi.resources.no_changes_notice
import fr.husi.resources.not_set
import fr.husi.resources.ok
import fr.husi.resources.outbound
import fr.husi.resources.override_address
import fr.husi.resources.override_port
import fr.husi.resources.package_name_regex
import fr.husi.resources.pin_drop
import fr.husi.resources.public_icon
import fr.husi.resources.push_pin
import fr.husi.resources.question_mark
import fr.husi.resources.route_action
import fr.husi.resources.route_block
import fr.husi.resources.route_bridge
import fr.husi.resources.route_bypass
import fr.husi.resources.route_invert
import fr.husi.resources.route_name
import fr.husi.resources.route_options
import fr.husi.resources.route_proxy
import fr.husi.resources.rule_set_match
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
import fr.husi.resources.tls_spoof
import fr.husi.resources.tls_spoof_method
import fr.husi.resources.unsaved_changes_prompt
import fr.husi.resources.warning
import fr.husi.resources.warning_amber
import fr.husi.resources.wifi
import fr.husi.resources.wifi_find
import fr.husi.results.ResultEffect
import fr.husi.ui.jsoneditor.ConfigSchema
import fr.husi.ui.profile.tlsSpoofMethod
import fr.husi.ui.tools.RuleSetMatchDialog
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.MultiSelectListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import java.io.File
import kotlin.random.Random

private const val KEY_ACTION_OPTIONS = "action_options"
private const val CUSTOM_OUTBOUND_OPTION = -5L // Placeholder

// If too big, the performance is low and no one will check it patiently.
private const val RULE_SET_SUGGESTION_LIMIT = 64

@ExperimentalMaterial3Api
@Composable
internal fun RouteSettingsScreen(
    routeId: Long,
    initialState: RouteSettingsUiState?,
    onBackPress: () -> Unit,
    onSaved: () -> Unit,
    onOpenProfileSelect: OpenProfilePicker,
    onOpenAppList: (NavRoutes.AppList) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteSettingsViewModel = viewModel {
        RouteSettingsViewModel(routeId, initialState)
    },
) {
    val isDirty by viewModel.isDirty.collectAsState()
    var showBackAlert by remember { mutableStateOf(false) }
    BackHandler(enabled = isDirty) { showBackAlert = true }

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }

    val stringCopySuccess = stringResource(Res.string.copy_success)
    val stringOK = stringResource(Res.string.ok)

    var showExpandedMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEmptyRouteAlert by remember { mutableStateOf(false) }
    var showNoChangesAlert by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    fun saveAndExit() {
        viewModel.save()
        onSaved()
    }

    val resultKeyNumber = rememberSaveable { routeId.takeIf { it >= 0 } ?: Random.nextLong() }

    val appListResultKey = remember { "app-list-${resultKeyNumber}" }
    val configEditResultKey = remember { "route-config-${resultKeyNumber}" }
    val dnsConfigEditResultKey = remember { "route-dns-config-${resultKeyNumber}" }
    ResultEffect<Set<String>>(resultKey = appListResultKey) { result ->
        viewModel.setPackages(result)
    }
    ResultEffect<String?>(resultKey = configEditResultKey) { result ->
        if (result == null) return@ResultEffect
        viewModel.setCustomConfig(result)
    }
    ResultEffect<String?>(resultKey = dnsConfigEditResultKey) { result ->
        if (result == null) return@ResultEffect
        viewModel.setCustomDnsConfig(result)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
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
                title = { Text(stringResource(Res.string.menu_route)) },
                actions = {
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.delete),
                            contentDescription = stringResource(Res.string.delete),
                            onClick = { showDeleteConfirm = true },
                        )
                    }
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.done),
                            contentDescription = stringResource(Res.string.apply),
                        ) {
                            if (uiState.needsRules()) {
                                showEmptyRouteAlert = true
                            } else if (isDirty) {
                                saveAndExit()
                            } else {
                                showNoChangesAlert = true
                            }
                        }
                    }

                    Box {
                        CapsuleActionButton {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.more_vert),
                                contentDescription = stringResource(Res.string.more),
                            ) {
                                showExpandedMenu = true
                            }
                        }
                        DropdownMenuPopup(
                            expanded = showExpandedMenu,
                            onDismissRequest = { showExpandedMenu = false },
                        ) {
                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShape(0, 3),
                            ) {
                                DropdownMenuItem(
                                    checked = uiState.invert,
                                    onCheckedChange = viewModel::setInvert,
                                    text = { Text(stringResource(Res.string.route_invert)) },
                                    shapes = MenuDefaults.itemShapes(),
                                )
                            }
                            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShape(1, 3),
                            ) {
                                DropdownMenuSectionHeader(stringResource(Res.string.custom_config))
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.menu_route)) },
                                    onClick = {
                                        showExpandedMenu = false
                                        onOpenConfigEditor(
                                            NavRoutes.ConfigEditor(
                                                initialText = uiState.customConfig,
                                                resultKey = configEditResultKey,
                                            ),
                                        )
                                    },
                                    shape = MenuDefaults.itemShape(0, 2).shape,
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.cag_dns)) },
                                    onClick = {
                                        showExpandedMenu = false
                                        onOpenConfigEditor(
                                            NavRoutes.ConfigEditor(
                                                initialText = uiState.customDnsConfig,
                                                resultKey = dnsConfigEditResultKey,
                                                schema = ConfigSchema.DNS_RULE,
                                            ),
                                        )
                                    },
                                    shape = MenuDefaults.itemShape(1, 2).shape,
                                )
                            }
                            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShape(2, 3),
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
        snackbarHost = { SwipeableSnackbarHost(snackbarState) },
    ) { innerPadding ->
        ProvidePreferenceLocals {
            RouteSettings(
                paddings = innerPadding,
                uiState = uiState,
                viewModel = viewModel,
                onSelectOutboundProfile = { selected ->
                    onOpenProfileSelect(selected.takeIf { it > 0 }) { id ->
                        viewModel.setOutbound(id)
                    }
                },
                onSelectApps = { packages ->
                    if (PlatformInfo.isAndroid) {
                        onOpenAppList(
                            NavRoutes.AppList(
                                initialPackages = packages,
                                resultKey = appListResultKey,
                            ),
                        )
                    } else {
                        viewModel.setPackages(packages)
                    }
                },
                onRuleSetCopy = {
                    scope.launch {
                        snackbarState.showSnackbar(
                            message = stringCopySuccess,
                            actionLabel = stringOK,
                            duration = SnackbarDuration.Short,
                        )
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
    if (showEmptyRouteAlert) {
        AlertDialog(
            onDismissRequest = { showEmptyRouteAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    showEmptyRouteAlert = false
                }
            },
            icon = { Icon(vectorResource(Res.drawable.warning_amber), null) },
            title = { Text(stringResource(Res.string.empty_route)) },
            text = { Text(stringResource(Res.string.empty_route_notice)) },
        )
    }
    if (showNoChangesAlert) {
        AlertDialog(
            onDismissRequest = { showNoChangesAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    showNoChangesAlert = false
                }
            },
            icon = { Icon(vectorResource(Res.drawable.warning_amber), null) },
            title = { Text(stringResource(Res.string.no_changes)) },
            text = { Text(stringResource(Res.string.no_changes_notice)) },
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
    onRuleSetCopy: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val geoDir = remember(resolveRepository().externalAssetsDir) {
        resolveRepository().externalAssetsDir.resolve("geo").takeIf { it.isDirectory }
    }

    val listState = rememberLazyListState()
    val contentPadding = paddings.withNavigation()
    Row(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .fadingEdge(
                    scrollableState = listState,
                    fadeStart = true,
                    fadeEnd = true,
                ),
            contentPadding = contentPadding,
        ) {
            val outbounds = buildList {
                add(RuleEntity.OUTBOUND_PROXY)
                add(RuleEntity.OUTBOUND_DIRECT)
                add(RuleEntity.OUTBOUND_BLOCK)
                if (!PlatformInfo.isAndroid) {
                    add(RuleEntity.OUTBOUND_BRIDGE)
                }
                add(CUSTOM_OUTBOUND_OPTION)
            }

            preferenceGroup(key = "basic") {
                TextFieldPreference(
                    value = uiState.name,
                    onValueChange = { viewModel.setName(it) },
                    title = { Text(stringResource(Res.string.route_name)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.emoji_symbols,
                            color = IconMaskColors.IconLightYellow,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.name)) },
                    valueToText = { it },
                )
                PreferenceDivider()
                AppSelectPreference(uiState.packages, onSelectApps)
                if (PlatformInfo.isAndroid) {
                    PreferenceDivider()
                    TextFieldPreference(
                        value = uiState.packageNameRegex,
                        onValueChange = { viewModel.setPackageNameRegex(it) },
                        title = { Text(stringResource(Res.string.package_name_regex)) },
                        textToValue = { it },
                        icon = {
                            MaskedIcon(
                                resource = Res.drawable.fiber_smart_record,
                                color = IconMaskColors.IconCyan,
                            )
                        },
                        summary = { Text(contentOrUnset(uiState.packageNameRegex)) },
                        valueToText = { it },
                    )
                }
                PreferenceDivider()
                MultiSelectListPreference(
                    value = uiState.networkType,
                    onValueChange = { viewModel.setNetworkType(it) },
                    values = networkTypes,
                    title = { Text(stringResource(Res.string.network_type)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.public_icon,
                            color = IconMaskColors.IconLightBlue,
                        )
                    },
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
                PreferenceDivider()
                ListPreference(
                    value = uiState.action,
                    onValueChange = { viewModel.setAction(it) },
                    values = buildList {
                        add(SingBoxOptions.ACTION_ROUTE)
                        if (PlatformInfo.isLinux) {
                            add(SingBoxOptions.ACTION_BYPASS)
                        }
                        add(SingBoxOptions.ACTION_ROUTE_OPTIONS)
                        add(SingBoxOptions.ACTION_SNIFF)
                        add(SingBoxOptions.ACTION_RESOLVE)
                        add(SingBoxOptions.ACTION_HIJACK_DNS)
                        add(SingBoxOptions.ACTION_REJECT)
                    },
                    title = { Text(stringResource(Res.string.route_action)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.shuffle,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.action)) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(it) },
                )
            }

            item("category_settings") {
                PreferenceCategory(text = { Text(stringResource(Res.string.settings)) })
            }
            preferenceGroup {
                TextFieldPreference(
                    value = uiState.domains,
                    onValueChange = { viewModel.setDomains(it) },
                    title = { Text("domain") },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.domain,
                            color = IconMaskColors.IconCyan,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.domains)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        var showRuleSetMatchDialog by rememberSaveable {
                            mutableStateOf(false)
                        }
                        Column {
                            RuleSetAutoCompleteTextField(
                                value = value,
                                onValueChange = onValueChange,
                                onOk = onOk,
                                geoDir = geoDir,
                            )
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.manage_search),
                                contentDescription = stringResource(Res.string.rule_set_match),
                                onClick = { showRuleSetMatchDialog = true },
                            )
                        }
                        if (showRuleSetMatchDialog) {
                            RuleSetMatchDialog(
                                onDismissRequest = { showRuleSetMatchDialog = false },
                                onCopy = onRuleSetCopy,
                            )
                        }
                    },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = uiState.ip,
                    onValueChange = { viewModel.setIp(it) },
                    title = { Text("ip") },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.add_road,
                            color = IconMaskColors.IconLightBlue,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.ip)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        RuleSetAutoCompleteTextField(
                            value = value,
                            onValueChange = onValueChange,
                            onOk = onOk,
                            geoDir = geoDir,
                        )
                    },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = uiState.port,
                    onValueChange = { viewModel.setPort(it) },
                    title = { Text("port") },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.directions_boat,
                            color = IconMaskColors.IconLightOrange,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.port)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                TextFieldPreference(
                    value = uiState.sourcePort,
                    onValueChange = { viewModel.setSourcePort(it) },
                    title = { Text("sourcePort") },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.home,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.sourcePort)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                MultiSelectListPreference(
                    value = uiState.network,
                    onValueChange = { viewModel.setNetwork(it) },
                    values = listOf(
                        SingBoxOptions.NetworkTCP,
                        SingBoxOptions.NetworkUDP,
                        SingBoxOptions.NetworkICMP,
                    ),
                    title = { Text("network") },
                    icon = {
                        MaskedIcon(
                            Res.drawable.compare_arrows,
                            color = IconMaskColors.IconLightGreen,
                        )
                    },
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
                PreferenceDivider()
                TextFieldPreference(
                    value = uiState.source,
                    onValueChange = { viewModel.setSource(it) },
                    title = { Text("source") },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.local_bar,
                            color = IconMaskColors.IconCoral,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.source)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                MultiSelectListPreference(
                    value = uiState.protocol,
                    onValueChange = { viewModel.setProtocol(it) },
                    values = sniffers,
                    title = { Text("protocol") },
                    icon = {
                        MaskedIcon(
                            Res.drawable.layers,
                            color = IconMaskColors.IconWarmGray,
                        )
                    },
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
                PreferenceDivider()
                TextFieldPreference(
                    value = uiState.client,
                    onValueChange = { viewModel.setClient(it) },
                    title = { Text("client") },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.fingerprint,
                            color = IconMaskColors.IconLightPink,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.client)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                val showWifi = uiState.networkType.contains(SingBoxOptions.NETWORK_TYPE_WIFI)
                if (showWifi) {
                    PreferenceDivider()
                    TextFieldPreference(
                        value = uiState.ssid,
                        onValueChange = { viewModel.setSsid(it) },
                        title = { Text("SSID") },
                        textToValue = { it },
                        icon = {
                            MaskedIcon(
                                Res.drawable.wifi,
                                color = IconMaskColors.IconLightBlue,
                            )
                        },
                        summary = { Text(contentOrUnset(uiState.ssid)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                    PreferenceDivider()
                    TextFieldPreference(
                        value = uiState.bssid,
                        onValueChange = { viewModel.setBssid(it) },
                        title = { Text("BSSID") },
                        textToValue = { it },
                        icon = {
                            MaskedIcon(
                                Res.drawable.wifi_find,
                                color = IconMaskColors.IconCyan,
                            )
                        },
                        summary = { Text(contentOrUnset(uiState.bssid)) },
                        valueToText = { it },
                        textField = { value, onValueChange, onOk ->
                            MultilineTextField(value, onValueChange, onOk)
                        },
                    )
                }
                PreferenceDivider()
                TextFieldPreference(
                    value = uiState.clashMode,
                    onValueChange = { viewModel.setClashMode(it) },
                    title = { Text(stringResource(Res.string.clash_mode)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.category,
                            color = IconMaskColors.IconLightYellow,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.clashMode)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        MultilineTextField(value, onValueChange, onOk)
                    },
                )
                PreferenceDivider()
                SwitchPreference(
                    value = uiState.networkIsExpensive,
                    onValueChange = { viewModel.setNetworkIsExpensive(it) },
                    title = { Text(stringResource(Res.string.network_expensive)) },
                    icon = {
                        MaskedIcon(
                            Res.drawable.monetization_on,
                            color = IconMaskColors.IconCoral,
                        )
                    },
                )
                PreferenceDivider()
                MapPreference(
                    value = uiState.networkInterfaceAddress,
                    keys = LinkedHashSet(networkTypes),
                    onValueChange = { viewModel.setNetworkInterfaceAddress(it) },
                    displayKey = { it },
                    valueToText = { it },
                    textToValue = { it },
                    title = { Text("networkInterfaceAddress") },
                    icon = {
                        MaskedIcon(
                            Res.drawable.local_airport,
                            color = IconMaskColors.IconLightGreen,
                        )
                    },
                    summary = { Text(uiState.networkInterfaceAddress.toString()) },
                )
            }

            when (uiState.action) {
                "", SingBoxOptions.ACTION_ROUTE -> {
                    item(KEY_ACTION_OPTIONS) {
                        PreferenceCategory(text = { Text(stringResource(Res.string.menu_route)) })
                    }
                    preferenceGroup {
                        ListPreference(
                            value = uiState.outbound,
                            onValueChange = {
                                when (it) {
                                    RuleEntity.OUTBOUND_PROXY,
                                    RuleEntity.OUTBOUND_DIRECT,
                                    RuleEntity.OUTBOUND_BLOCK,
                                    RuleEntity.OUTBOUND_BRIDGE,
                                        -> viewModel.setOutbound(it)

                                    else -> onSelectOutboundProfile(uiState.outbound)
                                }
                            },
                            values = outbounds,
                            title = { Text(stringResource(Res.string.outbound)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.router,
                                    color = IconMaskColors.IconLightBlue,
                                )
                            },
                            summary = {
                                val text = when (uiState.outbound) {
                                    RuleEntity.OUTBOUND_PROXY -> stringResource(Res.string.route_proxy)
                                    RuleEntity.OUTBOUND_DIRECT -> stringResource(Res.string.route_bypass)
                                    RuleEntity.OUTBOUND_BLOCK -> stringResource(Res.string.route_block)
                                    RuleEntity.OUTBOUND_BRIDGE -> stringResource(Res.string.route_bridge)
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
                                    RuleEntity.OUTBOUND_BRIDGE -> Res.string.route_bridge
                                    else -> Res.string.select_profile
                                }
                                AnnotatedString(stringResource(id))
                            },
                        )
                    }
                }

                SingBoxOptions.ACTION_BYPASS -> {
                    item(KEY_ACTION_OPTIONS) {
                        PreferenceCategory(text = { Text(SingBoxOptions.ACTION_BYPASS) })
                    }
                    preferenceGroup {
                        ListPreference(
                            value = uiState.outbound,
                            onValueChange = {
                                when (it) {
                                    RuleEntity.OUTBOUND_PROXY,
                                    RuleEntity.OUTBOUND_DIRECT,
                                    RuleEntity.OUTBOUND_BLOCK,
                                    RuleEntity.OUTBOUND_BRIDGE,
                                        -> viewModel.setOutbound(it)

                                    else -> onSelectOutboundProfile(uiState.outbound)
                                }
                            },
                            values = outbounds,
                            title = { Text(stringResource(Res.string.fallback_outbound)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.router,
                                    color = IconMaskColors.IconLightBlue,
                                )
                            },
                            summary = {
                                val text = when (uiState.outbound) {
                                    RuleEntity.OUTBOUND_PROXY -> stringResource(Res.string.route_proxy)
                                    RuleEntity.OUTBOUND_DIRECT -> stringResource(Res.string.action_direct)
                                    RuleEntity.OUTBOUND_BLOCK -> stringResource(Res.string.route_block)
                                    RuleEntity.OUTBOUND_BRIDGE -> stringResource(Res.string.route_bridge)
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
                                    RuleEntity.OUTBOUND_DIRECT -> Res.string.action_direct
                                    RuleEntity.OUTBOUND_BLOCK -> Res.string.route_block
                                    RuleEntity.OUTBOUND_BRIDGE -> Res.string.route_bridge
                                    else -> Res.string.select_profile
                                }
                                AnnotatedString(stringResource(id))
                            },
                        )
                    }
                }

                SingBoxOptions.ACTION_ROUTE_OPTIONS -> {
                    item(KEY_ACTION_OPTIONS) {
                        PreferenceCategory(text = { Text(stringResource(Res.string.route_options)) })
                    }
                    preferenceGroup {
                        TextFieldPreference(
                            value = uiState.overrideAddress,
                            onValueChange = { viewModel.setOverrideAddress(it) },
                            title = { Text(stringResource(Res.string.override_address)) },
                            textToValue = { it },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.location_on,
                                    color = IconMaskColors.IconCoral,
                                )
                            },
                            summary = { Text(contentOrUnset(uiState.overrideAddress)) },
                            valueToText = { it },
                        )
                        PreferenceDivider()
                        TextFieldPreference(
                            value = uiState.overridePort,
                            onValueChange = { viewModel.setOverridePort(it) },
                            title = { Text(stringResource(Res.string.override_port)) },
                            textToValue = { it.toIntOrNull() ?: 0 },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.pin_drop,
                                    color = IconMaskColors.IconLightOrange,
                                )
                            },
                            summary = { Text(contentOrUnset(uiState.overridePort)) },
                            textField = { value, onValueChange, onOk ->
                                UIntegerTextField(value, onValueChange, onOk)
                            },
                        )
                        PreferenceDivider()
                        SwitchPreference(
                            value = uiState.tlsFragment,
                            onValueChange = { viewModel.setTlsFragment(it) },
                            title = { Text(stringResource(Res.string.tls_fragment)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.segment,
                                    color = IconMaskColors.IconLightYellow,
                                )
                            },
                        )
                        PreferenceDivider()
                        TextFieldPreference(
                            value = uiState.tlsFragmentFallbackDelay,
                            onValueChange = { viewModel.setTlsFragmentFallbackDelay(it) },
                            title = { Text(stringResource(Res.string.tls_fragment_fallback_delay)) },
                            textToValue = { it },
                            enabled = uiState.tlsFragment,
                            icon = {
                                MaskedIcon(
                                    Res.drawable.hourglass_top,
                                    color = IconMaskColors.IconLightBlue,
                                )
                            },
                            summary = { Text(contentOrUnset(uiState.tlsFragmentFallbackDelay)) },
                            textField = { value, onValueChange, onOk ->
                                DurationTextField(value, onValueChange, onOk)
                            },
                        )
                        PreferenceDivider()
                        SwitchPreference(
                            value = uiState.tlsRecordFragment,
                            onValueChange = { viewModel.setTlsRecordFragment(it) },
                            title = { Text(stringResource(Res.string.tls_record_fragment)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.fiber_smart_record,
                                    color = IconMaskColors.IconLavender,
                                )
                            },
                        )
                        if (!PlatformInfo.isAndroid) {
                            PreferenceDivider()
                            TextFieldPreference(
                                value = uiState.tlsSpoof,
                                onValueChange = { viewModel.setTlsSpoof(it) },
                                title = { Text(stringResource(Res.string.tls_spoof)) },
                                textToValue = { it },
                                icon = {
                                    MaskedIcon(
                                        Res.drawable.domino_mask,
                                        color = IconMaskColors.IconWarmGray,
                                    )
                                },
                                summary = { Text(contentOrUnset(uiState.tlsSpoof)) },
                                valueToText = { it },
                            )
                            PreferenceDivider()
                            ListPreference(
                                value = uiState.tlsSpoofMethod,
                                values = tlsSpoofMethod,
                                onValueChange = { viewModel.setTlsSpoofMethod(it) },
                                title = { Text(stringResource(Res.string.tls_spoof_method)) },
                                enabled = uiState.tlsSpoof.isNotBlank(),
                                icon = {
                                    MaskedIcon(
                                        Res.drawable.computer_cancel,
                                        color = IconMaskColors.IconCyan,
                                    )
                                },
                                summary = { Text(contentOrUnset(uiState.tlsSpoofMethod)) },
                                type = ListPreferenceType.DROPDOWN_MENU,
                                valueToText = { AnnotatedString(it) },
                            )
                        }
                    }
                }

                SingBoxOptions.ACTION_RESOLVE -> {
                    item(KEY_ACTION_OPTIONS) {
                        PreferenceCategory(text = { Text("Resolve") })
                    }
                    preferenceGroup {
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
                            icon = {
                                MaskedIcon(
                                    Res.drawable.dns,
                                    color = IconMaskColors.IconLightBlue,
                                )
                            },
                            summary = {
                                val text = uiState.resolveStrategy.blankAsNull()
                                    ?: stringResource(Res.string.auto)
                                Text(text)
                            },
                            type = ListPreferenceType.DROPDOWN_MENU,
                            valueToText = { AnnotatedString(it) },
                        )
                        PreferenceDivider()
                        SwitchPreference(
                            value = uiState.resolveDisableCache,
                            onValueChange = { viewModel.setResolveDisableCache(it) },
                            title = { Text("Disable Cache") },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.cached,
                                    color = IconMaskColors.IconWarmGray,
                                )
                            },
                        )
                        PreferenceDivider()
                        TextFieldPreference(
                            value = uiState.resolveRewriteTTL,
                            onValueChange = { viewModel.setResolveRewriteTTL(it) },
                            title = { Text("Rewrite TTL") },
                            textToValue = { it.toIntOrNull() ?: 0 },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.timer,
                                    color = IconMaskColors.IconLightOrange,
                                )
                            },
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
                        PreferenceDivider()
                        TextFieldPreference(
                            value = uiState.resolveClientSubnet,
                            onValueChange = { viewModel.setResolveClientSubnet(it) },
                            title = { Text("Client Subnet") },
                            textToValue = { it },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.push_pin,
                                    color = IconMaskColors.IconCoral,
                                )
                            },
                            summary = { Text(contentOrUnset(uiState.resolveClientSubnet)) },
                            valueToText = { it },
                        )
                    }
                }

                SingBoxOptions.ACTION_SNIFF -> {
                    item(KEY_ACTION_OPTIONS) {
                        PreferenceCategory(text = { Text(stringResource(Res.string.sniff)) })
                    }
                    preferenceGroup {
                        TextFieldPreference(
                            value = uiState.sniffTimeout,
                            onValueChange = { viewModel.setSniffTimeout(it) },
                            title = { Text(stringResource(Res.string.sniff_timeout)) },
                            textToValue = { it },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.timelapse,
                                    color = IconMaskColors.IconLightBlue,
                                )
                            },
                            summary = { Text(contentOrUnset(uiState.sniffTimeout)) },
                            textField = { value, onValueChange, onOk ->
                                DurationTextField(value, onValueChange, onOk)
                            },
                        )
                        PreferenceDivider()
                        MultiSelectListPreference(
                            value = uiState.sniffers,
                            onValueChange = { viewModel.setSniffers(it) },
                            values = sniffers,
                            title = { Text("Sniffers") },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.layers,
                                    color = IconMaskColors.IconLavender,
                                )
                            },
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

internal data class SelectedLine(
    val start: Int,
    val end: Int,
    val cursor: Int,
    val raw: String,
) {
    val text = raw.removeSuffix("\r")
    val suffix = if (raw.endsWith('\r')) "\r" else ""
}

internal fun TextFieldValue.selectedLine(): SelectedLine? {
    if (!selection.collapsed) return null

    val cursor = selection.end.fastCoerceIn(0, annotatedString.length)
    val text = annotatedString.text
    val end = text.indexOf('\n', cursor).let {
        if (it >= 0) {
            it
        } else {
            text.length
        }
    }
    val start = if (cursor == 0) {
        0
    } else {
        text.lastIndexOf('\n', cursor - 1).let {
            if (it >= 0) {
                it + 1
            } else {
                0
            }
        }
    }
    return SelectedLine(
        start = start,
        end = end,
        cursor = cursor,
        raw = text.substring(start, end),
    )
}

@Composable
private fun RuleSetAutoCompleteTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onOk: () -> Unit,
    geoDir: File?,
    modifier: Modifier = Modifier,
) {
    var ruleSets by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(geoDir) {
        ruleSets = onIoDispatcher {
            geoDir?.listFiles()
                ?.filter { it.isFile && it.extension == "srs" }
                ?.map { it.nameWithoutExtension }
                ?.sorted()
                .orEmpty()
        }
    }

    val suggestions = remember(value, ruleSets) {
        val prefix = value.selectedLine()?.ruleSetSuggestionPrefix() ?: return@remember emptyList()
        ruleSets.findRuleSetSuggestions(prefix, RULE_SET_SUGGESTION_LIMIT)
    }

    AutoCompleteTextField(
        value = value,
        onValueChange = onValueChange,
        onOk = onOk,
        suggestions = suggestions,
        onChooseSuggestion = { suggestion ->
            val line = value.selectedLine() ?: return@AutoCompleteTextField
            val linePrefix = line.raw.substringBefore(':', missingDelimiterValue = "")
            if (linePrefix.isBlank()) return@AutoCompleteTextField
            val replacement = "$linePrefix:$suggestion${line.suffix}"
            onValueChange(
                value.copy(
                    annotatedString = AnnotatedString(
                        value.annotatedString.text.replaceRange(line.start, line.end, replacement),
                    ),
                    selection = TextRange(line.start + replacement.length),
                    composition = null,
                ),
            )
        },
        modifier = modifier,
        displaySuggestion = { it },
    )
}

// Implement a mini parser to improve performance.
internal fun SelectedLine.ruleSetSuggestionPrefix(): String? {
    if (cursor != end) return null

    val delimiterIndex = text.indexOf(':')
    if (delimiterIndex <= 0) return null

    val rawType = text.substring(0, delimiterIndex)
    val type = when {
        rawType.endsWith(RuleItem.TYPE_FLAG_PLUS_DNS) -> {
            rawType.removeSuffix(RuleItem.TYPE_FLAG_PLUS_DNS)
        }

        rawType.endsWith(RuleItem.TYPE_FLAG_MINUS_DNS) -> {
            rawType.removeSuffix(RuleItem.TYPE_FLAG_MINUS_DNS)
        }

        else -> rawType
    }
    if (type != RuleItem.TYPE_FLAG_RULE_SET) return null

    return text.substring(delimiterIndex + 1)
}

internal fun List<String>.findRuleSetSuggestions(prefix: String, limit: Int): List<String> {
    if (isEmpty() || limit <= 0) return emptyList()

    val startIndex = lowerBound(prefix)
    if (startIndex == size || !this[startIndex].startsWith(prefix)) return emptyList()

    val suggestions = ArrayList<String>(limit.coerceAtMost(size - startIndex))
    for (index in startIndex until size) {
        val candidate = this[index]
        if (!candidate.startsWith(prefix)) break

        suggestions += candidate
        if (suggestions.size >= limit) break
    }
    return suggestions
}

private fun List<String>.lowerBound(prefix: String): Int {
    var low = 0
    var high = size
    while (low < high) {
        val mid = (low + high).ushr(1)
        if (this[mid] < prefix) {
            low = mid + 1
        } else {
            high = mid
        }
    }
    return low
}
