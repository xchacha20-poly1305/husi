package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.anytls.AnyTLSBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers

class AnyTLSSettingsActivity : ProfileSettingsActivity<AnyTLSBean>() {
    override fun createEntity() = AnyTLSBean().applyDefaultValues()

    override fun AnyTLSBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        password = DataStore.serverPassword
        idleSessionCheckInterval = DataStore.serverIdleSessionCheckInterval
        idleSessionTimeout = DataStore.serverIdleSessionTimeout
        minIdleSession = DataStore.serverMinIdleSession
        serverName = DataStore.serverSNI
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        utlsFingerprint = DataStore.serverUtlsFingerPrint
        allowInsecure = DataStore.serverAllowInsecure
        echConfig = DataStore.serverECHConfig
    }

    override fun AnyTLSBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverPassword = password
        DataStore.serverIdleSessionCheckInterval = idleSessionCheckInterval
        DataStore.serverIdleSessionTimeout = idleSessionTimeout
        DataStore.serverMinIdleSession = minIdleSession
        DataStore.serverSNI = serverName
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverUtlsFingerPrint = utlsFingerprint
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverECHConfig = echConfig
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        addPreferencesFromResource(R.xml.anytls_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }
}
