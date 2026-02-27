@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.GroupType
import fr.husi.compose.BackHandler
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.paddingExceptBottom
import fr.husi.database.ProxyEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.AbstractBean
import fr.husi.resources.Res
import fr.husi.resources.apply
import fr.husi.resources.cancel
import fr.husi.resources.close
import fr.husi.resources.custom_config
import fr.husi.resources.delete
import fr.husi.resources.delete_confirm_prompt
import fr.husi.resources.done
import fr.husi.resources.full
import fr.husi.resources.group_status_empty
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.move
import fr.husi.resources.no
import fr.husi.resources.ok
import fr.husi.resources.outbound
import fr.husi.resources.question_mark
import fr.husi.resources.unsaved_changes_prompt
import fr.husi.resources.warning
import fr.husi.ui.stringOrRes
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

internal val LocalOpenConfigEditor = staticCompositionLocalOf<(String, (String) -> Unit) -> Unit> {
    { _, _ -> }
}

@Composable
fun ProfileEditorScreen(
    type: Int,
    profileId: Long,
    isSubscription: Boolean,
    onOpenProfileSelect: (preSelected: Long?, onSelected: (Long) -> Unit) -> Unit,
    onOpenConfigEditor: (String, (String) -> Unit) -> Unit,
    onResult: (updated: Boolean) -> Unit,
) {
    CompositionLocalProvider(
        LocalOpenConfigEditor provides onOpenConfigEditor,
    ) {
        when (type) {
            ProxyEntity.TYPE_CONFIG -> ConfigSettingScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_DIRECT -> DirectSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_HTTP -> HttpSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_SOCKS -> SocksSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_SSH -> SSHSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_HYSTERIA -> HysteriaSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_MIERU -> MieruSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_ANYTLS -> AnyTLSSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_CHAIN -> ChainSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onOpenProfileSelect = onOpenProfileSelect,
                onResult = onResult,
            )

            ProxyEntity.TYPE_PROXY_SET -> ProxySetSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onOpenProfileSelect = onOpenProfileSelect,
                onResult = onResult,
            )

            ProxyEntity.TYPE_SS -> ShadowsocksSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_NAIVE -> NaiveSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_JUICITY -> JuicitySettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_WG -> WireGuardSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_TUIC -> TuicSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_SHADOWTLS -> ShadowTLSSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_SHADOWQUIC -> ShadowQUICSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_TRUST_TUNNEL -> TrustTunnelSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_VMESS -> VMessSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_VLESS -> VLESSSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            ProxyEntity.TYPE_TROJAN -> TrojanSettingsScreen(
                profileId = profileId,
                isSubscription = isSubscription,
                onResult = onResult,
            )

            else -> error("Unsupported profile type: $type")
        }
    }
}

