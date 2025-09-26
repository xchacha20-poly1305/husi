package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.widget.MaterialSwitchPreference
import io.nekohasekai.sagernet.widget.PasswordSummaryProvider
import rikka.preference.SimpleMenuPreference

@OptIn(ExperimentalMaterial3Api::class)
class HysteriaSettingsActivity : ProfileSettingsActivity<HysteriaBean>() {

    override val viewModel by viewModels<HysteriaSettingsViewModel>()

    private lateinit var serverStreamReceiveWindow: EditTextPreference
    private lateinit var serverConnectionReceiveWindow: EditTextPreference
    private lateinit var serverDisableMTUDiscovery: MaterialSwitchPreference
    private lateinit var serverMTlsCategory: PreferenceCategory

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.hysteria_preferences)

        val authType = findPreference<SimpleMenuPreference>(Key.SERVER_AUTH_TYPE)!!
        val authPayload = findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!
        authPayload.isVisible = authType.value != "${HysteriaBean.TYPE_NONE}"
        authType.setOnPreferenceChangeListener { _, newValue ->
            authPayload.isVisible = newValue != "${HysteriaBean.TYPE_NONE}"
            true
        }

        val protocol = findPreference<SimpleMenuPreference>(Key.SERVER_PROTOCOL)!!
        val alpn = findPreference<EditTextPreference>(Key.SERVER_ALPN)!!

        serverStreamReceiveWindow = findPreference(Key.SERVER_STREAM_RECEIVE_WINDOW)!!
        serverConnectionReceiveWindow = findPreference(Key.SERVER_CONNECTION_RECEIVE_WINDOW)!!
        serverDisableMTUDiscovery = findPreference(Key.SERVER_DISABLE_MTU_DISCOVERY)!!
        serverMTlsCategory = findPreference(Key.SERVER_M_TLS_CATEGORY)!!
        fun updateVersion(v: Int) {
            if (v == 2) {
                authPayload.isVisible = true

                authType.isVisible = false
                protocol.isVisible = false
                alpn.isVisible = false

                serverStreamReceiveWindow.isVisible = false
                serverConnectionReceiveWindow.isVisible = false
                serverDisableMTUDiscovery.isVisible = false
                serverMTlsCategory.isVisible = true

                authPayload.title = resources.getString(R.string.password)
            } else {
                authType.isVisible = true
                authPayload.isVisible = true
                protocol.isVisible = true
                alpn.isVisible = true

                serverStreamReceiveWindow.isVisible = true
                serverConnectionReceiveWindow.isVisible = true
                serverDisableMTUDiscovery.isVisible = true
                serverMTlsCategory.isVisible = false

                authPayload.title = resources.getString(R.string.hysteria_auth_payload)
            }
        }
        findPreference<SimpleMenuPreference>(Key.PROTOCOL_VERSION)!!.setOnPreferenceChangeListener { _, newValue ->
            updateVersion(newValue.toString().toIntOrNull() ?: 1)
            true
        }
        updateVersion(DataStore.protocolVersion)

        findPreference<EditTextPreference>(Key.SERVER_STREAM_RECEIVE_WINDOW)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        findPreference<EditTextPreference>(Key.SERVER_CONNECTION_RECEIVE_WINDOW)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_OBFS)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

}
