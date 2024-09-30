package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.app.ActivityCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import androidx.preference.forEach
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.RuleProvider
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.needReload
import io.nekohasekai.sagernet.ktx.needRestart
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.LinkOrContentPreference
import moe.matsuri.nb4a.ui.ColorPickerPreference
import moe.matsuri.nb4a.ui.LongClickListPreference
import moe.matsuri.nb4a.ui.SimpleMenuPreference

class SettingsPreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var isProxyApps: SwitchPreference

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


        lateinit var ntpEnable: SwitchPreference
        lateinit var ntpAddress: EditTextPreference
        lateinit var ntpPort: EditTextPreference
        lateinit var ntpInterval: SimpleMenuPreference

        lateinit var bypassLan: SwitchPreference
        lateinit var bypassLanInCore: SwitchPreference

        lateinit var logLevel: LongClickListPreference
        lateinit var alwaysShowAddress: SwitchPreference
        lateinit var blurredAddress: SwitchPreference

        lateinit var profileTrafficStatistics: SwitchPreference
        lateinit var speedInterval: SimpleMenuPreference
        lateinit var showDirectSpeed: SwitchPreference

        lateinit var ruleProvider: SimpleMenuPreference
        lateinit var customRuleProvider: EditTextPreference


        preferenceManager.sharedPreferences
        preferenceScreen.forEach { preferenceCategory ->
            preferenceCategory as PreferenceCategory
            when (preferenceCategory.key) {
                Key.GENERAL_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
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
                            preference.isVisible = false
                        } else {
                            preference.onPreferenceChangeListener = reloadListener
                        }

                        Key.SERVICE_MODE -> preference.setOnPreferenceChangeListener { _, _ ->
                            if (DataStore.serviceState.started) SagerNet.stopService()
                            true
                        }

                        Key.MEMORY_LIMIT -> {
                            preference.onPreferenceChangeListener = restartListener
                        }

                        Key.LOG_LEVEL -> logLevel = preference as LongClickListPreference

                        Key.ALWAYS_SHOW_ADDRESS -> alwaysShowAddress =
                            preference as SwitchPreference

                        Key.BLURRED_ADDRESS -> blurredAddress = preference as SwitchPreference

                        Key.PROFILE_TRAFFIC_STATISTICS -> profileTrafficStatistics =
                            preference as SwitchPreference

                        Key.SPEED_INTERVAL -> speedInterval = preference as SimpleMenuPreference

                        Key.SHOW_DIRECT_SPEED -> showDirectSpeed = preference as SwitchPreference

                        Key.PERSIST_ACROSS_REBOOT, Key.SECURITY_ADVISORY -> {}
                        else -> preference.onPreferenceChangeListener = reloadListener
                    }
                }

                Key.ROUTE_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
                        Key.PROXY_APPS -> {
                            isProxyApps = preference as SwitchPreference
                            isProxyApps.setOnPreferenceChangeListener { _, newValue ->
                                startActivity(Intent(activity, AppManagerActivity::class.java))
                                if (newValue as Boolean) DataStore.dirty = true
                                newValue
                            }
                        }

                        Key.BYPASS_LAN -> bypassLan = preference as SwitchPreference
                        Key.BYPASS_LAN_IN_CORE -> bypassLanInCore = preference as SwitchPreference
                        Key.RULES_PROVIDER -> ruleProvider = preference as SimpleMenuPreference
                        Key.CUSTOM_RULE_PROVIDER -> {
                            customRuleProvider = (preference as LinkOrContentPreference).also {
                                it.allowMultipleLines = true
                                it.isVisible = DataStore.rulesProvider == RuleProvider.CUSTOM
                            }
                        }

                        else -> preference.onPreferenceChangeListener = reloadListener
                    }
                }

                Key.PROTOCOL_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
                        Key.CUSTOM_PLUGIN_PREFIX -> {
                            preference.onPreferenceChangeListener = restartListener
                        }

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

                        Key.CONNECTION_TEST_URL, Key.APP_TLS_VERSION,
                        Key.SHOW_BOTTOM_BAR, Key.ALLOW_INSECURE_ON_REQUEST,
                        -> Unit

                        else -> preference.onPreferenceChangeListener = reloadListener
                    }
                }

                Key.NTP_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
                        Key.ENABLE_NTP -> ntpEnable = preference as SwitchPreference
                        Key.NTP_SERVER -> ntpAddress = preference as EditTextPreference
                        Key.NTP_PORT -> ntpPort = preference as EditTextPreference
                        Key.NTP_INTERVAL -> ntpInterval = preference as SimpleMenuPreference
                    }
                }

                else -> preferenceCategory.forEach { preference ->
                    preference.onPreferenceChangeListener = reloadListener
                }

            }
        }

        ntpAddress.isEnabled = ntpEnable.isChecked
        ntpPort.isEnabled = ntpEnable.isChecked
        ntpInterval.isEnabled = ntpEnable.isChecked
        ntpAddress.onPreferenceChangeListener = reloadListener
        ntpPort.onPreferenceChangeListener = reloadListener
        ntpPort.setPortEdit()
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

        profileTrafficStatistics.isEnabled = speedInterval.value.toString() != "0"
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

        ruleProvider.setOnPreferenceChangeListener { _, newValue ->
            customRuleProvider.isVisible = (newValue as String).toInt() == RuleProvider.CUSTOM
            true
        }

    }

    override fun onResume() {
        super.onResume()

        if (::isProxyApps.isInitialized) {
            isProxyApps.isChecked = DataStore.proxyApps
        }
    }

    private fun EditTextPreference.setPortEdit() {
        setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        onPreferenceChangeListener = reloadListener
    }

}