package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.widget.MaterialSwitchPreference
import io.nekohasekai.sagernet.widget.PasswordSummaryProvider
import rikka.preference.SimpleMenuPreference

@OptIn(ExperimentalMaterial3Api::class)
class ShadowsocksSettingsActivity : ProfileSettingsActivity<ShadowsocksBean>() {

    override val viewModel by viewModels<ShadowsocksSettingsViewModel>()

    private lateinit var serverBrutal: MaterialSwitchPreference
    private lateinit var serverMuxType: SimpleMenuPreference
    private lateinit var serverMuxNumber: EditTextPreference
    private lateinit var serverMuxStrategy: SimpleMenuPreference
    private lateinit var serverMuxPadding: MaterialSwitchPreference
    private lateinit var udpOverTcp: MaterialSwitchPreference

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.shadowsocks_preferences)

        serverMuxType = findPreference(Key.SERVER_MUX_TYPE)!!
        serverMuxStrategy = findPreference(Key.SERVER_MUX_STRATEGY)!!
        serverMuxPadding = findPreference(Key.SERVER_MUX_PADDING)!!
        serverMuxNumber = findPreference<EditTextPreference>(Key.SERVER_MUX_NUMBER)!!.also {
            it.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        fun onBrutalChange(brutal: Boolean) {
            serverMuxStrategy.isEnabled = !brutal
            serverMuxNumber.isEnabled = !brutal
        }
        serverBrutal = findPreference(Key.SERVER_BRUTAL)!!
        onBrutalChange(serverBrutal.isChecked)
        serverBrutal.setOnPreferenceChangeListener { _, newValue ->
            onBrutalChange(newValue as Boolean)
            true
        }
        udpOverTcp = findPreference(Key.UDP_OVER_TCP)!!
        updateMuxState(DataStore.serverMux)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }

        findPreference<MaterialSwitchPreference>(Key.SERVER_MUX)!!.apply {
            setOnPreferenceChangeListener { _, newValue ->
                updateMuxState(newValue as Boolean)
                true
            }
        }
    }

    private fun updateMuxState(enabled: Boolean) {
        serverBrutal.isVisible = enabled
        serverMuxType.isVisible = enabled
        serverMuxStrategy.isVisible = enabled
        serverMuxNumber.isVisible = enabled
        serverMuxPadding.isVisible = enabled

        udpOverTcp.isEnabled = !enabled
    }

}
