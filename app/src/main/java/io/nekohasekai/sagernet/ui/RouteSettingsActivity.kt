package io.nekohasekai.sagernet.ui

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.github.shadowsocks.plugin.Empty
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet.Companion.app
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.fmt.SingBoxOptions.ACTION_ROUTE
import io.nekohasekai.sagernet.fmt.SingBoxOptions.ACTION_ROUTE_OPTIONS
import io.nekohasekai.sagernet.fmt.SingBoxOptions.ACTION_RESOLVE
import io.nekohasekai.sagernet.fmt.SingBoxOptions.ACTION_SNIFF
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.configuration.ProfileSelectActivity
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.widget.AppListPreference
import io.nekohasekai.sagernet.widget.DurationPreference
import io.nekohasekai.sagernet.widget.setOutbound
import io.nekohasekai.sagernet.widget.updateOutboundSummary
import io.nekohasekai.sagernet.widget.updateSummary
import kotlinx.parcelize.Parcelize
import rikka.preference.SimpleMenuPreference

class RouteSettingsActivity(
    @LayoutRes resId: Int = R.layout.layout_settings_activity,
) : ThemedActivity(resId),
    OnPreferenceDataStoreChangeListener {

    override val onBackPressedCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            UnsavedChangesDialogFragment().apply {
                key()
            }.show(supportFragmentManager, null)
        }
    }

    fun init(packageName: String?) {
        RuleEntity().apply {
            if (!packageName.isNullOrBlank()) {
                packages = setOf(packageName)
                name = app.getString(R.string.route_for, PackageCache.loadLabel(packageName))
            }
        }.init()
    }

    fun RuleEntity.init() {
        DataStore.routeName = name
        DataStore.routeAction = action

        DataStore.routeDomain = domains
        DataStore.routeIP = ip
        DataStore.routePort = port
        DataStore.routeSourcePort = sourcePort
        DataStore.routeNetwork = network
        DataStore.routeSource = source
        DataStore.routeProtocol = protocol
        DataStore.routeOutboundRule = outbound
        DataStore.routeSSID = ssid
        DataStore.routeBSSID = bssid
        DataStore.routeClient = clientType
        DataStore.routeClashMode = clashMode
        DataStore.routeNetworkType = networkType
        DataStore.routeNetworkIsExpensive = networkIsExpensive

        DataStore.routeOverrideAddress = overrideAddress
        DataStore.routeOverridePort = overridePort
        DataStore.routeTlsFragment = tlsFragment
        DataStore.routeTlsRecordFragment = tlsRecordFragment
        DataStore.routeTlsFragmentFallbackDelay = tlsFragmentFallbackDelay

        DataStore.routeResolveStrategy = resolveStrategy
        DataStore.routeResolveDisableCache = resolveDisableCache
        DataStore.routeResolveRewriteTTL = resolveRewriteTTL
        DataStore.routeResolveClientSubnet = resolveClientSubnet

        DataStore.routeSniffTimeout = sniffTimeout
        DataStore.routeSniffers = sniffers

        DataStore.routeOutbound = when (outbound) {
            RuleEntity.OUTBOUND_PROXY -> 0
            RuleEntity.OUTBOUND_DIRECT -> 1
            RuleEntity.OUTBOUND_BLOCK -> 2
            else -> 3
        }
        DataStore.routePackages = packages
    }

    fun RuleEntity.serialize() {
        name = DataStore.routeName
        action = DataStore.routeAction

        domains = DataStore.routeDomain
        ip = DataStore.routeIP
        port = DataStore.routePort
        sourcePort = DataStore.routeSourcePort
        network = DataStore.routeNetwork
        source = DataStore.routeSource
        protocol = DataStore.routeProtocol
        clientType = DataStore.routeClient
        ssid = DataStore.routeSSID
        bssid = DataStore.routeBSSID
        clashMode = DataStore.routeClashMode
        networkType = DataStore.routeNetworkType
        networkIsExpensive = DataStore.routeNetworkIsExpensive

        overrideAddress = DataStore.routeOverrideAddress
        overridePort = DataStore.routeOverridePort
        tlsFragment = DataStore.routeTlsFragment
        tlsRecordFragment = DataStore.routeTlsRecordFragment
        tlsFragmentFallbackDelay = DataStore.routeTlsFragmentFallbackDelay

        resolveStrategy = DataStore.routeResolveStrategy
        resolveDisableCache = DataStore.routeResolveDisableCache
        resolveRewriteTTL = DataStore.routeResolveRewriteTTL
        resolveClientSubnet = DataStore.routeResolveClientSubnet

        sniffTimeout = DataStore.routeSniffTimeout
        sniffers = DataStore.routeSniffers

        outbound = when (DataStore.routeOutbound) {
            0 -> RuleEntity.OUTBOUND_PROXY
            1 -> RuleEntity.OUTBOUND_DIRECT
            2 -> RuleEntity.OUTBOUND_BLOCK
            else -> DataStore.routeOutboundRule
        }
        packages = DataStore.routePackages.filterTo(hashSetOf()) { it.isNotBlank() }

        if (DataStore.editingId == 0L) {
            enabled = true
        }
    }

    fun needSave(): Boolean {
        if (!DataStore.dirty) return false
        if (DataStore.routePackages.isEmpty() &&
            DataStore.routeDomain.isBlank() &&
            DataStore.routeIP.isBlank() &&
            DataStore.routePort.isBlank() &&
            DataStore.routeSourcePort.isBlank() &&
            DataStore.routeNetwork.isBlank() &&
            DataStore.routeSource.isBlank() &&
            DataStore.routeProtocol.isEmpty() &&
            DataStore.routeClient.isEmpty() &&
            DataStore.routeSSID.isBlank() &&
            DataStore.routeBSSID.isBlank() &&
            DataStore.routeClashMode.isBlank() &&
            DataStore.routeNetworkType.isEmpty() &&
            DataStore.routeNetworkIsExpensive &&
            DataStore.routeOutbound == 0 &&
            DataStore.routeOverrideAddress.isBlank() &&
            DataStore.routeOverridePort == 0 &&
            !DataStore.routeTlsFragment &&
            !DataStore.routeTlsRecordFragment &&
            DataStore.routeTlsFragmentFallbackDelay.isBlank() &&
            DataStore.routeResolveStrategy.isBlank() &&
            !DataStore.routeResolveDisableCache &&
            DataStore.routeResolveRewriteTTL >= 0 &&
            DataStore.routeResolveClientSubnet.isBlank() &&
            DataStore.routeSniffTimeout.isBlank() &&
            DataStore.routeSniffers.isEmpty()
        ) {
            return false
        }
        return true
    }

    fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.route_preferences)
    }

    val selectProfileForAdd = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { (resultCode, data) ->
        if (resultCode == RESULT_OK) runOnDefaultDispatcher {
            val profile = ProfileManager.getProfile(
                data!!.getLongExtra(
                    ProfileSelectActivity.EXTRA_PROFILE_ID, 0
                )
            ) ?: return@runOnDefaultDispatcher
            DataStore.routeOutboundRule = profile.id
            onMainDispatcher {
                outbound.value = OUTBOUND_POSITION
                outbound.updateOutboundSummary()
            }
        }
    }

    val selectAppList = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { (_, _) ->
        apps.postUpdate()
    }

    private lateinit var action: SimpleMenuPreference

    private lateinit var apps: AppListPreference
    private lateinit var outbound: SimpleMenuPreference
    private lateinit var networkType: MultiSelectListPreference
    private lateinit var ssid: EditTextPreference
    private lateinit var bssid: EditTextPreference
    private lateinit var protocol: MultiSelectListPreference
    private lateinit var clientType: MultiSelectListPreference
    private lateinit var overridePort: EditTextPreference
    private lateinit var sniffers: MultiSelectListPreference
    private lateinit var tlsFragment: SwitchPreference
    private lateinit var tlsFragmentFallbackDelay: DurationPreference

    private lateinit var actionRoute: PreferenceCategory
    private lateinit var actionRouteOptions: PreferenceCategory
    private lateinit var actionResolve: PreferenceCategory
    private lateinit var actionSniff: PreferenceCategory

    @Suppress("UNCHECKED_CAST")
    fun PreferenceFragmentCompat.viewCreated(view: View, savedInstanceState: Bundle?) {
        action = findPreference(Key.ROUTE_ACTION)!!
        outbound = findPreference(Key.ROUTE_OUTBOUND)!!
        apps = findPreference(Key.ROUTE_PACKAGES)!!
        networkType = findPreference(Key.ROUTE_NETWORK_TYPE)!!
        ssid = findPreference(Key.ROUTE_SSID)!!
        bssid = findPreference(Key.ROUTE_BSSID)!!
        protocol = findPreference(Key.ROUTE_PROTOCOL)!!
        clientType = findPreference(Key.ROUTE_CLIENT)!!
        overridePort = findPreference<EditTextPreference>(Key.ROUTE_OVERRIDE_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        sniffers = findPreference(Key.ROUTE_SNIFFERS)!!

        tlsFragment = findPreference(Key.ROUTE_TLS_FRAGMENT)!!
        tlsFragmentFallbackDelay = findPreference(Key.ROUTE_TLS_FRAGMENT_FALLBACK_DELAY)!!
        fun updateTlsFragment(enableTlsFragment: Boolean = tlsFragment.isChecked) {
            tlsFragmentFallbackDelay.isEnabled = enableTlsFragment
        }
        updateTlsFragment()
        tlsFragment.setOnPreferenceChangeListener { _, newValue ->
            updateTlsFragment(newValue as Boolean)
            true
        }

        actionRoute = findPreference(Key.ROUTE_ACTION_ROUTE)!!
        actionRouteOptions = findPreference(Key.ROUTE_ACTION_ROUTE_OPTIONS)!!
        actionResolve = findPreference(Key.ROUTE_ACTION_RESOLVE_OPTIONS)!!
        actionSniff = findPreference(Key.ROUTE_ACTION_SNIFF_OPTIONS)!!

        outbound.updateOutboundSummary()
        outbound.setOutbound(OUTBOUND_POSITION) {
            selectProfileForAdd.launch(
                Intent(this@RouteSettingsActivity, ProfileSelectActivity::class.java)
            )
        }

        apps.setOnPreferenceClickListener {
            selectAppList.launch(
                Intent(this@RouteSettingsActivity, AppListActivity::class.java)
            )
            true
        }

        fun updateNetwork(newValue: Set<String> = networkType.values) {
            networkType.updateSummary(newValue)
            val visible = newValue.contains(NETWORK_TYPE_WIFI)
            ssid.isVisible = visible
            bssid.isVisible = visible
        }
        updateNetwork()
        networkType.setOnPreferenceChangeListener { _, newValue ->
            updateNetwork(newValue as Set<String>)
            true
        }

        protocol.updateSummary()
        protocol.setOnPreferenceChangeListener { _, newValue ->
            protocol.updateSummary(newValue as Set<String>)
            true
        }
        clientType.updateSummary()
        clientType.setOnPreferenceChangeListener { _, newValue ->
            clientType.updateSummary(newValue as Set<String>)
            true
        }
        sniffers.updateSummary()
        sniffers.setOnPreferenceChangeListener { _, newValue ->
            sniffers.updateSummary(newValue as Set<String>)
            true
        }

        fun updateAction(newValue: String = action.value) {
            when (newValue) {
                "", ACTION_ROUTE -> {
                    actionRoute.isVisible = true
                    actionRouteOptions.isVisible = false
                    actionResolve.isVisible = false
                    actionSniff.isVisible = false
                }

                ACTION_ROUTE_OPTIONS -> {
                    actionRoute.isVisible = false
                    actionRouteOptions.isVisible = true
                    actionResolve.isVisible = false
                    actionSniff.isVisible = false
                }

                ACTION_RESOLVE -> {
                    actionRoute.isVisible = false
                    actionRouteOptions.isVisible = false
                    actionResolve.isVisible = true
                    actionSniff.isVisible = false
                }

                ACTION_SNIFF -> {
                    actionRoute.isVisible = false
                    actionRouteOptions.isVisible = false
                    actionResolve.isVisible = false
                    actionSniff.isVisible = true
                }

                // ACTION_HIJACK_DNS ->
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

    fun PreferenceFragmentCompat.displayPreferenceDialog(preference: Preference): Boolean {
        return false
    }

    class UnsavedChangesDialogFragment : AlertDialogFragment<Empty, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.unsaved_changes_prompt)
            setPositiveButton(android.R.string.ok) { _, _ ->
                runOnDefaultDispatcher {
                    (requireActivity() as RouteSettingsActivity).saveAndExit()
                }
            }
            setNegativeButton(R.string.no) { _, _ ->
                requireActivity().finish()
            }
            setNeutralButton(android.R.string.cancel, null)
        }
    }

    @Parcelize
    data class ProfileIdArg(val ruleId: Long) : Parcelable
    class DeleteConfirmationDialogFragment : AlertDialogFragment<ProfileIdArg, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.delete_route_prompt)
            setPositiveButton(android.R.string.ok) { _, _ ->
                runOnDefaultDispatcher {
                    ProfileManager.deleteRule(arg.ruleId)
                }
                requireActivity().finish()
            }
            setNegativeButton(android.R.string.cancel, null)
        }
    }

    companion object {
        const val EXTRA_ROUTE_ID = "id"
        const val EXTRA_PACKAGE_NAME = "pkg"

        const val NETWORK_TYPE_WIFI = "wifi"

        private const val OUTBOUND_POSITION = "3"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar)) { v, insets ->
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
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.menu_route)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        if (savedInstanceState == null) {
            val editingId = intent.getLongExtra(EXTRA_ROUTE_ID, 0L)
            DataStore.editingId = editingId
            runOnDefaultDispatcher {
                if (editingId == 0L) {
                    init(intent.getStringExtra(EXTRA_PACKAGE_NAME))
                } else {
                    val ruleEntity = SagerDatabase.rulesDao.getById(editingId)
                    if (ruleEntity == null) {
                        onMainDispatcher {
                            finish()
                        }
                        return@runOnDefaultDispatcher
                    }
                    ruleEntity.init()
                }

                onMainDispatcher {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.settings, MyPreferenceFragmentCompat())
                        .commit()

                    DataStore.dirty = false
                    DataStore.profileCacheStore.registerChangeListener(this@RouteSettingsActivity)
                }
            }

        }

    }

    suspend fun saveAndExit() {

        if (!needSave()) {
            onMainDispatcher {
                MaterialAlertDialogBuilder(this@RouteSettingsActivity).setTitle(R.string.empty_route)
                    .setMessage(R.string.empty_route_notice)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            return
        }

        val editingId = DataStore.editingId
        if (editingId == 0L) {
            if (intent.hasExtra(EXTRA_PACKAGE_NAME)) {
                setResult(RESULT_OK, Intent())
            }

            ProfileManager.createRule(RuleEntity().apply { serialize() })
        } else {
            val entity = SagerDatabase.rulesDao.getById(DataStore.editingId)
            if (entity == null) {
                finish()
                return
            }
            ProfileManager.updateRule(entity.apply { serialize() })
        }
        finish()

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.profile_config_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_delete -> {
            if (DataStore.editingId == 0L) {
                finish()
            } else {
                DeleteConfirmationDialogFragment().apply {
                    arg(ProfileIdArg(DataStore.editingId))
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

    class MyPreferenceFragmentCompat : PreferenceFragmentCompat() {

        var activity: RouteSettingsActivity? = null

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.profileCacheStore
            try {
                activity = (requireActivity() as RouteSettingsActivity).apply {
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

            activity?.apply {
                viewCreated(view, savedInstanceState)
            }
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            activity?.apply {
                if (displayPreferenceDialog(preference)) return
            }
            super.onDisplayPreferenceDialog(preference)
        }

    }

    object PasswordSummaryProvider : Preference.SummaryProvider<EditTextPreference> {

        override fun provideSummary(preference: EditTextPreference): CharSequence {
            val text = preference.text
            return if (text.isNullOrBlank()) {
                preference.context.getString(androidx.preference.R.string.not_set)
            } else {
                "\u2022".repeat(text.length)
            }
        }

    }

}