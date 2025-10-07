package io.nekohasekai.sagernet.ui.profile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.QuickToggleShortcut
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.paddingExceptBottom
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ui.ComposeActivity
import io.nekohasekai.sagernet.ui.stringResource
import me.zhanghai.compose.preference.ProvidePreferenceLocals

@ExperimentalMaterial3Api
@Suppress("UNCHECKED_CAST")
abstract class ProfileSettingsActivity<T : AbstractBean> : ComposeActivity() {

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_IS_SUBSCRIPTION = "is_subscription"
    }


    internal open val viewModel: ProfileSettingsViewModel<T> by viewModels<ProfileSettingsViewModel<T>>()

    @get:StringRes
    protected open val title: Int = R.string.profile_config

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val editingId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        val isSubscription = intent.getBooleanExtra(EXTRA_IS_SUBSCRIPTION, false)
        viewModel.initialize(editingId, isSubscription)

        setContent {
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

            BackHandler(enabled = isDirty) {
                showBackAlert = true
            }

            AppTheme {
                val windowInsets = WindowInsets.safeDrawing
                val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                var showDeleteAlert by remember { mutableStateOf(false) }
                var showExtendMenu by remember { mutableStateOf(false) }
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(title)) },
                            navigationIcon = {
                                SimpleIconButton(Icons.Filled.Close) {
                                    onBackPressedDispatcher.onBackPressed()
                                }
                            },
                            actions = {
                                if (!viewModel.isNew) SimpleIconButton(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    onClick = { showDeleteAlert = true },
                                )
                                SimpleIconButton(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = stringResource(R.string.apply),
                                    onClick = ::saveAndExit,
                                )

                                Box {
                                    SimpleIconButton(Icons.Filled.MoreVert) {
                                        showExtendMenu = true
                                    }
                                    DropdownMenu(
                                        expanded = showExtendMenu,
                                        onDismissRequest = { showExtendMenu = false },
                                    ) {
                                        if (!viewModel.isNew
                                            && !viewModel.isSubscription
                                            && SagerDatabase.groupDao.allGroups()
                                                .filter { it.type == GroupType.BASIC }.size > 1 // have other basic group
                                        ) DropdownMenuItem(
                                            text = { Text(stringResource(R.string.create_shortcut)) },
                                            onClick = ::buildShortCut,
                                        )
                                        if (!viewModel.isNew && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) DropdownMenuItem(
                                            text = { Text(stringResource(R.string.move)) },
                                            onClick = { showMoveDialog = true },
                                        )

                                        HorizontalDivider()
                                        Text(stringResource(R.string.custom_config))
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.outbound)) },
                                            onClick = {
                                                resultCallbackCustomOutbound.launch(
                                                    Intent(
                                                        baseContext,
                                                        ConfigEditActivity::class.java,
                                                    ).putExtra(
                                                        ConfigEditActivity.EXTRA_CUSTOM_CONFIG,
                                                        viewModel.uiState.value.customOutbound,
                                                    )
                                                )
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.full)) },
                                            onClick = {
                                                resultCallbackCustomConfig.launch(
                                                    Intent(
                                                        baseContext,
                                                        ConfigEditActivity::class.java,
                                                    ).putExtra(
                                                        ConfigEditActivity.EXTRA_CUSTOM_CONFIG,
                                                        viewModel.uiState.value.customConfig,
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
                    MainColumn(Modifier.paddingExceptBottom(innerPadding))
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

                if (showDeleteAlert) AlertDialog(
                    onDismissRequest = { showDeleteAlert = false },
                    confirmButton = {
                        TextButton(stringResource(android.R.string.ok)) {
                            showDeleteAlert = false
                            viewModel.delete()
                            finish()
                        }
                    },
                    dismissButton = {
                        TextButton(stringResource(android.R.string.cancel)) {
                            showDeleteAlert = false
                        }
                    },
                    icon = { Icon(Icons.Filled.Warning, null) },
                    title = { Text(stringResource(R.string.delete_confirm_prompt)) },
                )

                if (showMoveDialog) {
                    MoveProfileDialog {
                        showMoveDialog = false
                    }
                }

                showGenericAlert?.let { alert ->
                    AlertDialog(
                        onDismissRequest = { showGenericAlert = null },
                        confirmButton = {
                            TextButton(stringResource(android.R.string.ok)) {
                                showGenericAlert = null
                            }
                        },
                        icon = { Icon(Icons.Filled.Warning, null) },
                        title = { Text(stringResource(alert.title)) },
                        text = { Text(stringResource(alert.message)) }
                    )
                }
            }
        }

    }

    protected fun saveAndExit() {
        viewModel.save()
        setResult(RESULT_OK)
        finish()
    }

    private val resultCallbackCustomConfig = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.setCustomConfig(it.data!!.getStringExtra(ConfigEditActivity.EXTRA_CUSTOM_CONFIG)!!)
        }
    }

    private val resultCallbackCustomOutbound = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.setCustomOutbound(it.data!!.getStringExtra(ConfigEditActivity.EXTRA_CUSTOM_CONFIG)!!)
        }
    }

    @Composable
    private fun MainColumn(modifier: Modifier) {
        ProvidePreferenceLocals {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LazyColumn(modifier = modifier) {
                settings(uiState)

                item("bottom_padding") {
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }

    internal abstract fun LazyListScope.settings(state: ProfileSettingsUiState)

    private fun buildShortCut() {
        val entity = viewModel.proxyEntity
        val name = entity.displayName()
        val shortcut = ShortcutInfoCompat
            .Builder(this, "shortcut-profile-${entity.id}")
            .setShortLabel(name)
            .setLongLabel(name)
            .setIcon(
                IconCompat.createWithResource(this, R.drawable.ic_qu_shadowsocks_launcher)
            )
            .setIntent(
                Intent(baseContext, QuickToggleShortcut::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .putExtra(QuickToggleShortcut.EXTRA_PROFILE_ID, entity.id)
            )
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
    }

    @Composable
    private fun MoveProfileDialog(onDismiss: () -> Unit) {
        val groups by produceState(
            initialValue = emptyList(),
            key1 = viewModel.proxyEntity.groupId,
        ) {
            value = viewModel.groupsForMove()
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.move)) },
            text = {
                if (groups.isEmpty()) {
                    Text(text = stringResource(R.string.group_status_empty))
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
                                            finish()
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
                TextButton(stringResource(android.R.string.cancel)) {
                    onDismiss()
                }
            }
        )
    }

}