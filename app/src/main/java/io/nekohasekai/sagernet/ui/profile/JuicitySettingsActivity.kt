package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.activity.viewModels
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.widget.PasswordSummaryProvider

class JuicitySettingsActivity : ProfileSettingsActivity<JuicityBean>() {

    override val viewModel by viewModels<JuicitySettingsViewModel>()

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.juicity_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

}