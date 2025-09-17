package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.widget.PasswordSummaryProvider

@OptIn(ExperimentalMaterial3Api::class)
class WireGuardSettingsActivity : ProfileSettingsActivity<WireGuardBean>() {

    override val viewModel by viewModels<WireGuardSettingsViewModel>()

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.wireguard_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.setOnBindEditTextListener(
            EditTextPreferenceModifiers.Port
        )
        findPreference<EditTextPreference>(Key.LISTEN_PORT)!!.setOnBindEditTextListener(
            EditTextPreferenceModifiers.Port
        )
        findPreference<EditTextPreference>(Key.PRIVATE_KEY)!!.setSummaryProvider(
            PasswordSummaryProvider
        )
        findPreference<EditTextPreference>(Key.PRE_SHARED_KEY)!!.setSummaryProvider(
            PasswordSummaryProvider
        )
        findPreference<EditTextPreference>(Key.SERVER_MTU)!!.setOnBindEditTextListener(
            EditTextPreferenceModifiers.Number
        )
        findPreference<EditTextPreference>(Key.SERVER_PERSISTENT_KEEPALIVE_INTERVAL)!!.setOnBindEditTextListener(
            EditTextPreferenceModifiers.Number
        )
    }

}