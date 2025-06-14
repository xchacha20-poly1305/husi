package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

class JuicitySettingsActivity : ProfileSettingsActivity<JuicityBean>() {

    override fun createBean() = JuicityBean().applyDefaultValues()

    override fun JuicityBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = uuid
        DataStore.serverPassword = password
        DataStore.serverSNI = sni
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverPinnedCertificateChain = pinSHA256
    }

    override fun JuicityBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        uuid = DataStore.serverUsername
        password = DataStore.serverPassword
        sni = DataStore.serverSNI
        allowInsecure = DataStore.serverAllowInsecure
        pinSHA256 = DataStore.serverPinnedCertificateChain
    }

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