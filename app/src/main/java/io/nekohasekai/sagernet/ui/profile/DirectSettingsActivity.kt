package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.direct.DirectBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

class DirectSettingsActivity : ProfileSettingsActivity<DirectBean>() {
    override fun createBean() = DirectBean().applyDefaultValues()

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        addPreferencesFromResource(R.xml.direct_preferences)
    }

    override fun DirectBean.serialize() {
        name = DataStore.profileName
    }

    override fun DirectBean.init() {
        DataStore.profileName = name
    }
}