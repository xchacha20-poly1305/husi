package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.naive.NaiveBean

class NaiveSettingsActivity : ProfileSettingsActivity<NaiveBean>() {

    override fun createBean() = NaiveBean()

    override fun NaiveBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = username
        DataStore.serverPassword = password
        DataStore.serverProtocol = proto
        DataStore.serverSNI = sni
        DataStore.serverHeaders = extraHeaders
        DataStore.serverInsecureConcurrency = insecureConcurrency
        DataStore.udpOverTcp = udpOverTcp
        DataStore.serverNoPostQuantum = noPostQuantum
    }

    override fun NaiveBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        proto = DataStore.serverProtocol
        sni = DataStore.serverSNI
        extraHeaders = DataStore.serverHeaders.replace("\r\n", "\n")
        insecureConcurrency = DataStore.serverInsecureConcurrency
        udpOverTcp = DataStore.udpOverTcp
        noPostQuantum = DataStore.serverNoPostQuantum
    }

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

    override fun finish() {
        if (DataStore.profileName == "人间风雨爱好者") {
            DataStore.isExpert = true
        } else if (DataStore.profileName == "世界") {
            DataStore.isExpert = false
        }
        super.finish()
    }

}