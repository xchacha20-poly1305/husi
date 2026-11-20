package fr.husi.tasker

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.ListPreference
import fr.husi.compose.ProvidePreferenceLocals
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.paddingExceptBottom
import fr.husi.compose.theme.AppTheme
import fr.husi.database.ProfileManager
import fr.husi.ktx.intListN
import fr.husi.permission.LocalPermissionPlatform
import fr.husi.permission.rememberAndroidPermissionPlatform
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.apply
import fr.husi.resources.close
import fr.husi.resources.done
import fr.husi.resources.layers
import fr.husi.resources.menu_configuration
import fr.husi.resources.no
import fr.husi.resources.not_set
import fr.husi.resources.ok
import fr.husi.resources.question_mark
import fr.husi.resources.router
import fr.husi.resources.select_profile
import fr.husi.resources.tasker_action
import fr.husi.resources.tasker_action_start_service
import fr.husi.resources.tasker_action_stop_service
import fr.husi.resources.tasker_blurb_start_profile
import fr.husi.resources.tasker_settings
import fr.husi.resources.tasker_start_current_profile
import fr.husi.resources.unsaved_changes_prompt
import fr.husi.ui.ComposeActivity
import fr.husi.ui.configuration.ProfileSelectSheet
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

class TaskerActivity : ComposeActivity() {

    private val viewModel by viewModels<TaskerActivityViewModel>()
    private lateinit var settings: TaskerBundle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            reloadIntent(intent)
        }

        setContent {
            val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
            var showBackAlert by remember { mutableStateOf(false) }
            BackHandler(enabled = isDirty) {
                showBackAlert = true
            }
            var profileSelectSession by remember { mutableStateOf<ProfileSelectSession?>(null) }

            val windowInsets = WindowInsets.safeDrawing
            val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

            val platformPermission = rememberAndroidPermissionPlatform()

            CompositionLocalProvider(
                LocalPermissionPlatform provides platformPermission,
            ) {
                AppTheme {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        topBar = {
                            CapsuleTopBar(
                                hazeState = null,
                                title = { Text(stringResource(Res.string.tasker_settings)) },
                                navigationIcon = {
                                    SimpleIconButton(
                                        imageVector = vectorResource(Res.drawable.close),
                                        contentDescription = stringResource(Res.string.close),
                                        onClick = {
                                            onBackPressedDispatcher.onBackPressed()
                                        },
                                    )
                                },
                                actions = {
                                    CapsuleActionButton {
                                        SimpleIconButton(
                                            imageVector = vectorResource(Res.drawable.done),
                                            contentDescription = stringResource(Res.string.apply),
                                            onClick = {
                                                saveAndExit()
                                            },
                                        )
                                    }
                                },
                                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                                scrollBehavior = scrollBehavior,
                            )
                        },
                    ) { innerPadding ->
                        Column(modifier = Modifier.paddingExceptBottom(innerPadding)) {
                            TaskerPreference(
                                onOpenProfileSelect = { preSelected, onSelected ->
                                    profileSelectSession =
                                        ProfileSelectSession(preSelected, onSelected)
                                },
                            )

                            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                        }
                    }

                    if (showBackAlert) AlertDialog(
                        onDismissRequest = { showBackAlert = false },
                        confirmButton = {
                            TextButton(stringResource(Res.string.ok)) {
                                saveAndExit()
                            }
                        },
                        dismissButton = {
                            TextButton(stringResource(Res.string.no)) {
                                finish()
                            }
                        },
                        icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
                        title = { Text(stringResource(Res.string.unsaved_changes_prompt)) },
                    )

                    val session = profileSelectSession
                    if (session != null) {
                        ProfileSelectSheet(
                            preSelected = session.preSelected,
                            onDismiss = { profileSelectSession = null },
                            onSelected = { id ->
                                session.onSelected(id)
                                profileSelectSession = null
                            },
                        )
                    }
                }
            }
        }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        reloadIntent(intent)
    }

    private fun reloadIntent(intent: Intent) {
        settings = TaskerBundle.fromIntent(intent)
        viewModel.loadFromSetting(settings.action, settings.profileId)
    }

    private fun saveAndExit() {
        lifecycleScope.launch {
            setResult(RESULT_OK, buildIntent())
            finish()
        }
    }

    private suspend fun buildIntent(): Intent {
        val uiState = viewModel.uiState.value
        val action = uiState.action
        val profileId = uiState.profileID
        settings.action = action
        settings.profileId = profileId
        val blurb = when (action) {
            TaskerBundle.ACTION_START -> {
                val entity = if (profileId > 0) {
                    ProfileManager.getProfile(profileId)
                } else {
                    null
                }
                if (entity != null) {
                    resolveRepository().getString(
                        Res.string.tasker_blurb_start_profile, entity.displayName(),
                    )
                } else {
                    resolveRepository().getString(Res.string.tasker_action_start_service)
                }
            }

            TaskerBundle.ACTION_STOP -> {
                resolveRepository().getString(Res.string.tasker_action_stop_service)
            }

            else -> ""
        }
        return Intent().apply {
            putExtra(TaskerBundle.EXTRA_BUNDLE, settings.bundle)
            putExtra(TaskerBundle.EXTRA_STRING_BLURB, blurb)
        }
    }

    @Composable
    private fun TaskerPreference(
        onOpenProfileSelect: (preSelected: Long?, onSelected: (Long) -> Unit) -> Unit,
    ) {
        ProvidePreferenceLocals {
            val uiState by viewModel.uiState.collectAsState()

            val startServiceText = stringResource(Res.string.tasker_action_start_service)
            val stopServiceText = stringResource(Res.string.tasker_action_stop_service)
            fun actionText(action: Int) = when (action) {
                TaskerBundle.ACTION_START -> startServiceText
                TaskerBundle.ACTION_STOP -> stopServiceText
                else -> error("impossible")
            }
            ListPreference(
                value = uiState.action,
                onValueChange = { viewModel.setAction(it) },
                values = intListN(2),
                title = { Text(stringResource(Res.string.tasker_action)) },
                icon = { Icon(vectorResource(Res.drawable.layers), null) },
                summary = { Text(actionText(uiState.action)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = {
                    AnnotatedString(actionText(it))
                },
            )

            val startCurrentProfileText = stringResource(Res.string.tasker_start_current_profile)
            val selectProfileText = stringResource(Res.string.select_profile)
            ListPreference(
                value = uiState.profileID,
                onValueChange = {
                    if (it == -1L) {
                        viewModel.setProfileID(it)
                    } else {
                        onOpenProfileSelect(uiState.profileID.takeIf { id -> id > 0 }) { id ->
                            viewModel.setProfileID(id)
                        }
                    }
                },
                values = listOf(-1L, 0L),
                title = { Text(stringResource(Res.string.menu_configuration)) },
                enabled = uiState.action == TaskerBundle.ACTION_START,
                icon = { Icon(vectorResource(Res.drawable.router), null) },
                summary = {
                    val notSet = stringResource(Res.string.not_set)
                    val summary by produceState(notSet, uiState.profileID) {
                        value = ProfileManager.getProfile(uiState.profileID)?.displayName()
                            ?: notSet
                    }
                    Text(summary)
                },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = {
                    val text = if (it == -1L) {
                        startCurrentProfileText
                    } else {
                        selectProfileText
                    }
                    AnnotatedString(text)
                },
            )
        }
    }

}

private data class ProfileSelectSession(
    val preSelected: Long?,
    val onSelected: (Long) -> Unit,
)
