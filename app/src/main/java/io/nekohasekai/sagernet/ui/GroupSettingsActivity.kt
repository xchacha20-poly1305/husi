package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.LayoutRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet.Companion.app
import io.nekohasekai.sagernet.SubscriptionType
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.configuration.ProfileSelectActivity
import io.nekohasekai.sagernet.widget.MaterialSwitchPreference
import io.nekohasekai.sagernet.widget.UserAgentPreference
import io.nekohasekai.sagernet.widget.launchOnPosition
import io.nekohasekai.sagernet.widget.setSummaryForOutbound
import kotlinx.coroutines.launch
import rikka.preference.SimpleMenuPreference

class GroupSettingsActivity(
    @LayoutRes resId: Int = R.layout.layout_config_settings,
) : ThemedActivity(resId) {

    override val onBackPressedCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            MaterialAlertDialogBuilder(this@GroupSettingsActivity)
                .setTitle(R.string.unsaved_changes_prompt)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    runOnDefaultDispatcher {
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

    private lateinit var frontProxyPreference: SimpleMenuPreference
    private lateinit var landingProxyPreference: SimpleMenuPreference

    fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.group_preferences)

        frontProxyPreference = findPreference(Key.GROUP_FRONT_PROXY)!!
        frontProxyPreference.setSummaryForOutbound()
        frontProxyPreference.launchOnPosition(OUTBOUND_POSITION.toString()) {
            selectProfileForAddFront.launch(
                Intent(this@GroupSettingsActivity, ProfileSelectActivity::class.java)
            )
        }

        landingProxyPreference = findPreference(Key.GROUP_LANDING_PROXY)!!
        landingProxyPreference.setSummaryForOutbound()
        landingProxyPreference.launchOnPosition(OUTBOUND_POSITION.toString()) {
            selectProfileForAddLanding.launch(
                Intent(this@GroupSettingsActivity, ProfileSelectActivity::class.java)
            )
        }

        val groupType = findPreference<SimpleMenuPreference>(Key.GROUP_TYPE)!!
        val groupSubscription = findPreference<PreferenceCategory>(Key.GROUP_SUBSCRIPTION)!!
        val subscriptionUpdate = findPreference<PreferenceCategory>(Key.SUBSCRIPTION_UPDATE)!!

        fun updateGroupType(groupType: Int = DataStore.groupType) {
            val isSubscription = groupType == GroupType.SUBSCRIPTION
            groupSubscription.isVisible = isSubscription
            subscriptionUpdate.isVisible = isSubscription
        }
        updateGroupType()
        groupType.setOnPreferenceChangeListener { _, newValue ->
            updateGroupType((newValue as String).toInt())
            true
        }

        val subscriptionType = findPreference<SimpleMenuPreference>(Key.SUBSCRIPTION_TYPE)!!
        val subscriptionLink = findPreference<EditTextPreference>(Key.SUBSCRIPTION_LINK)!!
        val subscriptionToken = findPreference<EditTextPreference>(Key.SUBSCRIPTION_TOKEN)!!
        val subscriptionUserAgent =
            findPreference<UserAgentPreference>(Key.SUBSCRIPTION_USER_AGENT)!!

        fun updateSubscriptionType(subscriptionType: Int = DataStore.subscriptionType) {
            subscriptionLink.isVisible = subscriptionType != SubscriptionType.OOCv1
            subscriptionToken.isVisible = subscriptionType == SubscriptionType.OOCv1
            subscriptionUserAgent.notifyChanged()
        }
        updateSubscriptionType()
        subscriptionType.setOnPreferenceChangeListener { _, newValue ->
            updateSubscriptionType((newValue as String).toInt())
            true
        }

        val subscriptionAutoUpdate =
            findPreference<MaterialSwitchPreference>(Key.SUBSCRIPTION_AUTO_UPDATE)!!
        val subscriptionAutoUpdateDelay =
            findPreference<EditTextPreference>(Key.SUBSCRIPTION_AUTO_UPDATE_DELAY)!!

        subscriptionAutoUpdateDelay.isEnabled = subscriptionAutoUpdate.isChecked
        subscriptionAutoUpdateDelay.setOnPreferenceChangeListener { _, newValue ->
            val delay = (newValue as String).toIntOrNull()
            if (delay == null) {
                false
            } else {
                delay >= 15
            }
        }
        subscriptionAutoUpdate.setOnPreferenceChangeListener { _, newValue ->
            subscriptionAutoUpdateDelay.isEnabled = (newValue as Boolean)
            true
        }
    }

    companion object {
        const val EXTRA_GROUP_ID = "id"

        private const val OUTBOUND_POSITION = 1
    }

    private val viewModel by viewModels<GroupSettingsActivityViewModel>()

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toolbar = findViewById<ComposeView>(R.id.toolbar)
        toolbar.setContent {
            @Suppress("DEPRECATION")
            AppTheme {
                TopAppBar(
                    title = { Text(stringResource(R.string.group_settings)) },
                    navigationIcon = {
                        SimpleIconButton(
                            imageVector = Icons.Filled.Close,
                        ) {
                            onBackPressedDispatcher.onBackPressed()
                        }
                    },
                    actions = {
                        SimpleIconButton(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete),
                        ) {
                            if (viewModel.isNew) {
                                finish()
                            } else MaterialAlertDialogBuilder(this@GroupSettingsActivity)
                                .setTitle(R.string.delete_group_prompt)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    viewModel.delete()
                                    finish()
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                        SimpleIconButton(
                            imageVector = Icons.Filled.Done,
                            contentDescription = stringResource(R.string.apply),
                            onClick = ::saveAndExit,
                        )
                    },
                )
            }
        }

        if (savedInstanceState == null) {
            val editingId = intent.getLongExtra(EXTRA_GROUP_ID, 0L)
            viewModel.initialize(editingId)
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings, MyPreferenceFragmentCompat())
                .commit()
        }
        DataStore.profileCacheStore.registerChangeListener(viewModel)

        onBackPressedCallback.isEnabled = viewModel.dirty.value
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dirty.collect {
                    onBackPressedCallback.isEnabled = it
                }
            }
        }
    }

    private fun saveAndExit() {
        viewModel.save()
        setResult(RESULT_OK)
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

        val activity: GroupSettingsActivity
            get() = requireActivity() as GroupSettingsActivity

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

    private val selectProfileForAddFront = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) runOnDefaultDispatcher {
            val profile = ProfileManager.getProfile(
                it.data!!.getLongExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, 0)
            ) ?: return@runOnDefaultDispatcher
            DataStore.frontProxy = profile.id
            onMainDispatcher {
                frontProxyPreference.value = OUTBOUND_POSITION.toString()
                frontProxyPreference.setSummaryForOutbound()
            }
        }
    }

    private val selectProfileForAddLanding = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) runOnDefaultDispatcher {
            val profile = ProfileManager.getProfile(
                it.data!!.getLongExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, 0)
            ) ?: return@runOnDefaultDispatcher
            DataStore.landingProxy = profile.id
            onMainDispatcher {
                landingProxyPreference.value = OUTBOUND_POSITION.toString()
                landingProxyPreference.setSummaryForOutbound()
            }
        }
    }

}