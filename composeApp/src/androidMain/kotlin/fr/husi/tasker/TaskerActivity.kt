/******************************************************************************
 * Copyright (C) 2022 by nekohasekai <contact-git@sekai.icu>                  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

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
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.paddingExceptBottom
import fr.husi.compose.theme.AppTheme
import fr.husi.database.ProfileManager
import fr.husi.ktx.intListN
import fr.husi.repository.repo
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
import fr.husi.resources.route_profile
import fr.husi.resources.router
import fr.husi.resources.tasker_action
import fr.husi.resources.tasker_action_start_service
import fr.husi.resources.tasker_action_stop_service
import fr.husi.resources.tasker_blurb_start_profile
import fr.husi.resources.tasker_settings
import fr.husi.resources.tasker_start_current_profile
import fr.husi.resources.unsaved_changes_prompt
import fr.husi.ui.ComposeActivity
import fr.husi.ui.MainViewModel
import fr.husi.ui.configuration.ProfileSelectSheet
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

class TaskerActivity : ComposeActivity() {

    private val viewModel by viewModels<TaskerActivityViewModel>()
    private val mainViewModel by viewModels<MainViewModel>()
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

            AppTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        TopAppBar(
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
                                SimpleIconButton(
                                    imageVector = vectorResource(Res.drawable.done),
                                    contentDescription = stringResource(Res.string.apply),
                                    onClick = {
                                        saveAndExit()
                                    },
                                )
                            },
                            windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { innerPadding ->
                    Column(modifier = Modifier.paddingExceptBottom(innerPadding)) {
                        TaskerPreference(
                            onOpenProfileSelect = { preSelected, onSelected ->
                                profileSelectSession = ProfileSelectSession(preSelected, onSelected)
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
                        mainViewModel = mainViewModel,
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
        setResult(RESULT_OK, buildIntent())
        finish()
    }

    private fun buildIntent(): Intent {
        val uiState = viewModel.uiState.value
        val action = uiState.action
        val profileId = uiState.profileID
        settings.action = action
        settings.profileId = profileId
        var blurb = ""
        when (action) {
            TaskerBundle.ACTION_START -> {
                if (profileId > 0) {
                    val entity = ProfileManager.getProfile(profileId)
                    if (entity != null) {
                        blurb = runBlocking {
                            repo.getString(
                                Res.string.tasker_blurb_start_profile, entity.displayName(),
                            )
                        }
                    }
                }
                if (blurb.isBlank()) runBlocking {
                    blurb = repo.getString(Res.string.tasker_action_start_service)
                }
            }

            TaskerBundle.ACTION_STOP -> runBlocking {
                blurb = repo.getString(Res.string.tasker_action_stop_service)
            }
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

            fun actionText(action: Int) = when (action) {
                TaskerBundle.ACTION_START -> Res.string.tasker_action_start_service
                TaskerBundle.ACTION_STOP -> Res.string.tasker_action_stop_service
                else -> error("impossible")
            }
            ListPreference(
                value = uiState.action,
                onValueChange = { viewModel.setAction(it) },
                values = intListN(2),
                title = { Text(stringResource(Res.string.tasker_action)) },
                icon = { Icon(vectorResource(Res.drawable.layers), null) },
                summary = { Text(stringResource(actionText(uiState.action))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = {
                    val text = runBlocking { repo.getString(actionText(it)) }
                    AnnotatedString(text)
                },
            )

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
                    val summary = ProfileManager.getProfile(uiState.profileID)?.displayName()
                        ?: stringResource(Res.string.not_set)
                    Text(summary)
                },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = {
                    val id = if (it == -1L) {
                        Res.string.tasker_start_current_profile
                    } else {
                        Res.string.route_profile
                    }
                    val text = runBlocking { repo.getString(id) }
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
