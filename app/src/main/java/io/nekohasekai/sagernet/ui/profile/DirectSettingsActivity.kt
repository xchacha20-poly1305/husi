package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.direct.DirectBean

class DirectSettingsActivity : ProfileSettingsActivity<DirectBean>() {
    override fun createEntity() = DirectBean()

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        addPreferencesFromResource(R.xml.direct_preferences)
        findPreference<EditTextPreference>(Key.OVERRIDE_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
    }

    override fun DirectBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.overrideAddress
        serverPort = DataStore.overridePort
    }

    override fun DirectBean.init() {
        DataStore.profileName = name
        DataStore.overrideAddress = serverAddress
        DataStore.overridePort = serverPort
    }
}