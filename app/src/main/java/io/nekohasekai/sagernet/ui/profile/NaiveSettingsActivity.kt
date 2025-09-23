package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.widget.PasswordSummaryProvider

@OptIn(ExperimentalMaterial3Api::class)
class NaiveSettingsActivity : ProfileSettingsActivity<NaiveBean>() {

    override val viewModel by viewModels<NaiveSettingsViewModel>()

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.naive_preferences)
        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_HEADERS)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Hosts)
        }
        findPreference<EditTextPreference>(Key.SERVER_INSECURE_CONCURRENCY)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
    }

}