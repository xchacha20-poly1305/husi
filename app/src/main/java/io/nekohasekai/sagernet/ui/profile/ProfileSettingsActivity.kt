package io.nekohasekai.sagernet.ui.profile

import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.github.shadowsocks.plugin.Empty
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.QuickToggleShortcut
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet.Companion.app
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.databinding.LayoutGroupItemBinding
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.byteBuffer
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.Parcelize
import java.io.ByteArrayOutputStream
import kotlin.properties.Delegates

@Suppress("UNCHECKED_CAST")
abstract class ProfileSettingsActivity<T : AbstractBean>(
    @LayoutRes resId: Int = R.layout.layout_config_settings,
) : ThemedActivity(resId), OnPreferenceDataStoreChangeListener {

    override val onBackPressedCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            UnsavedChangesDialogFragment().apply {
                key()
            }.show(supportFragmentManager, null)
        }
    }

    class UnsavedChangesDialogFragment : AlertDialogFragment<Empty, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.unsaved_changes_prompt)
            setPositiveButton(android.R.string.ok) { _, _ ->
                runOnDefaultDispatcher {
                    (requireActivity() as ProfileSettingsActivity<*>).saveAndExit()
                }
            }
            setNegativeButton(R.string.no) { _, _ ->
                requireActivity().finish()
            }
            setNeutralButton(android.R.string.cancel, null)
        }
    }

    @Parcelize
    data class ProfileIdArg(val profileId: Long, val groupId: Long) : Parcelable
    class DeleteConfirmationDialogFragment : AlertDialogFragment<ProfileIdArg, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.delete_confirm_prompt)
            setPositiveButton(android.R.string.ok) { _, _ ->
                runOnDefaultDispatcher {
                    ProfileManager.deleteProfile(arg.groupId, arg.profileId)
                }
                requireActivity().finish()
            }
            setNegativeButton(android.R.string.cancel, null)
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "id"
        const val EXTRA_IS_SUBSCRIPTION = "sub"
        const val KEY_TEMP_BEAN_BYTES = "temp_bean_bytes"
    }

    abstract fun createBean(): T
    abstract fun T.init()
    abstract fun T.serialize()

    val proxyEntity by lazy {
        SagerDatabase.proxyDao.getById(DataStore.editingId) ?: runBlocking {
            createEntity()
        }
    }
    protected var bean: T? = null
    protected var isSubscription by Delegates.notNull<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        savedInstanceState?.getByteArray(KEY_TEMP_BEAN_BYTES)?.let {
            bean = createBean().apply {
                deserialize(ByteBufferInput(it))
            }
        }
        super.onCreate(savedInstanceState)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
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
            setTitle(R.string.profile_config)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        if (savedInstanceState == null) runOnDefaultDispatcher {
            val editingId = intent.getLongExtra(EXTRA_PROFILE_ID, 0L)
            isSubscription = intent.getBooleanExtra(EXTRA_IS_SUBSCRIPTION, false)
            DataStore.editingId = editingId
            bean = if (editingId == 0L) {
                DataStore.editingGroup = DataStore.selectedGroupForImport()
                createBean().applyDefaultValues()
            } else {
                DataStore.editingGroup = proxyEntity.groupId
                (proxyEntity.requireBean() as T)
            }
            bean!!.init()

            onMainDispatcher {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings, MyPreferenceFragmentCompat())
                    .commit()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        bean?.let {
            val output = ByteArrayOutputStream()
            val buffer = output.byteBuffer()
            it.serializeToBuffer(buffer)
            buffer.flush()
            buffer.close()
            outState.putByteArray(KEY_TEMP_BEAN_BYTES, output.toByteArray())
        }
    }

    open suspend fun saveAndExit() {
        ProfileManager.updateProfile(proxyEntity.apply { (requireBean() as T).serialize() })
        setResult(RESULT_OK)
        finish()
    }

    open suspend fun createEntity(): ProxyEntity {
        val editingGroup = DataStore.editingGroup
        return ProfileManager.createProfile(editingGroup, createBean().apply { serialize() })
    }

    val child by lazy { supportFragmentManager.findFragmentById(R.id.settings) as MyPreferenceFragmentCompat }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.profile_config_menu, menu)
        menu.findItem(R.id.action_move)?.apply {
            if (DataStore.editingId != 0L // not new profile
                && SagerDatabase.groupDao.getById(DataStore.editingGroup)?.type == GroupType.BASIC // not in subscription group
                && SagerDatabase.groupDao.allGroups()
                    .filter { it.type == GroupType.BASIC }.size > 1 // have other basic group
            ) isVisible = true
        }
        menu.findItem(R.id.action_create_shortcut)?.apply {
            if (Build.VERSION.SDK_INT >= 26 && DataStore.editingId != 0L) {
                isVisible = true // not new profile
            }
        }
        // shared menu item
        menu.findItem(R.id.action_custom_outbound_json)?.isVisible = true
        menu.findItem(R.id.action_custom_config_json)?.isVisible = true
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_delete -> {
            if (DataStore.editingId == 0L) {
                finish()
            } else {
                DeleteConfirmationDialogFragment().apply {
                    arg(ProfileIdArg(DataStore.editingId, DataStore.editingGroup))
                    key()
                }.show(supportFragmentManager, null)
            }
            true
        }

        R.id.action_apply -> {
            runOnDefaultDispatcher {
                saveAndExit()
            }
            true
        }

        R.id.action_custom_outbound_json -> {
            val bean = proxyEntity.requireBean()
            DataStore.serverCustomOutbound = bean.customOutboundJson
            child.callbackCustomOutbound = { bean.customOutboundJson = it }
            child.resultCallbackCustomOutbound.launch(
                Intent(
                    baseContext,
                    ConfigEditActivity::class.java,
                ).apply {
                    putExtra("key", Key.SERVER_CUSTOM_OUTBOUND)
                })
            true
        }

        R.id.action_custom_config_json -> {
            val bean = proxyEntity.requireBean()
            DataStore.serverCustom = bean.customConfigJson
            child.callbackCustom = { bean.customConfigJson = it }
            child.resultCallbackCustom.launch(
                Intent(
                    baseContext,
                    ConfigEditActivity::class.java,
                ).apply {
                    putExtra("key", Key.SERVER_CUSTOM)
                })
            true
        }

        R.id.action_create_shortcut -> {
            val shortcut = ShortcutInfoCompat.Builder(this, "shortcut-profile-${proxyEntity.id}")
                .setShortLabel(proxyEntity.displayName())
                .setLongLabel(proxyEntity.displayName())
                .setIcon(
                    IconCompat.createWithResource(
                        this, R.drawable.ic_qu_shadowsocks_launcher
                    )
                ).setIntent(
                    Intent(
                        baseContext, QuickToggleShortcut::class.java
                    ).apply {
                        action = Intent.ACTION_MAIN
                        putExtra("profile", proxyEntity.id)
                    }).build()
            ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
        }

        R.id.action_move -> {
            val view = LinearLayout(baseContext).apply {
                orientation = LinearLayout.VERTICAL

                SagerDatabase.groupDao.allGroups()
                    .filter { it.type == GroupType.BASIC && it.id != proxyEntity.groupId }
                    .forEach { group ->
                        LayoutGroupItemBinding.inflate(layoutInflater, this, true).apply {
                            edit.isVisible = false
                            options.isVisible = false
                            groupName.text = group.displayName()
                            groupUpdate.text = getString(R.string.move)
                            groupUpdate.setOnClickListener {
                                runOnDefaultDispatcher {
                                    val oldGroupId = proxyEntity.groupId
                                    val newGroupId = group.id
                                    proxyEntity.groupId = newGroupId
                                    ProfileManager.updateProfile(proxyEntity)
                                    GroupManager.postUpdate(oldGroupId) // reload
                                    GroupManager.postUpdate(newGroupId)
                                    DataStore.editingGroup = newGroupId // post switch animation
                                    runOnMainDispatcher {
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

    override fun onDestroy() {
        DataStore.profileCacheStore.unregisterChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        if (key != Key.PROFILE_DIRTY) {
            DataStore.dirty = true
            onBackPressedCallback.isEnabled = true
        }
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

    class MyPreferenceFragmentCompat : PreferenceFragmentCompat() {

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
                DataStore.dirty = false
                DataStore.profileCacheStore.registerChangeListener(this)
            }
        }

        var callbackCustom: ((String) -> Unit)? = null
        var callbackCustomOutbound: ((String) -> Unit)? = null

        val resultCallbackCustom = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { (_, _) ->
            callbackCustom?.let { it(DataStore.serverCustom) }
        }

        val resultCallbackCustomOutbound = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { (_, _) ->
            callbackCustomOutbound?.let { it(DataStore.serverCustomOutbound) }
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            activity.apply {
                if (displayPreferenceDialog(preference)) return
            }
            super.onDisplayPreferenceDialog(preference)
        }

    }

}