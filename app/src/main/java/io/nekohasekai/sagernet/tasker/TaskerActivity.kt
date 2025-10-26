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

package io.nekohasekai.sagernet.tasker

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Router
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.paddingExceptBottom
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ktx.intListN
import io.nekohasekai.sagernet.ui.ComposeActivity
import io.nekohasekai.sagernet.ui.configuration.ProfileSelectActivity
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.ProvidePreferenceLocals

class TaskerActivity : ComposeActivity() {

    private val viewModel by viewModels<TaskerActivityViewModel>()
    private lateinit var settings: TaskerBundle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reloadIntent(intent)

        setContent {
            val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
            var showBackAlert by remember { mutableStateOf(false) }
            BackHandler(enabled = isDirty) {
                showBackAlert = true
            }

            val windowInsets = WindowInsets.safeDrawing
            val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

            AppTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.tasker_settings)) },
                            navigationIcon = {
                                SimpleIconButton(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.close),
                                    onClick = {
                                        onBackPressedDispatcher.onBackPressed()
                                    },
                                )
                            },
                            actions = {
                                SimpleIconButton(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = stringResource(R.string.apply),
                                    onClick = {
                                        saveAndExit()
                                    },
                                )
                            },
                            windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                            scrollBehavior = scrollBehavior,
                        )
                    }
                ) { innerPadding ->
                    Column(modifier = Modifier.paddingExceptBottom(innerPadding)) {
                        TaskerPreference()

                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
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

    private val selectProfileForTasker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { (resultCode, data) ->
        if (resultCode == RESULT_OK) {
            val id = data!!.getLongExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, 0)
            viewModel.setProfileID(id)
        }
    }

    private fun saveAndExit() {
        setResult(RESULT_OK, buildIntent())
        finish()
    }

    private fun buildIntent(): Intent {
        val uiState = viewModel.uiState.value
        val action = uiState.action
        val profileId = uiState.profileID
        var blurb = ""
        when (action) {
            TaskerBundle.ACTION_START -> {
                if (profileId > 0) {
                    val entity = ProfileManager.getProfile(profileId)
                    if (entity != null) {
                        blurb = getString(
                            R.string.tasker_blurb_start_profile, entity.displayName()
                        )
                    }
                }
                if (blurb.isBlank()) {
                    blurb = getString(R.string.tasker_action_start_service)
                }
            }

            TaskerBundle.ACTION_STOP -> {
                blurb = getString(R.string.tasker_action_stop_service)
            }
        }
        return Intent().apply {
            putExtra(TaskerBundle.EXTRA_BUNDLE, settings.bundle)
            putExtra(TaskerBundle.EXTRA_STRING_BLURB, blurb)
        }
    }

    @Composable
    private fun TaskerPreference() {
        ProvidePreferenceLocals {
            val uiState by viewModel.uiState.collectAsState()

            fun actionText(action: Int) = when (action) {
                TaskerBundle.ACTION_START -> R.string.tasker_action_start_service
                TaskerBundle.ACTION_STOP -> R.string.tasker_action_stop_service
                else -> error("impossible")
            }
            ListPreference(
                value = uiState.action,
                onValueChange = { viewModel.setAction(it) },
                values = intListN(2),
                title = { Text(stringResource(R.string.tasker_action)) },
                icon = { Icon(Icons.Filled.Layers, null) },
                summary = { Text(stringResource(actionText(uiState.action))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(getString(actionText(it))) },
            )

            ListPreference(
                value = uiState.profileID,
                onValueChange = {
                    if (it == -1L) {
                        viewModel.setProfileID(it)
                    } else {
                        selectProfileForTasker.launch(
                            Intent(this@TaskerActivity, ProfileSelectActivity::class.java)
                                .putExtra(ProfileSelectActivity.EXTRA_SELECTED, uiState.profileID)
                        )
                    }
                },
                values = listOf(-1L, 0L),
                title = { Text(stringResource(R.string.menu_configuration)) },
                enabled = uiState.action == TaskerBundle.ACTION_START,
                icon = { Icon(Icons.Filled.Router, null) },
                summary = {
                    val summary = ProfileManager.getProfile(uiState.profileID)?.displayName()
                        ?: stringResource(R.string.not_set)
                    Text(summary)
                },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = {
                    val id = if (it == -1L) {
                        R.string.tasker_start_current_profile
                    } else {
                        R.string.route_profile
                    }
                    AnnotatedString(getString(id))
                },
            )
        }
    }

}