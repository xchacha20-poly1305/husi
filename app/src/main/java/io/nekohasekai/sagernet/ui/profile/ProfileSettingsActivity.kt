package io.nekohasekai.sagernet.ui.profile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.Toolbar
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.QuickToggleShortcut
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet.Companion.app
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.LayoutGroupItemBinding
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.MaterialPreferenceFragment
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.ui.getStringOrRes
import kotlinx.coroutines.launch

@Suppress("UNCHECKED_CAST")
abstract class ProfileSettingsActivity<T : AbstractBean>(
    @LayoutRes resId: Int = R.layout.layout_config_settings,
) : ThemedActivity(resId) {

    override val onBackPressedCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            MaterialAlertDialogBuilder(this@ProfileSettingsActivity)
                .setTitle(R.string.unsaved_changes_prompt)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch {
                        saveAndExit()
                    }
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    finish()
                }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "id"
        const val EXTRA_IS_SUBSCRIPTION = "sub"
    }


    internal open val viewModel: ProfileSettingsViewModel<T> by viewModels<ProfileSettingsViewModel<T>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setTitle(R.string.profile_config)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        if (savedInstanceState == null) lifecycleScope.launch {
            val editingId = intent.getLongExtra(EXTRA_PROFILE_ID, 0L)
            val isSubscription = intent.getBooleanExtra(EXTRA_IS_SUBSCRIPTION, false)
            viewModel.initialize(editingId, isSubscription)

            onMainDispatcher {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings, MyPreferenceFragmentCompat())
                    .commit()
            }
        }

        onBackPressedCallback.isEnabled = viewModel.dirty

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect(::handleUiEvent)
            }
        }
    }

    private fun handleUiEvent(event: ProfileSettingsUiEvent) {
        when (event) {
            ProfileSettingsUiEvent.EnableBackPressCallback -> {
                onBackPressedCallback.isEnabled = true
            }

            is ProfileSettingsUiEvent.Alert -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getStringOrRes(event.title))
                    .setMessage(getStringOrRes(event.message))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    open suspend fun saveAndExit() {
        viewModel.saveEntity()
        setResult(RESULT_OK)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.profile_config_menu, menu)
        val isNew = DataStore.editingId == 0L
        menu.findItem(R.id.action_move)?.apply {
            if (!isNew && !viewModel.isSubscription
                && SagerDatabase.groupDao.allGroups()
                    .filter { it.type == GroupType.BASIC }.size > 1 // have other basic group
            ) isVisible = true
        }
        menu.findItem(R.id.action_create_shortcut)?.apply {
            if (!isNew && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                isVisible = true // not for new profile
            }
        }
        return true
    }

    private val resultCallbackCustomConfig = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onEditedCustomConfig(result.resultCode == RESULT_OK)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_delete -> {
            if (DataStore.editingId == 0L) {
                finish()
            } else MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirm_prompt)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    runOnDefaultDispatcher {
                        viewModel.delete()
                        finish()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        R.id.action_apply -> {
            runOnDefaultDispatcher {
                saveAndExit()
            }
            true
        }

        R.id.action_custom_outbound_json -> {
            viewModel.prepareForEditCustomConfig(ProfileSettingsViewModel.CustomConfigType.Outbound)
            resultCallbackCustomConfig.launch(
                Intent(baseContext, ConfigEditActivity::class.java)
                    .putExtra(ConfigEditActivity.EXTRA_CUSTOM_CONFIG, Key.SERVER_CUSTOM_OUTBOUND)
            )
            true
        }

        R.id.action_custom_config_json -> {
            viewModel.prepareForEditCustomConfig(ProfileSettingsViewModel.CustomConfigType.Fall)
            resultCallbackCustomConfig.launch(
                Intent(baseContext, ConfigEditActivity::class.java)
                    .putExtra(ConfigEditActivity.EXTRA_CUSTOM_CONFIG, Key.SERVER_CUSTOM)
            )
            true
        }

        R.id.action_create_shortcut -> {
            val entity = viewModel.proxyEntity
            val shortcut = ShortcutInfoCompat.Builder(this, "shortcut-profile-${entity.id}")
                .setShortLabel(entity.displayName())
                .setLongLabel(entity.displayName())
                .setIcon(
                    IconCompat.createWithResource(
                        this, R.drawable.ic_qu_shadowsocks_launcher
                    )
                ).setIntent(
                    Intent(
                        baseContext, QuickToggleShortcut::class.java
                    )
                        .setAction(Intent.ACTION_MAIN)
                        .putExtra("profile", entity.id)
                ).build()
            ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
        }

        R.id.action_move -> {
            val entity = viewModel.proxyEntity
            val view = LinearLayout(baseContext).apply {
                orientation = LinearLayout.VERTICAL

                SagerDatabase.groupDao.allGroups()
                    .filter { it.type == GroupType.BASIC && it.id != entity.groupId }
                    .forEach { group ->
                        LayoutGroupItemBinding.inflate(layoutInflater, this, true).apply {
                            edit.isVisible = false
                            options.isVisible = false
                            groupName.text = group.displayName()
                            groupUpdate.text = getString(R.string.move)
                            groupUpdate.setOnClickListener {
                                lifecycleScope.launch {
                                    val oldGroupId = entity.groupId
                                    val newGroupId = group.id
                                    entity.groupId = newGroupId
                                    ProfileManager.updateProfile(entity)
                                    GroupManager.postUpdate(oldGroupId) // reload
                                    GroupManager.postUpdate(newGroupId)
                                    DataStore.editingGroup = newGroupId // post switch animation
                                    onMainDispatcher {
                                        finish()
                                    }
                                }
                            }
                        }
                    }
            }
            val scrollView = ScrollView(baseContext).apply {
                addView(view)
            }
            MaterialAlertDialogBuilder(this).setView(scrollView).show()
            true
        }

        else -> false
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    abstract fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    )

    open fun PreferenceFragmentCompat.viewCreated(view: View, savedInstanceState: Bundle?) {
    }

    open fun PreferenceFragmentCompat.displayPreferenceDialog(preference: Preference): Boolean {
        return false
    }

    class MyPreferenceFragmentCompat : MaterialPreferenceFragment() {

        val activity: ProfileSettingsActivity<*>
            get() = requireActivity() as ProfileSettingsActivity<*>

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.profileCacheStore
            try {
                activity.apply {
                    createPreferences(savedInstanceState, rootKey)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    app,
                    "Error on createPreferences, please try again.",
                    Toast.LENGTH_SHORT,
                ).show()
                Logs.e(e)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout()
                )
                v.updatePadding(
                    left = bars.left,
                    right = bars.right,
                    bottom = bars.bottom,
                )
                insets
            }

            activity.apply {
                viewCreated(view, savedInstanceState)
            }
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            activity.apply {
                if (displayPreferenceDialog(preference)) return
            }
            super.onDisplayPreferenceDialog(preference)
        }

    }

}