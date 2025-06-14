package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.shadowquic.ShadowQUICBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

class ShadowQUICSettingsActivity : ProfileSettingsActivity<ShadowQUICBean>() {
    override fun createBean() = ShadowQUICBean().applyDefaultValues()

    override fun ShadowQUICBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverJLSPassword = jlsPassword
        DataStore.serverJLSIV = jlsIv
        DataStore.serverSNI = sni
        DataStore.serverALPN = alpn
        DataStore.serverInitialMTU = initialMTU
        DataStore.serverMinimumMTU = minimumMTU
        DataStore.serverCongestionController = congestionControl
        DataStore.serverZeroRTT = zeroRTT
        DataStore.udpOverTcp = udpOverStream
    }

    override fun ShadowQUICBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        jlsPassword = DataStore.serverJLSPassword
        jlsIv = DataStore.serverJLSIV
        sni = DataStore.serverSNI
        alpn = DataStore.serverALPN
        initialMTU = DataStore.serverInitialMTU
        minimumMTU = DataStore.serverMinimumMTU
        congestionControl = DataStore.serverCongestionController
        zeroRTT = DataStore.serverZeroRTT
        udpOverStream = DataStore.udpOverTcp
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        addPreferencesFromResource(R.xml.shadowquic_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_JLS_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_JLS_IV)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

}