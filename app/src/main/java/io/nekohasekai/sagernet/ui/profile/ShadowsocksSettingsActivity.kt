package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type

class ShadowsocksSettingsActivity : ProfileSettingsActivity<ShadowsocksBean>() {

    override fun createEntity() = ShadowsocksBean()

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))
    private val serverAddress = pbm.add(PreferenceBinding(Type.Text, Key.SERVER_ADDRESS))
    private val serverPort = pbm.add(PreferenceBinding(Type.TextToInt, Key.SERVER_PORT))
    private val password = pbm.add(PreferenceBinding(Type.Text, "password"))
    private val method = pbm.add(PreferenceBinding(Type.Text, "method"))
    private val pluginName =
        pbm.add(PreferenceBinding(Type.Text, "pluginName").apply { disable = true })
    private val pluginConfig =
        pbm.add(PreferenceBinding(Type.Text, "pluginConfig").apply { disable = true })

    private val serverMux = pbm.add(PreferenceBinding(Type.Bool, Key.SERVER_MUX))
    private val serverBrutal = pbm.add(PreferenceBinding(Type.Bool, Key.SERVER_BRUTAL))
    private val serverMuxType = pbm.add(PreferenceBinding(Type.Int, Key.SERVER_MUX_TYPE))
    private val serverMuxConcurrency = pbm.add(PreferenceBinding(Type.TextToInt, Key.SERVER_MUX_CONCURRENCY))
    private val serverMuxPadding = pbm.add(PreferenceBinding(Type.Bool, Key.SERVER_MUX_PADDING))
    private val sUoT = pbm.add(PreferenceBinding(Type.Bool, "sUoT"))

    override fun ShadowsocksBean.init() {
        pbm.writeToCacheAll(this)

        DataStore.profileCacheStore.putString("pluginName", plugin.substringBefore(";"))
        DataStore.profileCacheStore.putString("pluginConfig", plugin.substringAfter(";"))
    }

    override fun ShadowsocksBean.serialize() {
        pbm.fromCacheAll(this)

        val pn = pluginName.readStringFromCache()
        val pc = pluginConfig.readStringFromCache()
        plugin = if (pn.isNotBlank()) "$pn;$pc" else ""
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.shadowsocks_preferences)
        pbm.setPreferenceFragment(this)

        updateMuxState(DataStore.serverMux)

        serverPort.preference.apply {
            this as EditTextPreference
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        password.preference.apply {
            this as EditTextPreference
            summaryProvider = PasswordSummaryProvider
        }
        serverMux.preference.apply {
            this as SwitchPreference
            setOnPreferenceChangeListener { _, newValue ->
                updateMuxState(newValue as Boolean)
                true
            }
        }
        serverBrutal.preference.apply {
            this as SwitchPreference
            setOnPreferenceChangeListener { _, newValue ->
                serverMuxConcurrency.preference.isEnabled = !(newValue as Boolean)
                true
            }
        }
        (serverMuxConcurrency.preference as EditTextPreference).setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
    }

    private fun updateMuxState(enabled: Boolean) {
        if (enabled) {
            serverBrutal.preference.isVisible = true
            serverMuxType.preference.isVisible = true
            serverMuxConcurrency.preference.isVisible = true
            serverMuxPadding.preference.isVisible = true
        } else {
            serverBrutal.preference.isVisible = false
            serverMuxType.preference.isVisible = false
            serverMuxConcurrency.preference.isVisible = false
            serverMuxPadding.preference.isVisible = false
        }
    }

}
