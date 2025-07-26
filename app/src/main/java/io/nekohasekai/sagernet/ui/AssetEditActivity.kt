package io.nekohasekai.sagernet.ui

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.support.annotation.LayoutRes
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.github.shadowsocks.plugin.Empty
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.AssetEntity
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class AssetEditActivity(
    @LayoutRes resId: Int = R.layout.layout_config_settings,
) : ThemedActivity(resId) {

    override val onBackPressedCallback: OnBackPressedCallback
        get() = mOnBackPressedCallback
    private val mOnBackPressedCallback by lazy {
        object : OnBackPressedCallback(enabled = viewModel.backEnabled.value) {
            override fun handleOnBackPressed() {
                UnsavedChangesDialogFragment().apply {
                    key()
                }.show(supportFragmentManager, null)
            }
        }
    }

    fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.asset_preferences)
    }

    class UnsavedChangesDialogFragment : AlertDialogFragment<Empty, Empty>(Empty::class.java) {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.unsaved_changes_prompt)
            setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    (requireActivity() as AssetEditActivity).saveAndExit()
                }
            }
            setNegativeButton(R.string.no) { _, _ ->
                requireActivity().finish()
            }
            setNeutralButton(android.R.string.cancel, null)
        }
    }

    @Parcelize
    data class AssetNameArg(val assetName: String) : Parcelable
    class DeleteConfirmationDialogFragment : AlertDialogFragment<AssetNameArg, Empty>(AssetNameArg::class.java) {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.delete_confirm_prompt)
            setPositiveButton(android.R.string.ok) { _, _ ->
                (requireActivity() as AssetEditActivity).apply {
                    setResult(RESULT_DELETE)
                    finish()
                }
            }
            setNegativeButton(android.R.string.cancel, null)
        }
    }

    companion object {
        const val EXTRA_ASSET_NAME = "name"

        const val RESULT_SHOULD_UPDATE = 1
        const val RESULT_DELETE = 2
    }

    override fun onStart() {
        super.onStart()
        DataStore.profileCacheStore.registerChangeListener(viewModel)
    }

    private val viewModel: AssetEditActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                top = bars.top,
                left = bars.left,
                right = bars.right,
            )
            insets
        }

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setTitle(R.string.assets_settings)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        if (savedInstanceState == null) {
            val editingAssetName = intent.getStringExtra(EXTRA_ASSET_NAME) ?: ""
            viewModel.editingAssetName = editingAssetName
            lifecycleScope.launch {
                if (editingAssetName.isEmpty()) {
                    // Create at first time
                    viewModel.shouldUpdate = true
                    viewModel.loadAssetEntity(AssetEntity())
                } else {
                    val entity = SagerDatabase.assetDao.get(editingAssetName)
                    if (entity == null) {
                        onMainDispatcher {
                            finish()
                        }
                        return@launch
                    }
                    viewModel.loadAssetEntity(entity)
                }

                onMainDispatcher {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.settings, MyPreferenceFragmentCompat())
                        .commit()

                    DataStore.dirty = false
                }
            }

        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.backEnabled.collect { enabled ->
                    onBackPressedCallback.isEnabled = enabled
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect(::handleEvent)
            }
        }
    }

    private fun handleEvent(event: AssetEditEvents) {
        when (event) {
            is AssetEditEvents.UpdateName -> {
                val fragment = supportFragmentManager.findFragmentById(R.id.settings) as? MyPreferenceFragmentCompat
                fragment?.updateAssetNamePreference(event.name)
            }
        }
    }

    suspend fun saveAndExit() {
        viewModel.validate()?.let {
            onMainDispatcher {
                MaterialAlertDialogBuilder(this@AssetEditActivity)
                    .setTitle(R.string.error_title)
                    .setMessage(it)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            return
        }

        viewModel.save()
        setResult(if (viewModel.shouldUpdate) {
            RESULT_SHOULD_UPDATE
        } else {
            RESULT_OK
        }, Intent().putExtra(EXTRA_ASSET_NAME, DataStore.assetName))
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.profile_config_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_delete -> {
            val editingAssetName = viewModel.editingAssetName
            if (editingAssetName.isEmpty()) {
                finish()
            } else {
                DeleteConfirmationDialogFragment().apply {
                    arg(AssetNameArg(editingAssetName))
                    key()
                }.show(supportFragmentManager, null)
            }
            true
        }

        R.id.action_apply -> {
            lifecycleScope.launch {
                saveAndExit()
            }
            true
        }

        else -> false
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun onStop() {
        DataStore.profileCacheStore.unregisterChangeListener(viewModel)
        super.onStop()
    }

    class MyPreferenceFragmentCompat : MaterialPreferenceFragment() {

        val activity: AssetEditActivity
            get() = requireActivity() as AssetEditActivity

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.profileCacheStore
            try {
                activity.apply {
                    createPreferences(savedInstanceState, rootKey)
                }
            } catch (e: Exception) {
                Logs.w(e)
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

        fun updateAssetNamePreference(newName: String) {
            val assetName = findPreference<EditTextPreference>(Key.ASSET_NAME)
            assetName?.text = newName
        }
    }

}