package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.app.ActivityCompat
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.AppListPreference
import moe.matsuri.nb4a.Protocols
import moe.matsuri.nb4a.ui.ColorPickerPreference
import moe.matsuri.nb4a.ui.LongClickListPreference
import moe.matsuri.nb4a.ui.SimpleMenuPreference

class SettingsPreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var isProxyApps: SwitchPreference
    private lateinit var nekoPlugins: AppListPreference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView.layoutManager = FixedLinearLayoutManager(listView)
    }

    private val reloadListener = Preference.OnPreferenceChangeListener { _, _ ->
        needReload()
        true
    }

    private val restartListener = Preference.OnPreferenceChangeListener { _, _ ->
        needRestart()
        true
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore
        DataStore.initGlobal()
        addPreferencesFromResource(R.xml.global_preferences)

        val globalPreferences = findPreference<PreferenceScreen>(Key.GLOBAL_PREFERENCES)!!
        globalPreferences.forEach { preferenceCategory ->
            preferenceCategory as PreferenceCategory
            when (preferenceCategory.key) {
                Key.GENERAL_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
                        Key.NEKO_PLUGIN_MANAGED -> {
                            DataStore.routePackages = DataStore.nekoPlugins
                            preference.setOnPreferenceClickListener {
                                // borrow from route app settings
                                startActivity(Intent(
                                    context, AppListActivity::class.java
                                ).apply { putExtra(Key.NEKO_PLUGIN_MANAGED, true) })
                                true
                            }

                        }

                        Key.APP_THEME -> {
                            preference as ColorPickerPreference
                            preference.setOnPreferenceChangeListener { _, newTheme ->
                                if (DataStore.serviceState.started) {
                                    SagerNet.reloadService()
                                }
                                val theme = Theme.getTheme(newTheme as Int)
                                app.setTheme(theme)
                                requireActivity().apply {
                                    setTheme(theme)
                                    ActivityCompat.recreate(this)
                                }
                                true
                            }
                        }


                        Key.NIGHT_THEME -> preference.setOnPreferenceChangeListener { _, newTheme ->
                            Theme.currentNightMode = (newTheme as String).toInt()
                            Theme.applyNightTheme()
                            true
                        }

                        Key.METERED_NETWORK -> if (Build.VERSION.SDK_INT < 28) {
                            preference.remove()
                        }

                        Key.SERVICE_MODE -> preference.setOnPreferenceChangeListener { _, _ ->
                            if (DataStore.serviceState.started) SagerNet.stopService()
                            true
                        }

                        Key.MEMORY_LIMIT -> {
                            preference.onPreferenceChangeListener = restartListener
                        }


                        Key.PROXY_APPS -> {
                            isProxyApps = preference as SwitchPreference
                            isProxyApps.setOnPreferenceChangeListener { _, newValue ->
                                startActivity(Intent(activity, AppManagerActivity::class.java))
                                if (newValue as Boolean) DataStore.dirty = true
                                newValue
                            }
                        }

                        else -> preference.onPreferenceChangeListener = reloadListener
                    }
                }

                Key.PROTOCOL_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
                        Key.MUX_PROTOCOLS -> {
                            preference as MultiSelectListPreference
                            preference.apply {
                                val e = Protocols.getCanMuxList().toTypedArray()
                                entries = e
                                entryValues = e
                            }
                        }

                        Key.MUX_CONCURRENCY, Key.UPLOAD_SPEED, Key.DOWNLOAD_SPEED -> (preference as EditTextPreference).setPortEdit()

                        else -> preference.onPreferenceChangeListener = reloadListener
                    }
                }

                Key.INBOUND_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
                        Key.MIXED_PORT, Key.LOCAL_DNS_PORT -> (preference as EditTextPreference).setPortEdit()
                        else -> preference.onPreferenceChangeListener = reloadListener
                    }
                }

                Key.MISC_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
                        Key.TCP_KEEP_ALIVE_INTERVAL -> {
                            preference.onPreferenceChangeListener = reloadListener
                            preference.isVisible = false
                        }

                        else -> preference.onPreferenceChangeListener = reloadListener
                    }
                }

                else -> preferenceCategory.forEach { preference ->
                    preference.onPreferenceChangeListener = reloadListener
                }

            }
        }

        val ntpEnable = findPreference<SwitchPreference>(Key.ENABLE_NTP)!!
        val ntpAddress = findPreference<EditTextPreference>(Key.NTP_SERVER)!!
        val ntpPort = findPreference<EditTextPreference>(Key.NTP_PORT)!!
        val ntpInterval = findPreference<SimpleMenuPreference>(Key.NTP_INTERVAL)!!
        ntpAddress.isEnabled = ntpEnable.isChecked
        ntpPort.isEnabled = ntpEnable.isChecked
        ntpInterval.isEnabled = ntpEnable.isChecked
        ntpAddress.onPreferenceChangeListener = reloadListener
        ntpPort.onPreferenceChangeListener = reloadListener
        ntpInterval.onPreferenceChangeListener = reloadListener
        ntpEnable.setOnPreferenceChangeListener { _, newValue ->
            needReload()
            if (newValue as Boolean) {
                ntpAddress.isEnabled = true
                ntpPort.isEnabled = true
                ntpInterval.isEnabled = true
            } else {
                ntpAddress.isEnabled = false
                ntpPort.isEnabled = false
                ntpInterval.isEnabled = false
            }
            true
        }

        val bypassLan = findPreference<SwitchPreference>(Key.BYPASS_LAN)!!
        val bypassLanInCore = findPreference<SwitchPreference>(Key.BYPASS_LAN_IN_CORE)!!

        val logLevel = findPreference<LongClickListPreference>(Key.LOG_LEVEL)!!
        val alwaysShowAddress = findPreference<SwitchPreference>(Key.ALWAYS_SHOW_ADDRESS)!!
        val blurredAddress = findPreference<SwitchPreference>(Key.BLURRED_ADDRESS)!!

        logLevel.dialogLayoutResource = R.layout.layout_loglevel_help
        logLevel.onPreferenceChangeListener = restartListener
        logLevel.setOnLongClickListener {
            if (context == null) return@setOnLongClickListener true

            val view = EditText(context).apply {
                inputType = EditorInfo.TYPE_CLASS_NUMBER
                var size = DataStore.logBufSize
                if (size == 0) size = 50
                setText(size.toString())
            }

            MaterialAlertDialogBuilder(requireContext()).setTitle("Log buffer size (kb)")
                .setView(view)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    DataStore.logBufSize = view.text.toString().toInt()
                    if (DataStore.logBufSize <= 0) DataStore.logBufSize = 50
                    needRestart()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        val profileTrafficStatistics =
            findPreference<SwitchPreference>(Key.PROFILE_TRAFFIC_STATISTICS)!!
        val speedInterval = findPreference<SimpleMenuPreference>(Key.SPEED_INTERVAL)!!
        profileTrafficStatistics.isEnabled = speedInterval.value.toString() != "0"
        val showDirectSpeed = findPreference<SwitchPreference>(Key.SHOW_DIRECT_SPEED)!!
        showDirectSpeed.isEnabled = speedInterval.value.toString() != "0"
        speedInterval.setOnPreferenceChangeListener { _, newValue ->
            profileTrafficStatistics.isEnabled = newValue.toString() != "0"
            showDirectSpeed.isEnabled = newValue.toString() != "0"
            needReload()
            true
        }
        showDirectSpeed.onPreferenceChangeListener = reloadListener

        bypassLanInCore.isEnabled = bypassLan.isChecked
        bypassLanInCore.onPreferenceChangeListener = reloadListener
        bypassLan.setOnPreferenceChangeListener { _, newValue ->
            bypassLanInCore.isEnabled = newValue as Boolean
            needReload()
            true
        }

        blurredAddress.isEnabled = alwaysShowAddress.isChecked
        alwaysShowAddress.setOnPreferenceChangeListener { _, newValue ->
            blurredAddress.isEnabled = newValue as Boolean
            true
        }

    }

    override fun onResume() {
        super.onResume()

        if (::isProxyApps.isInitialized) {
            isProxyApps.isChecked = DataStore.proxyApps
        }
        if (::nekoPlugins.isInitialized) {
            nekoPlugins.postUpdate()
        }
    }

    private fun EditTextPreference.setPortEdit() {
        setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        onPreferenceChangeListener = reloadListener
    }

}