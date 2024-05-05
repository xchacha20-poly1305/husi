package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.MuxState
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type
import moe.matsuri.nb4a.ui.SimpleMenuPreference

class ShadowsocksSettingsActivity : ProfileSettingsActivity<ShadowsocksBean>() {

    override fun createEntity() = ShadowsocksBean()

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))
    private val serverAddress = pbm.add(PreferenceBinding(Type.Text, "serverAddress"))
    private val serverPort = pbm.add(PreferenceBinding(Type.TextToInt, "serverPort"))
    private val password = pbm.add(PreferenceBinding(Type.Text, "password"))
    private val method = pbm.add(PreferenceBinding(Type.Text, "method"))
    private val pluginName =
        pbm.add(PreferenceBinding(Type.Text, "pluginName").apply { disable = true })
    private val pluginConfig =
        pbm.add(PreferenceBinding(Type.Text, "pluginConfig").apply { disable = true })
    private val muxState = pbm.add(PreferenceBinding(Type.TextToInt, "muxState"))
    private val serverBrutal = pbm.add(PreferenceBinding(Type.Bool, "serverBrutal"))
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

        serverBrutal.preference.isEnabled = DataStore.profileCacheStore.getString(muxState.fieldName) != MuxState.DISABLED.toString()

        serverPort.preference.apply {
            this as EditTextPreference
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        password.preference.apply {
            this as EditTextPreference
            summaryProvider = PasswordSummaryProvider
        }
        muxState.preference.apply {
            this as SimpleMenuPreference
            setOnPreferenceChangeListener {_, newValue ->
                updateBrutal(newValue as String)
                true
            }
        }
    }

    private fun updateBrutal(muxState: String) {
        when(muxState) {
            MuxState.DEFAULT.toString(), MuxState.ENABLED.toString()-> {
                serverBrutal.preference.isEnabled = true
            }
            MuxState.DISABLED.toString() -> {
                serverBrutal.preference.isEnabled = false
                (serverBrutal.preference as SwitchPreference).isChecked = false
            }
        }
    }

}
