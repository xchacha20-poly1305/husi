package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.os.LocaleListCompat
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import androidx.preference.forEach
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.DEFAULT_HTTP_BYPASS
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.RuleProvider
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.isExpert
import io.nekohasekai.sagernet.ktx.needReload
import io.nekohasekai.sagernet.ktx.needRestart
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.DurationPreference
import io.nekohasekai.sagernet.widget.LinkOrContentPreference
import io.nekohasekai.sagernet.widget.updateSummary
import moe.matsuri.nb4a.ui.ColorPickerPreference
import moe.matsuri.nb4a.ui.LongClickListPreference
import rikka.preference.SimpleMenuPreference
import java.util.Locale

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
        lateinit var ntpInterval: DurationPreference

        lateinit var bypassLan: SwitchPreference
        lateinit var bypassLanInCore: SwitchPreference
        lateinit var enableSniff: SwitchPreference
        lateinit var sniffTimeout: DurationPreference

        lateinit var logLevel: LongClickListPreference
        lateinit var alwaysShowAddress: SwitchPreference
        lateinit var blurredAddress: SwitchPreference

        lateinit var profileTrafficStatistics: SwitchPreference
        lateinit var speedInterval: SimpleMenuPreference
        lateinit var showDirectSpeed: SwitchPreference

        lateinit var ruleProvider: SimpleMenuPreference
        lateinit var customRuleProvider: EditTextPreference

        lateinit var appendHttpProxy: SwitchPreference
        lateinit var httpProxyBypass: EditTextPreference

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

                        Key.APP_LANGUAGE -> {
                            fun getLanguageDisplayName(code: String): String = when (code) {
                                "" -> getString(R.string.language_system_default)
                                "ar" -> getString(R.string.language_ar_display_name)
                                "en-US" -> getString(R.string.language_en_display_name)
                                "es" -> getString(R.string.language_es_display_name)
                                "fa" -> getString(R.string.language_fa_display_name)
                                "ru" -> getString(R.string.language_ru_display_name)
                                "zh-Hans-CN" -> getString(R.string.language_zh_Hans_CN_display_name)
                                "zh-Hant-TW" -> getString(R.string.language_zh_Hant_TW_display_name)
                                "zh-Hant-HK" -> getString(R.string.language_zh_Hant_HK_display_name)
                                // just a fallback name from Java
                                else -> Locale.forLanguageTag(code).displayName
                            }

                            val appLanguage = preference as SimpleMenuPreference
                            val locale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                            appLanguage.summary = getLanguageDisplayName(locale)
                            appLanguage.value =
                                if (locale in resources.getStringArray(R.array.language_value)) {
                                    locale
                                } else {
                                    ""
                                }
                            appLanguage.setOnPreferenceChangeListener { _, newValue ->
                                newValue as String
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(newValue)
                                )
                                appLanguage.summary = getLanguageDisplayName(newValue)
                                appLanguage.value = newValue
                                true
                            }
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

                        Key.MEMORY_LIMIT -> preference.onPreferenceChangeListener = restartListener

                        Key.LOG_LEVEL -> logLevel = preference as LongClickListPreference

                        Key.ALWAYS_SHOW_ADDRESS -> {
                            alwaysShowAddress = preference as SwitchPreference
                        }

                        Key.BLURRED_ADDRESS -> blurredAddress = preference as SwitchPreference

                        Key.PROFILE_TRAFFIC_STATISTICS -> profileTrafficStatistics =
                            preference as SwitchPreference

                        Key.SPEED_INTERVAL -> speedInterval = preference as SimpleMenuPreference

                        Key.SHOW_DIRECT_SPEED -> showDirectSpeed = preference as SwitchPreference

                        Key.DEBUG_LISTEN -> {
                            preference.isVisible = isExpert
                            preference.onPreferenceChangeListener = reloadListener
                        }

                        Key.MTU -> {
                            preference as EditTextPreference
                            preference.onPreferenceChangeListener = reloadListener
                            preference.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
                        }

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
                        Key.NETWORK_PREFERRED_INTERFACES -> (preference as MultiSelectListPreference).let {
                            it.updateSummary()
                            it.setOnPreferenceChangeListener { _, newValue ->
                                @Suppress("UNCHECKED_CAST")
                                it.updateSummary(newValue as Set<String>)
                                needReload()
                                true
                            }
                        }

                        Key.RULES_PROVIDER -> ruleProvider = preference as SimpleMenuPreference
                        Key.CUSTOM_RULE_PROVIDER -> {
                            customRuleProvider = (preference as LinkOrContentPreference).also {
                                it.allowMultipleLines = true
                                it.isVisible = DataStore.rulesProvider == RuleProvider.CUSTOM
                            }
                        }

                        Key.ENABLE_SNIFF -> enableSniff = preference as SwitchPreference
                        Key.SNIFF_TIMEOUT -> sniffTimeout = preference as DurationPreference

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
                        Key.ANCHOR_SSID -> {
                            preference.isVisible = isExpert
                            preference.onPreferenceChangeListener = reloadListener
                        }

                        Key.APPEND_HTTP_PROXY -> appendHttpProxy = preference as SwitchPreference
                        Key.HTTP_PROXY_BYPASS -> httpProxyBypass = preference as EditTextPreference

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
                        Key.SHOW_BOTTOM_BAR -> Unit

                        Key.CERT_PROVIDER -> preference.onPreferenceChangeListener = restartListener

                        else -> preference.onPreferenceChangeListener = reloadListener
                    }
                }

                Key.NTP_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
                        Key.ENABLE_NTP -> ntpEnable = preference as SwitchPreference
                        Key.NTP_SERVER -> ntpAddress = preference as EditTextPreference
                        Key.NTP_PORT -> ntpPort = preference as EditTextPreference
                        Key.NTP_INTERVAL -> ntpInterval = preference as DurationPreference
                    }
                }

                else -> preferenceCategory.forEach { preference ->
                    preference.onPreferenceChangeListener = reloadListener
                }

            }
        }

        ntpEnable.isChecked.let {
            ntpAddress.isEnabled = it
            ntpPort.isEnabled = it
            ntpInterval.isEnabled = it
        }
        ntpAddress.onPreferenceChangeListener = reloadListener
        ntpPort.onPreferenceChangeListener = reloadListener
        ntpPort.setPortEdit()
        ntpInterval.onPreferenceChangeListener = reloadListener
        ntpEnable.setOnPreferenceChangeListener { _, newValue ->
            needReload()
            newValue as Boolean
            ntpAddress.isEnabled = newValue
            ntpPort.isEnabled = newValue
            ntpInterval.isEnabled = newValue
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

        profileTrafficStatistics.isEnabled = speedInterval.value != "0"
        showDirectSpeed.isEnabled = speedInterval.value != "0"
        speedInterval.setOnPreferenceChangeListener { _, newValue ->
            newValue as String
            profileTrafficStatistics.isEnabled = newValue != "0"
            showDirectSpeed.isEnabled = newValue != "0"
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

        sniffTimeout.isEnabled = enableSniff.isChecked
        sniffTimeout.onPreferenceChangeListener = reloadListener
        enableSniff.setOnPreferenceChangeListener { _, newValue ->
            sniffTimeout.isEnabled = newValue as Boolean
            needReload()
            true
        }

        blurredAddress.isEnabled = alwaysShowAddress.isChecked
        alwaysShowAddress.setOnPreferenceChangeListener { _, newValue ->
            blurredAddress.isEnabled = newValue as Boolean
            true
        }

        ruleProvider.setOnPreferenceChangeListener { _, newValue ->
            customRuleProvider.isVisible = (newValue as String) == RuleProvider.CUSTOM.toString()
            true
        }

        httpProxyBypass.apply {
            isEnabled = appendHttpProxy.isChecked
            onPreferenceChangeListener = reloadListener

            // I Don't want to set a long default value in xml,
            // but set default value here can't show default value in editor.
            // If you don't want to set bypass, just set a "#".
            setOnBindEditTextListener {
                val txt = it.text
                if (txt.isNullOrBlank()) {
                    it.setText(DEFAULT_HTTP_BYPASS)
                }
            }
        }
        appendHttpProxy.setOnPreferenceChangeListener { _, newValue ->
            httpProxyBypass.isEnabled = newValue as Boolean
            needReload()
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
        @Suppress("UsePropertyAccessSyntax") // Android studio bug
        setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        onPreferenceChangeListener = reloadListener
    }

}