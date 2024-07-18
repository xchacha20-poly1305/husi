package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.ktx.app
import moe.matsuri.nb4a.ui.SimpleMenuPreference

class TrojanGoSettingsActivity : ProfileSettingsActivity<TrojanGoBean>() {

    override fun createEntity() = TrojanGoBean()

    override fun TrojanGoBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverPassword = password
        DataStore.serverSNI = sni
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverNetwork = type
        DataStore.serverHost = host
        DataStore.serverPath = path
        DataStore.serverMux = serverMux
        DataStore.serverMuxConcurrency = serverMuxConcurrency
        if (encryption.startsWith("ss;")) {
            DataStore.serverEncryption = "ss"
            DataStore.serverMethod = encryption.substringAfter(";").substringBefore(":")
            DataStore.serverPassword1 = encryption.substringAfter(":")
        } else {
            DataStore.serverEncryption = encryption
        }
    }

    override fun TrojanGoBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        password = DataStore.serverPassword
        sni = DataStore.serverSNI
        allowInsecure = DataStore.serverAllowInsecure
        type = DataStore.serverNetwork
        host = DataStore.serverHost
        path = DataStore.serverPath
        serverMux = DataStore.serverMux
        serverMuxConcurrency = DataStore.serverMuxConcurrency
        encryption = when (val security = DataStore.serverEncryption) {
            "ss" -> {
                "ss;" + DataStore.serverMethod + ":" + DataStore.serverPassword1
            }

            else -> {
                security
            }
        }
    }

    lateinit var network: SimpleMenuPreference
    private lateinit var encryprtion: SimpleMenuPreference
    private lateinit var wsCategory: PreferenceCategory
    private lateinit var ssCategory: PreferenceCategory
    lateinit var method: SimpleMenuPreference

    lateinit var mux: SwitchPreference
    private lateinit var muxConcurrency: EditTextPreference

    private val trojanGoMethods: Array<String> = app.resources.getStringArray(R.array.trojan_go_methods)
    private val trojanGoNetworks: Array<String> = app.resources.getStringArray(R.array.trojan_go_networks_value)

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.trojan_go_preferences)
        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD1)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        wsCategory = findPreference(Key.SERVER_WS_CATEGORY)!!
        ssCategory = findPreference(Key.SERVER_SS_CATEGORY)!!
        method = findPreference(Key.SERVER_METHOD)!!

        network = findPreference(Key.SERVER_NETWORK)!!

        mux = findPreference(Key.SERVER_MUX)!!
        muxConcurrency = findPreference(Key.SERVER_MUX_CONCURRENCY)!!
        muxConcurrency.isVisible = mux.isChecked

        if (network.value !in trojanGoNetworks) {
            network.value = trojanGoNetworks[0]
        }

        updateNetwork(network.value)
        network.setOnPreferenceChangeListener { _, newValue ->
            updateNetwork(newValue as String)
            true
        }
        encryprtion = findPreference(Key.SERVER_ENCRYPTION)!!
        updateEncryption(encryprtion.value)
        encryprtion.setOnPreferenceChangeListener { _, newValue ->
            updateEncryption(newValue as String)
            true
        }

        mux.setOnPreferenceChangeListener { _, newValue ->
            muxConcurrency.isVisible = newValue as Boolean
            true
        }
        muxConcurrency.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
    }

    private fun updateNetwork(newNet: String) {
        when (newNet) {
            "ws" -> {
                wsCategory.isVisible = true
            }

            else -> {
                wsCategory.isVisible = false
            }
        }
    }

    private fun updateEncryption(encryption: String) {
        when (encryption) {
            "ss" -> {
                ssCategory.isVisible = true

                if (method.value !in trojanGoMethods) {
                    method.value = trojanGoMethods[0]
                }
            }

            else -> {
                ssCategory.isVisible = false
            }
        }
    }

}