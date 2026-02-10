package fr.husi.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.GroupOrder
import fr.husi.GroupType
import fr.husi.SubscriptionType
import fr.husi.compose.BackHandler
import fr.husi.compose.LinkOrContentTextField
import fr.husi.compose.MoreOverIcon
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceType
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.withNavigation
import fr.husi.database.ProfileManager
import fr.husi.database.SagerDatabase
import fr.husi.ktx.USER_AGENT
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.repository.repo
import fr.husi.resources.Res
import fr.husi.resources.apply
import fr.husi.resources.auto_update
import fr.husi.resources.auto_update_delay
import fr.husi.resources.close
import fr.husi.resources.deduplication
import fr.husi.resources.deduplication_sum
import fr.husi.resources.delete
import fr.husi.resources.delete_group_prompt
import fr.husi.resources.delete_sweep
import fr.husi.resources.done
import fr.husi.resources.emoji_symbols
import fr.husi.resources.filter_regex
import fr.husi.resources.flip_camera_android
import fr.husi.resources.force_resolve
import fr.husi.resources.force_resolve_sum
import fr.husi.resources.front_proxy
import fr.husi.resources.grid_3x3
import fr.husi.resources.group_basic
import fr.husi.resources.group_name
import fr.husi.resources.group_order
import fr.husi.resources.group_order_by_delay
import fr.husi.resources.group_order_by_name
import fr.husi.resources.group_order_origin
import fr.husi.resources.group_settings
import fr.husi.resources.group_subscription_link
import fr.husi.resources.group_type
import fr.husi.resources.import_contacts
import fr.husi.resources.landing_proxy
import fr.husi.resources.layers
import fr.husi.resources.link
import fr.husi.resources.low_priority
import fr.husi.resources.manage_search
import fr.husi.resources.nfc
import fr.husi.resources.no
import fr.husi.resources.no_thanks
import fr.husi.resources.not_set
import fr.husi.resources.ok
import fr.husi.resources.ooc_subscription_token
import fr.husi.resources.oocv1
import fr.husi.resources.proxy_chain
import fr.husi.resources.public_icon
import fr.husi.resources.question_mark
import fr.husi.resources.raw
import fr.husi.resources.route_profile
import fr.husi.resources.security
import fr.husi.resources.sip008
import fr.husi.resources.ssh_auth_type_none
import fr.husi.resources.subscription
import fr.husi.resources.subscription_settings
import fr.husi.resources.subscription_type
import fr.husi.resources.subscription_user_agent
import fr.husi.resources.unsaved_changes_prompt
import fr.husi.resources.update_settings
import fr.husi.resources.update_when_connected_only
import fr.husi.resources.update_when_connected_only_sum
import fr.husi.resources.vpn_key
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun GroupSettingsScreen(
    groupId: Long,
    onBackPress: () -> Unit,
    onOpenProfileSelect: (preSelected: Long?, onSelected: (Long) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupSettingsViewModel = viewModel { GroupSettingsViewModel() },
) {
    LaunchedEffect(viewModel, groupId) {
        viewModel.initialize(groupId)
    }

    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    var showBackAlert by remember { mutableStateOf(false) }
    BackHandler(enabled = isDirty) {
        showBackAlert = true
    }
    var showDeleteAlert by remember { mutableStateOf(false) }

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val uiState by viewModel.uiState.collectAsState()

    fun saveAndExit() {
        viewModel.save()
        onBackPress()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.group_settings)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                    ) {
                        onBackPress()
                    }
                },
                actions = {
                    AppBarRow(
                        overflowIndicator = ::MoreOverIcon,
                    ) {
                        clickableItem(
                            onClick = {
                                if (viewModel.isNew) {
                                    onBackPress()
                                } else {
                                    showDeleteAlert = true
                                }
                            },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.delete),
                                    null,
                                )
                            },
                            label = runBlocking { repo.getString(Res.string.delete) },
                        )
                        clickableItem(
                            onClick = ::saveAndExit,
                            icon = {
                                Icon(vectorResource(Res.drawable.done), null)
                            },
                            label = runBlocking { repo.getString(Res.string.apply) },
                        )
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        ProvidePreferenceLocals {
            LazyColumn(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding.withNavigation(),
            ) {
                groupSettings(
                    uiState = uiState,
                    viewModel = viewModel,
                    selectFrontProxy = {
                        onOpenProfileSelect(uiState.frontProxy.takeIf { it > 0 }) { id ->
                            val profile =
                                ProfileManager.getProfile(id) ?: return@onOpenProfileSelect
                            viewModel.setFrontProxy(profile.id)
                        }
                    },
                    selectLandingProxy = {
                        onOpenProfileSelect(uiState.landingProxy.takeIf { it > 0 }) { id ->
                            val profile =
                                ProfileManager.getProfile(id) ?: return@onOpenProfileSelect
                            viewModel.setLandingProxy(profile.id)
                        }
                    },
                )
            }
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
    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    viewModel.delete()
                    onBackPress()
                    showDeleteAlert = false
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.no_thanks)) {
                    showDeleteAlert = false
                }
            },
            icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
            title = { Text(stringResource(Res.string.delete_group_prompt)) },
        )
    }
}

