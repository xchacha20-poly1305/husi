package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import rikka.preference.SimpleMenuPreference

class HysteriaSettingsActivity : ProfileSettingsActivity<HysteriaBean>() {

    override fun createBean() = HysteriaBean().applyDefaultValues()

    override fun HysteriaBean.init() {
        DataStore.profileName = name
        DataStore.protocolVersion = protocolVersion
        DataStore.serverAddress = serverAddress
        DataStore.serverPorts = serverPorts
        DataStore.serverObfs = obfuscation
        DataStore.serverAuthType = authPayloadType
        DataStore.serverProtocolInt = protocol
        DataStore.serverPassword = authPayload
        DataStore.serverSNI = sni
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverStreamReceiveWindow = streamReceiveWindow
        DataStore.serverConnectionReceiveWindow = connectionReceiveWindow
        DataStore.serverDisableMtuDiscovery = disableMtuDiscovery
        DataStore.serverHopInterval = hopInterval
        DataStore.serverECH = ech
        DataStore.serverECHConfig = echConfig
    }

    override fun HysteriaBean.serialize() {
        name = DataStore.profileName
        protocolVersion = DataStore.protocolVersion
        serverAddress = DataStore.serverAddress
        serverPorts = DataStore.serverPorts
        obfuscation = DataStore.serverObfs
        authPayloadType = DataStore.serverAuthType
        authPayload = DataStore.serverPassword
        protocol = DataStore.serverProtocolInt
        sni = DataStore.serverSNI
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        allowInsecure = DataStore.serverAllowInsecure
        streamReceiveWindow = DataStore.serverStreamReceiveWindow
        connectionReceiveWindow = DataStore.serverConnectionReceiveWindow
        disableMtuDiscovery = DataStore.serverDisableMtuDiscovery
        hopInterval = DataStore.serverHopInterval
        ech = DataStore.serverECH
        echConfig = DataStore.serverECHConfig
    }

    lateinit var serverStreamReceiveWindow: EditTextPreference
    lateinit var serverConnectionReceiveWindow: EditTextPreference
    lateinit var serverDisableMTUDiscovery: SwitchPreference

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.hysteria_preferences)

        val authType = findPreference<SimpleMenuPreference>(Key.SERVER_AUTH_TYPE)!!
        val authPayload = findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!
        authPayload.isVisible = authType.value != "${HysteriaBean.TYPE_NONE}"
        authType.setOnPreferenceChangeListener { _, newValue ->
            authPayload.isVisible = newValue != "${HysteriaBean.TYPE_NONE}"
            true
        }

        val protocol = findPreference<SimpleMenuPreference>(Key.SERVER_PROTOCOL)!!
        val alpn = findPreference<EditTextPreference>(Key.SERVER_ALPN)!!

        serverStreamReceiveWindow = findPreference<EditTextPreference>(Key.SERVER_STREAM_RECEIVE_WINDOW)!!
        serverConnectionReceiveWindow = findPreference<EditTextPreference>(Key.SERVER_CONNECTION_RECEIVE_WINDOW)!!
        serverDisableMTUDiscovery = findPreference<SwitchPreference>(Key.SERVER_DISABLE_MTU_DISCOVERY)!!
        fun updateVersion(v: Int) {
            if (v == 2) {
                authPayload.isVisible = true

                authType.isVisible = false
                protocol.isVisible = false
                alpn.isVisible = false

                serverStreamReceiveWindow.isVisible = false
                serverConnectionReceiveWindow.isVisible = false
                serverDisableMTUDiscovery.isVisible = false

                authPayload.title = resources.getString(R.string.password)
            } else {
                authType.isVisible = true
                authPayload.isVisible = true
                protocol.isVisible = true
                alpn.isVisible = true

                serverStreamReceiveWindow.isVisible = true
                serverConnectionReceiveWindow.isVisible = true
                serverDisableMTUDiscovery.isVisible = true

                authPayload.title = resources.getString(R.string.hysteria_auth_payload)
            }
        }
        findPreference<SimpleMenuPreference>(Key.PROTOCOL_VERSION)!!.setOnPreferenceChangeListener { _, newValue ->
            updateVersion(newValue.toString().toIntOrNull() ?: 1)
            true
        }
        updateVersion(DataStore.protocolVersion)

        findPreference<EditTextPreference>(Key.SERVER_STREAM_RECEIVE_WINDOW)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        findPreference<EditTextPreference>(Key.SERVER_CONNECTION_RECEIVE_WINDOW)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_OBFS)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

}