@Composable
internal fun <T : AbstractBean> ProfileSettingsScreenScaffold(
    title: StringResource,
    viewModel: ProfileSettingsViewModel<T>,
    onResult: (updated: Boolean) -> Unit,
    settings: (
        scope: LazyListScope,
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) -> Unit,
) {
    val isDirty by viewModel.isDirty.collectAsState()
    var showBackAlert by remember { mutableStateOf(false) }
    var showGenericAlert by remember { mutableStateOf<ProfileSettingsUiEvent.Alert?>(null) }
    var showMoveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is ProfileSettingsUiEvent.Alert -> showGenericAlert = event
            }
        }
    }

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showDeleteAlert by remember { mutableStateOf(false) }
    var showExtendMenu by remember { mutableStateOf(false) }
    val openConfigEditor = LocalOpenConfigEditor.current

    BackHandler(enabled = isDirty) {
        showBackAlert = true
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                    ) {
                        if (isDirty) {
                            showBackAlert = true
                        } else {
                            onResult(false)
                        }
                    }
                },
                actions = {
                    if (!viewModel.isNew) SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.delete),
                        contentDescription = stringResource(Res.string.delete),
                        onClick = { showDeleteAlert = true },
                    )
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.done),
                        contentDescription = stringResource(Res.string.apply),
                        onClick = {
                            viewModel.save()
                            onResult(true)
                        },
                    )

                    Box {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.more_vert),
                            contentDescription = stringResource(Res.string.more),
                        ) {
                            showExtendMenu = true
                        }
                        DropdownMenuPopup(
                            expanded = showExtendMenu,
                            onDismissRequest = { showExtendMenu = false },
                        ) {
                            val showCreateShortCut =
                                platformSupportShortcut()
                                        && !viewModel.isNew
                                        && !viewModel.isSubscription
                            val showMove = !viewModel.isNew && runBlocking {
                                SagerDatabase.groupDao.allGroups().first().filter {
                                    it.type == GroupType.BASIC
                                }.size > 1
                            }
                            val hasFirstGroup = showCreateShortCut || showMove
                            if (hasFirstGroup) {
                                DropdownMenuGroup(
                                    shapes = MenuDefaults.groupShape(0, 2),
                                ) {
                                    if (showCreateShortCut) {
                                        ShortcutMenuItem(viewModel.proxyEntity) {
                                            showExtendMenu = false
                                        }
                                    }
                                    if (showMove) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.move)) },
                                            onClick = { showMoveDialog = true },
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))
                            }

                            DropdownMenuGroup(
                                shapes = if (hasFirstGroup) {
                                    MenuDefaults.groupShape(1, 2)
                                } else {
                                    MenuDefaults.groupShapes()
                                },
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
                                    text = { Text(stringResource(Res.string.outbound)) },
                                    onClick = {
                                        showExtendMenu = false
                                        openConfigEditor(viewModel.uiState.value.customOutbound) { text ->
                                            viewModel.setCustomOutbound(text)
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.full)) },
                                    onClick = {
                                        showExtendMenu = false
                                        openConfigEditor(viewModel.uiState.value.customConfig) { text ->
                                            viewModel.setCustomConfig(text)
                                        }
                                    },
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
        ProfileSettingsMainColumn(
            modifier = Modifier.paddingExceptBottom(innerPadding),
            viewModel = viewModel,
            settings = settings,
        )
    }

    if (showBackAlert) AlertDialog(
        onDismissRequest = { showBackAlert = false },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                viewModel.save()
                onResult(true)
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.no)) {
                onResult(false)
            }
        },
        icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
        title = { Text(stringResource(Res.string.unsaved_changes_prompt)) },
    )

    if (showDeleteAlert) AlertDialog(
        onDismissRequest = { showDeleteAlert = false },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                showDeleteAlert = false
                viewModel.delete()
                onResult(false)
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.cancel)) {
                showDeleteAlert = false
            }
        },
        icon = { Icon(vectorResource(Res.drawable.warning), null) },
        title = { Text(stringResource(Res.string.delete_confirm_prompt)) },
    )

    if (showMoveDialog) {
        MoveProfileDialog(
            viewModel = viewModel,
            onDismiss = { showMoveDialog = false },
            onMove = {
                showMoveDialog = false
                onResult(false)
            },
        )
    }

    showGenericAlert?.let { alert ->
        AlertDialog(
            onDismissRequest = { showGenericAlert = null },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    showGenericAlert = null
                }
            },
            icon = { Icon(vectorResource(Res.drawable.warning), null) },
            title = { Text(stringOrRes(alert.title)) },
            text = { Text(stringOrRes(alert.message)) },
        )
    }
}

@Composable
private fun <T : AbstractBean> ProfileSettingsMainColumn(
    modifier: Modifier,
    viewModel: ProfileSettingsViewModel<T>,
    settings: (
        scope: LazyListScope,
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) -> Unit,
) {
    ProvidePreferenceLocals {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val listState = rememberLazyListState()
        var scrollToKey by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(scrollToKey) {
            listState.layoutInfo.visibleItemsInfo
                .indexOfFirst { it.key == scrollToKey }
                .takeIf { it >= 0 }?.let {
                    listState.animateScrollToItem(it)
                    scrollToKey = null
                }
        }

        Row(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                state = listState,
            ) {
                settings(this, uiState) { key ->
                    scrollToKey = key
                }

                item("bottom_padding") {
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }

            BoxedVerticalScrollbar(
                modifier = Modifier.fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = listState),
                style = defaultMaterialScrollbarStyle().copy(
                    thickness = 12.dp,
                ),
            )
        }
    }
}

@Composable
private fun <T : AbstractBean> MoveProfileDialog(
    viewModel: ProfileSettingsViewModel<T>,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
) {
    val groups by produceState(
        initialValue = emptyList(),
        key1 = viewModel.proxyEntity.groupId,
    ) {
        value = viewModel.groupsForMove()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.move)) },
        text = {
            if (groups.isEmpty()) {
                Text(text = stringResource(Res.string.group_status_empty))
            } else {
                LazyColumn {
                    items(groups, key = { it.id }) { group ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.move(group.id)
                                        onMove()
                                    }
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = group.displayName(),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(stringResource(Res.string.cancel)) {
                onDismiss()
            }
        },
    )
}