private fun LazyListScope.groupSettings(
    uiState: GroupSettingsUiState,
    viewModel: GroupSettingsViewModel,
    selectFrontProxy: () -> Unit,
    selectLandingProxy: () -> Unit,
) {
    item("name", PreferenceType.TEXT_FIELD) {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.group_name)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.emoji_symbols), null) },
            summary = { Text(contentOrUnset(uiState.name)) },
            valueToText = { it },
        )
    }

    fun groupType(type: Int) = when (type) {
        GroupType.BASIC -> Res.string.group_basic
        GroupType.SUBSCRIPTION -> Res.string.subscription
        else -> error("impossible")
    }
    item("type", PreferenceType.LIST) {
        ListPreference(
            value = uiState.type,
            onValueChange = { viewModel.setType(it) },
            values = intListN(2),
            title = { Text(stringResource(Res.string.group_type)) },
            icon = { Icon(vectorResource(Res.drawable.layers), null) },
            summary = { Text(stringResource(groupType(uiState.type))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                val text = runBlocking { repo.getString(groupType(it)) }
                AnnotatedString(text)
            },
        )
    }

    fun groupOrder(order: Int) = when (order) {
        GroupOrder.ORIGIN -> Res.string.group_order_origin
        GroupOrder.BY_NAME -> Res.string.group_order_by_name
        GroupOrder.BY_DELAY -> Res.string.group_order_by_delay
        else -> error("impossible")
    }
    item("order", PreferenceType.LIST) {
        ListPreference(
            value = uiState.order,
            onValueChange = { viewModel.setOrder(it) },
            values = intListN(3),
            title = { Text(stringResource(Res.string.group_order)) },
            icon = { Icon(vectorResource(Res.drawable.low_priority), null) },
            summary = { Text(stringResource(groupOrder(uiState.order))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                val text = runBlocking { repo.getString(groupOrder(it)) }
                AnnotatedString(text)
            },
        )
    }

    item("category_chain", PreferenceType.CATEGORY) {
        PreferenceCategory(text = { Text(stringResource(Res.string.proxy_chain)) })
    }
    fun chainName(id: Long) = runBlocking { SagerDatabase.proxyDao.getById(id) }?.displayName()
    item("font", PreferenceType.LIST) {
        ListPreference(
            value = uiState.frontProxy,
            onValueChange = {
                if (it == -1L) {
                    viewModel.setFrontProxy(it)
                } else {
                    selectFrontProxy()
                }
            },
            values = listOf(-1L, 0L),
            title = { Text(stringResource(Res.string.front_proxy)) },
            icon = { Icon(vectorResource(Res.drawable.low_priority), null) },
            summary = {
                val text = chainName(uiState.frontProxy)
                    ?: stringResource(Res.string.not_set)
                Text(text)
            },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                val id = if (it == -1L) {
                    Res.string.ssh_auth_type_none
                } else {
                    Res.string.route_profile
                }
                val text = runBlocking { repo.getString(id) }
                AnnotatedString(text)
            },
        )
    }
    item("landing", PreferenceType.LIST) {
        ListPreference(
            value = uiState.landingProxy,
            onValueChange = {
                if (it == -1L) {
                    viewModel.setLandingProxy(it)
                } else {
                    selectLandingProxy()
                }
            },
            values = listOf(-1L, 0L),
            title = { Text(stringResource(Res.string.landing_proxy)) },
            icon = { Icon(vectorResource(Res.drawable.public_icon), null) },
            summary = {
                val text = chainName(uiState.landingProxy)
                    ?: stringResource(Res.string.not_set)
                Text(text)
            },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                val id = if (it == -1L) {
                    Res.string.ssh_auth_type_none
                } else {
                    Res.string.route_profile
                }
                val text = runBlocking { repo.getString(id) }
                AnnotatedString(text)
            },
        )
    }

    if (uiState.type == GroupType.SUBSCRIPTION) {
        item("category_subscription", PreferenceType.CATEGORY) {
            PreferenceCategory(text = { Text(stringResource(Res.string.subscription_settings)) })
        }
        fun subType(type: Int) = when (type) {
            SubscriptionType.RAW -> Res.string.raw
            SubscriptionType.OOCv1 -> Res.string.oocv1
            SubscriptionType.SIP008 -> Res.string.sip008
            else -> error("impossible")
        }
        item("subscription_type", PreferenceType.LIST) {
            ListPreference(
                value = uiState.subscriptionType,
                onValueChange = { viewModel.setSubscriptionType(it) },
                values = intListN(3),
                title = { Text(stringResource(Res.string.subscription_type)) },
                icon = { Icon(vectorResource(Res.drawable.nfc), null) },
                summary = { Text(stringResource(subType(uiState.subscriptionType))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = {
                    val text = runBlocking { repo.getString(subType(it)) }
                    AnnotatedString(text)
                },
            )
        }

        item("subscription_link", PreferenceType.TEXT_FIELD) {
            TextFieldPreference(
                value = uiState.subscriptionLink,
                onValueChange = { viewModel.setSubscriptionLink(it) },
                title = { Text(stringResource(Res.string.group_subscription_link)) },
                textToValue = { it },
                icon = { Icon(vectorResource(Res.drawable.link), null) },
                summary = { Text(contentOrUnset(uiState.subscriptionLink)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    LinkOrContentTextField(value, onValueChange, onOk)
                },
            )
        }
        val isOOCv1 = uiState.subscriptionType == SubscriptionType.OOCv1
        if (isOOCv1) {
            item("subscription_token", PreferenceType.TEXT_FIELD) {
                TextFieldPreference(
                    value = uiState.subscriptionToken,
                    onValueChange = { viewModel.setSubscriptionToken(it) },
                    title = { Text(stringResource(Res.string.ooc_subscription_token)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.vpn_key), null) },
                    summary = { Text(contentOrUnset(uiState.subscriptionToken)) },
                    valueToText = { it },
                )
            }
        }

        item("subscription_force_resolve", PreferenceType.SWITCH) {
            SwitchPreference(
                value = uiState.subscriptionForceResolve,
                onValueChange = { viewModel.setSubscriptionForceResolve(it) },
                title = { Text(stringResource(Res.string.force_resolve)) },
                icon = { Icon(vectorResource(Res.drawable.manage_search), null) },
                summary = { Text(stringResource(Res.string.force_resolve_sum)) },
            )
        }
        item("subscription_deduplication", PreferenceType.SWITCH) {
            SwitchPreference(
                value = uiState.subscriptionDeduplication,
                onValueChange = { viewModel.setSubscriptionDeduplication(it) },
                title = { Text(stringResource(Res.string.deduplication)) },
                icon = { Icon(vectorResource(Res.drawable.import_contacts), null) },
                summary = { Text(stringResource(Res.string.deduplication_sum)) },
            )
        }
        item("subscription_filter_not_regex", PreferenceType.TEXT_FIELD) {
            TextFieldPreference(
                value = uiState.subscriptionFilterNotRegex,
                onValueChange = { viewModel.setSubscriptionFilterNotRegex(it) },
                title = { Text(stringResource(Res.string.filter_regex)) },
                textToValue = { it },
                icon = { Icon(vectorResource(Res.drawable.delete_sweep), null) },
                summary = { Text(contentOrUnset(uiState.subscriptionFilterNotRegex)) },
                valueToText = { it },
            )
        }

        item("category_update", PreferenceType.CATEGORY) {
            PreferenceCategory(text = { Text(stringResource(Res.string.update_settings)) })
        }
        item("subscription_update_when_connected_only", PreferenceType.SWITCH) {
            SwitchPreference(
                value = uiState.subscriptionUpdateWhenConnectedOnly,
                onValueChange = { viewModel.setSubscriptionUpdateWhenConnectedOnly(it) },
                title = { Text(stringResource(Res.string.update_when_connected_only)) },
                icon = { Icon(vectorResource(Res.drawable.security), null) },
                summary = { Text(stringResource(Res.string.update_when_connected_only_sum)) },
            )
        }
        item("subscription_user_agent", PreferenceType.TEXT_FIELD) {
            TextFieldPreference(
                value = uiState.subscriptionUserAgent,
                onValueChange = { viewModel.setSubscriptionUserAgent(it) },
                title = { Text(stringResource(Res.string.subscription_user_agent)) },
                textToValue = { it },
                icon = { Icon(vectorResource(Res.drawable.grid_3x3), null) },
                summary = {
                    val text = uiState.subscriptionUserAgent.blankAsNull() ?: USER_AGENT
                    Text(text)
                },
                valueToText = { it },
            )
        }
        item("subscription_auto_update", PreferenceType.SWITCH) {
            SwitchPreference(
                value = uiState.subscriptionAutoUpdate,
                onValueChange = { viewModel.setSubscriptionAutoUpdate(it) },
                title = { Text(stringResource(Res.string.auto_update)) },
                icon = {
                    Icon(
                        vectorResource(Res.drawable.flip_camera_android),
                        null,
                    )
                },
            )
        }
        item("subscription_update_delay", PreferenceType.TEXT_FIELD) {
            TextFieldPreference(
                value = uiState.subscriptionUpdateDelay,
                onValueChange = { viewModel.setSubscriptionUpdateDelay(it) },
                title = { Text(stringResource(Res.string.auto_update_delay)) },
                textToValue = { it.toIntOrNull() ?: 1440 },
                enabled = uiState.subscriptionAutoUpdate,
                icon = { Icon(vectorResource(Res.drawable.grid_3x3), null) },
                summary = { Text(uiState.subscriptionUpdateDelay.toString()) },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                },
            )
        }
    }
}
