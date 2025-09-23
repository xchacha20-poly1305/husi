package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.LayoutRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceCategory
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet.Companion.app
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ui.configuration.ProfileSelectActivity
import io.nekohasekai.sagernet.widget.AppListPreference
import io.nekohasekai.sagernet.widget.DurationPreference
import io.nekohasekai.sagernet.widget.MaterialSwitchPreference
import io.nekohasekai.sagernet.widget.launchOnPosition
import io.nekohasekai.sagernet.widget.setSummaryForOutbound
import kotlinx.coroutines.launch
import rikka.preference.SimpleMenuPreference

@ExperimentalMaterial3Api
class RouteSettingsActivity(
    @LayoutRes resId: Int = R.layout.layout_settings_activity,
) : ThemedActivity(resId) {

    companion object {
        const val EXTRA_ROUTE_ID = "id"
        const val EXTRA_PACKAGE_NAME = "pkg"
        const val NETWORK_TYPE_WIFI = "wifi"
        private const val OUTBOUND_POSITION = "3"
    }

    override val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            MaterialAlertDialogBuilder(this@RouteSettingsActivity)
                .setTitle(R.string.unsaved_changes_prompt)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.saveAndExit()
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    finish()
                }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        }
    }

    private val viewModel by viewModels<RouteSettingsActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toolbar = findViewById<ComposeView>(R.id.toolbar)
        toolbar.setContent {
            @Suppress("DEPRECATION")
            Mdc3Theme {
                TopAppBar(
                    title = { Text(stringResource(R.string.menu_route)) },
                    navigationIcon = {
                        SimpleIconButton(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                        ) {
                            onBackPressedDispatcher.onBackPressed()
                        }
                    },
                    actions = {
                        SimpleIconButton(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete)
                        ) {
                            MaterialAlertDialogBuilder(this@RouteSettingsActivity)
                                .setTitle(R.string.delete_route_prompt)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    viewModel.deleteRule()
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                        SimpleIconButton(
                            imageVector = Icons.Filled.Done,
                            contentDescription = stringResource(R.string.apply),
                        ) {
                            viewModel.saveAndExit {
                                if (intent.hasExtra(EXTRA_PACKAGE_NAME)) {
                                    setResult(RESULT_OK, Intent())
                                }
                            }
                        }
                    },
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dirty.collect {
                    onBackPressedCallback.isEnabled = it
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect(::handleUiEvent)
            }
        }

        val editingId = intent.getLongExtra(EXTRA_ROUTE_ID, 0L)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        viewModel.loadRule(editingId, packageName)
    }

    private fun handleUiEvent(event: RouteSettingsActivityUiEvent) {
        when (event) {
            RouteSettingsActivityUiEvent.Finish -> finish()

            RouteSettingsActivityUiEvent.EmptyRouteDialog -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.empty_route)
                    .setMessage(R.string.empty_route_notice)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }

            is RouteSettingsActivityUiEvent.RuleLoaded -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings, MyPreferenceFragmentCompat())
                    .commit()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    class MyPreferenceFragmentCompat : MaterialPreferenceFragment() {

        private val selectProfileForAdd = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { (resultCode, data) ->
            if (resultCode == RESULT_OK) lifecycleScope.launch {
                val profile = ProfileManager.getProfile(
                    data!!.getLongExtra(
                        ProfileSelectActivity.EXTRA_PROFILE_ID, 0
                    )
                ) ?: return@launch
                DataStore.routeOutboundRule = profile.id
                onMainDispatcher {
                    outbound.value = OUTBOUND_POSITION
                    outbound.setSummaryForOutbound()
                }
            }
        }

        private val selectAppList = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            apps.postUpdate()
        }

        private lateinit var apps: AppListPreference
        private lateinit var outbound: SimpleMenuPreference

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.profileCacheStore
            addPreferencesFromResource(R.xml.route_preferences)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                v.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
                insets
            }

            try {
                setupPreferences()
            } catch (e: Exception) {
                Toast.makeText(
                    app,
                    "Error on createPreferences, please try again.",
                    Toast.LENGTH_SHORT,
                ).show()
                Logs.e(e)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun setupPreferences() {
            val action: SimpleMenuPreference = findPreference(Key.ROUTE_ACTION)!!
            outbound = findPreference(Key.ROUTE_OUTBOUND)!!
            apps = findPreference(Key.ROUTE_PACKAGES)!!
            val networkType: MultiSelectListPreference = findPreference(Key.ROUTE_NETWORK_TYPE)!!
            val ssid: EditTextPreference = findPreference(Key.ROUTE_SSID)!!
            val bssid: EditTextPreference = findPreference(Key.ROUTE_BSSID)!!
            findPreference<EditTextPreference>(Key.ROUTE_OVERRIDE_PORT)!!.apply {
                setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
            }
            val tlsFragment: MaterialSwitchPreference = findPreference(Key.ROUTE_TLS_FRAGMENT)!!
            val tlsFragmentFallbackDelay: DurationPreference =
                findPreference(Key.ROUTE_TLS_FRAGMENT_FALLBACK_DELAY)!!

            fun updateTlsFragment(enableTlsFragment: Boolean = tlsFragment.isChecked) {
                tlsFragmentFallbackDelay.isEnabled = enableTlsFragment
            }
            updateTlsFragment()
            tlsFragment.setOnPreferenceChangeListener { _, newValue ->
                updateTlsFragment(newValue as Boolean)
                true
            }

            findPreference<EditTextPreference>(Key.ROUTE_RESOLVE_REWRITE_TTL)!!.apply {
                setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
            }

            val actionRoute: PreferenceCategory = findPreference(Key.ROUTE_ACTION_ROUTE)!!
            val actionRouteOptions: PreferenceCategory =
                findPreference(Key.ROUTE_ACTION_ROUTE_OPTIONS)!!
            val actionResolve: PreferenceCategory =
                findPreference(Key.ROUTE_ACTION_RESOLVE_OPTIONS)!!
            val actionSniff: PreferenceCategory = findPreference(Key.ROUTE_ACTION_SNIFF_OPTIONS)!!

            outbound.setSummaryForOutbound()
            outbound.launchOnPosition(OUTBOUND_POSITION) {
                selectProfileForAdd.launch(
                    Intent(requireContext(), ProfileSelectActivity::class.java)
                )
            }

            apps.setOnPreferenceClickListener {
                selectAppList.launch(
                    Intent(requireContext(), AppListActivity::class.java)
                )
                true
            }

            fun updateNetwork(newValue: Set<String> = networkType.values) {
                val visible = newValue.contains(NETWORK_TYPE_WIFI)
                ssid.isVisible = visible
                bssid.isVisible = visible
            }
            updateNetwork()
            networkType.setOnPreferenceChangeListener { _, newValue ->
                updateNetwork(newValue as Set<String>)
                true
            }

            fun updateAction(newValue: String = action.value) {
                when (newValue) {
                    "", SingBoxOptions.ACTION_ROUTE -> {
                        actionRoute.isVisible = true
                        actionRouteOptions.isVisible = false
                        actionResolve.isVisible = false
                        actionSniff.isVisible = false
                    }

                    SingBoxOptions.ACTION_ROUTE_OPTIONS -> {
                        actionRoute.isVisible = false
                        actionRouteOptions.isVisible = true
                        actionResolve.isVisible = false
                        actionSniff.isVisible = false
                    }

                    SingBoxOptions.ACTION_RESOLVE -> {
                        actionRoute.isVisible = false
                        actionRouteOptions.isVisible = false
                        actionResolve.isVisible = true
                        actionSniff.isVisible = false
                    }

                    SingBoxOptions.ACTION_SNIFF -> {
                        actionRoute.isVisible = false
                        actionRouteOptions.isVisible = false
                        actionResolve.isVisible = false
                        actionSniff.isVisible = true
                    }

                    else -> {
                        actionRoute.isVisible = false
                        actionRouteOptions.isVisible = false
                        actionResolve.isVisible = false
                        actionSniff.isVisible = false
                    }
                }
            }
            updateAction()
            action.setOnPreferenceChangeListener { _, newValue ->
                updateAction(newValue.toString())
                true
            }
        }
    }
}
