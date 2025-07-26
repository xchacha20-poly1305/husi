package io.nekohasekai.sagernet.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.forEach
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.DEFAULT_HTTP_BYPASS
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.RuleProvider
import io.nekohasekai.sagernet.SagerNet.Companion.app
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.isExpert
import io.nekohasekai.sagernet.ktx.needReload
import io.nekohasekai.sagernet.ktx.needRestart
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.ColorPickerPreference
import io.nekohasekai.sagernet.widget.DurationPreference
import io.nekohasekai.sagernet.widget.FixedLinearLayout
import io.nekohasekai.sagernet.widget.LinkOrContentPreference
import io.nekohasekai.sagernet.widget.MaterialSwitchPreference
import io.nekohasekai.sagernet.widget.updateSummary
import kotlinx.coroutines.launch
import rikka.preference.SimpleMenuPreference
import java.util.Locale

class SettingsPreferenceFragment : MaterialPreferenceFragment() {

    private val viewModel: SettingsPreferenceFragmentViewModel by viewModels()
    private lateinit var isProxyApps: MaterialSwitchPreference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView.layoutManager = FixedLinearLayout(listView.context, RecyclerView.VERTICAL, false)
        listView.setPadding(0, 0, 0, dp2px(64))
        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom + dp2px(64),
            )
            insets
        }

        if (viewModel.tryReceiveShouldRestart()) lifecycleScope.launch {
            onMainDispatcher {
                needRestart()
            }
        }
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
        viewModel.initDataStore()
        addPreferencesFromResource(R.xml.global_preferences)

        lateinit var ntpEnable: MaterialSwitchPreference
        lateinit var ntpAddress: EditTextPreference
        lateinit var ntpPort: EditTextPreference
        lateinit var ntpInterval: DurationPreference

        lateinit var bypassLan: MaterialSwitchPreference
        lateinit var bypassLanInCore: MaterialSwitchPreference

        lateinit var alwaysShowAddress: MaterialSwitchPreference
        lateinit var blurredAddress: MaterialSwitchPreference

        lateinit var profileTrafficStatistics: MaterialSwitchPreference
        lateinit var speedInterval: SimpleMenuPreference
        lateinit var showDirectSpeed: MaterialSwitchPreference

        lateinit var ruleProvider: SimpleMenuPreference
        lateinit var customRuleProvider: EditTextPreference

        lateinit var appendHttpProxy: MaterialSwitchPreference
        lateinit var httpProxyBypass: EditTextPreference

        preferenceScreen.forEach { preferenceCategory ->
            preferenceCategory as PreferenceCategory
            when (preferenceCategory.key) {
                Key.GENERAL_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
                        Key.APP_THEME -> {
                            preference as ColorPickerPreference
                            preference.setOnPreferenceChangeListener { _, newTheme ->
                                viewModel.tryReloadService()
                                val theme = Theme.getTheme(newTheme as Int)
                                app.setTheme(theme)
                                ActivityCompat.recreate(requireActivity())
                                true
                            }
                        }


                        Key.NIGHT_THEME -> preference.setOnPreferenceChangeListener { _, newTheme ->
                            Theme.currentNightMode = (newTheme as String).toInt()
                            Theme.applyNightTheme()
                            ActivityCompat.recreate(requireActivity())
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
                                // Restart to make sure the context wrappers using
                                // ContextCompat.getLanguageContext can work.
                                // We store the message even Activity recreate.
                                viewModel.storeShouldRestart()
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
                            viewModel.tryStopService()
                            true
                        }

                        Key.MEMORY_LIMIT, Key.LOG_LEVEL -> {
                            preference.onPreferenceChangeListener = restartListener
                        }

                        Key.LOG_MAX_SIZE -> {
                            preference as EditTextPreference
                            preference.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
                        }

                        Key.ALWAYS_SHOW_ADDRESS -> {
                            alwaysShowAddress = preference as MaterialSwitchPreference
                        }

                        Key.BLURRED_ADDRESS -> blurredAddress =
                            preference as MaterialSwitchPreference

                        Key.PROFILE_TRAFFIC_STATISTICS -> profileTrafficStatistics =
                            preference as MaterialSwitchPreference

                        Key.SPEED_INTERVAL -> speedInterval = preference as SimpleMenuPreference

                        Key.SHOW_DIRECT_SPEED -> showDirectSpeed =
                            preference as MaterialSwitchPreference

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
                            isProxyApps = preference as MaterialSwitchPreference
                            isProxyApps.setOnPreferenceChangeListener { _, newValue ->
                                startActivity(Intent(activity, AppManagerActivity::class.java))
                                if (newValue as Boolean) DataStore.dirty = true
                                newValue
                            }
                        }

                        Key.BYPASS_LAN -> bypassLan = preference as MaterialSwitchPreference
                        Key.BYPASS_LAN_IN_CORE -> bypassLanInCore =
                            preference as MaterialSwitchPreference

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

                        Key.UPDATE_PROXY_APPS_WHEN_INSTALL -> {}

                        else -> preference.onPreferenceChangeListener = reloadListener
                    }
                }

                Key.PROTOCOL_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
                        Key.CUSTOM_PLUGIN_PREFIX -> {
                            preference.onPreferenceChangeListener = restartListener
                        }

                        Key.UPLOAD_SPEED, Key.DOWNLOAD_SPEED -> {
                            preference as EditTextPreference
                            preference.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
                            preference.onPreferenceChangeListener = reloadListener
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

                        Key.APPEND_HTTP_PROXY -> appendHttpProxy =
                            preference as MaterialSwitchPreference

                        Key.HTTP_PROXY_BYPASS -> httpProxyBypass = preference as EditTextPreference

                        else -> preference.onPreferenceChangeListener = reloadListener
                    }
                }

                Key.DNS_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
                        Key.DNS_HOSTS -> {
                            preference as EditTextPreference
                            preference.onPreferenceChangeListener = reloadListener
                            preference.setOnBindEditTextListener(EditTextPreferenceModifiers.Hosts)
                        }

                        else -> preference.onPreferenceChangeListener = reloadListener
                    }
                }

                Key.MISC_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
                        Key.TCP_KEEP_ALIVE_INTERVAL -> {
                            preference.onPreferenceChangeListener = reloadListener
                            preference.isVisible = false
                        }

                        Key.CERT_PROVIDER -> preference.onPreferenceChangeListener = restartListener

                        Key.DISABLE_PROCESS_TEXT -> preference.setOnPreferenceChangeListener { _, newValue ->
                            preference.context.packageManager.setComponentEnabledSetting(
                                ComponentName(
                                    preference.context,
                                    "io.nekohasekai.sagernet.ui.ProcessTextActivityAlias",
                                ),
                                if (newValue as Boolean) {
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                                } else {
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                },
                                PackageManager.DONT_KILL_APP,
                            )
                            true
                        }

                        Key.CONNECTION_TEST_URL, Key.IGNORE_DEVICE_IDLE -> Unit

                        else -> preference.onPreferenceChangeListener = reloadListener
                    }
                }

                Key.NTP_SETTINGS -> preferenceCategory.forEach { preference ->
                    when (preference.key) {
                        Key.ENABLE_NTP -> ntpEnable = preference as MaterialSwitchPreference
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
            setOnBindEditTextListener(EditTextPreferenceModifiers.Hosts)

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
        setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        onPreferenceChangeListener = reloadListener
    }

}