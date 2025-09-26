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
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ui.MaterialPreferenceFragment
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.ui.configuration.ProfileSelectActivity
import io.nekohasekai.sagernet.widget.launchOnPosition
import io.nekohasekai.sagernet.widget.setSummaryForOutbound
import kotlinx.coroutines.launch
import rikka.preference.SimpleMenuPreference

class TaskerActivity : ThemedActivity(R.layout.layout_config_settings) {

    companion object {
        private const val OUTBOUND_POSITION = "1"
    }

    override val onBackPressedCallback: OnBackPressedCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                MaterialAlertDialogBuilder(this@TaskerActivity)
                    .setTitle(R.string.unsaved_changes_prompt)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        saveAndExit()
                    }
                    .setNegativeButton(R.string.no) { _, _ ->
                        finish()
                    }
                    .setNeutralButton(android.R.string.cancel, null)
                    .show()
            }
        }

    private val viewModel by viewModels<TaskerActivityViewModel>()
    private val settings by lazy { TaskerBundle.fromIntent(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toolbar = findViewById<ComposeView>(R.id.toolbar)
        toolbar.setContent {
            @Suppress("DEPRECATION")
            AppTheme {
                TopAppBar(
                    title = { Text(stringResource(R.string.tasker_settings)) },
                    navigationIcon = {
                        SimpleIconButton(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                        ) {
                            onBackPressedDispatcher.onBackPressed()
                        }
                    },
                    actions = {
                        SimpleIconButton(
                            imageVector = Icons.Filled.Done,
                            contentDescription = stringResource(R.string.apply),
                        ) {
                            lifecycleScope.launch {
                                saveAndExit()
                            }
                        }
                    },
                )
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )
            insets
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings, MyPreferenceFragmentCompat())
                .commit()
        }
        onBackPressedCallback.isEnabled = viewModel.dirty

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect(::handleUiEvent)
            }
        }

        DataStore.profileCacheStore.registerChangeListener(viewModel)
    }

    private suspend fun handleUiEvent(event: TaskerActivityUiEvent) {
        when (event) {
            TaskerActivityUiEvent.EnableBackPressCallback -> if (!onBackPressedCallback.isEnabled) {
                onBackPressedCallback.isEnabled = true
            }

            is TaskerActivityUiEvent.SetAction -> {
                settings.action = event.action
                profile.isEnabled = event.action == TaskerBundle.ACTION_START
            }

            is TaskerActivityUiEvent.SetProfileID -> {
                settings.profileId = event.id
                profile.setSummaryForOutbound()
            }
        }
    }

    private lateinit var profile: SimpleMenuPreference
    private lateinit var action: SimpleMenuPreference

    fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.tasker_preferences)
        profile = findPreference(Key.TASKER_PROFILE)!!
        profile.setSummaryForOutbound()
        profile.launchOnPosition(OUTBOUND_POSITION) {
            selectProfileForTasker.launch(
                Intent(
                    this@TaskerActivity, ProfileSelectActivity::class.java
                ).apply {
                    putExtra(ProfileSelectActivity.EXTRA_SELECTED, settings.profileId)
                })
        }
        action = findPreference(Key.TASKER_ACTION)!!
        profile.isEnabled = action.value == TaskerBundle.ACTION_START.toString()
    }

    private val selectProfileForTasker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { (resultCode, data) ->
        if (resultCode == RESULT_OK) {
            val id = data!!.getLongExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, 0)
            viewModel.onSelectProfile(id)
            profile.value = OUTBOUND_POSITION
        }
    }

    fun saveAndExit() {
        setResult(RESULT_OK, settings.toIntent())
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun onDestroy() {
        DataStore.profileCacheStore.unregisterChangeListener(viewModel)
        super.onDestroy()
    }

    class MyPreferenceFragmentCompat : MaterialPreferenceFragment() {

        val activity: TaskerActivity
            get() = requireActivity() as TaskerActivity

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.profileCacheStore
            try {
                activity.apply {
                    createPreferences(savedInstanceState, rootKey)
                }
            } catch (e: Exception) {
                Logs.e(e)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                v.updatePadding(
                    left = bars.left,
                    right = bars.right,
                    bottom = bars.bottom,
                )
                insets
            }
        }

    }

}