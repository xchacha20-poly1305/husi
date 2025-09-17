package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.fmt.direct.DirectBean

@OptIn(ExperimentalMaterial3Api::class)
class DirectSettingsActivity : ProfileSettingsActivity<DirectBean>() {

    override val viewModel by viewModels<DirectSettingsViewModel>()

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        addPreferencesFromResource(R.xml.direct_preferences)
    }

}