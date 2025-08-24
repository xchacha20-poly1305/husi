package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.activity.viewModels
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.fmt.anytls.AnyTLSBean
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.widget.DurationPreference
import io.nekohasekai.sagernet.widget.MaterialSwitchPreference
import io.nekohasekai.sagernet.widget.PasswordSummaryProvider

class AnyTLSSettingsActivity : ProfileSettingsActivity<AnyTLSBean>() {

    override val viewModel by viewModels<AnyTLSSettingsViewModel>()

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.anytls_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_MIN_IDLE_SESSION)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }

        val fragment = findPreference<MaterialSwitchPreference>(Key.SERVER_FRAGMENT)!!
        val fragmentFallbackDelay =
            findPreference<DurationPreference>(Key.SERVER_FRAGMENT_FALLBACK_DELAY)!!

        fun updateFragmentFallbackDelay(enabled: Boolean = fragment.isChecked) {
            fragmentFallbackDelay.isEnabled = enabled
        }
        updateFragmentFallbackDelay()
        fragment.setOnPreferenceChangeListener { _, newValue ->
            updateFragmentFallbackDelay(newValue as Boolean)
            true
        }
    }
}