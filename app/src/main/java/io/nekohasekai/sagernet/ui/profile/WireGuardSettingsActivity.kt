package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

class WireGuardSettingsActivity : ProfileSettingsActivity<WireGuardBean>() {

    override fun createEntity() = WireGuardBean().applyDefaultValues()

    override fun WireGuardBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.localAddress = localAddress
        DataStore.listenPort = listenPort
        DataStore.privateKey = privateKey
        DataStore.publicKey = publicKey
        DataStore.preSharedKey = preSharedKey
        DataStore.serverMTU = mtu
        DataStore.serverReserved = reserved
        DataStore.serverPersistentKeepaliveInterval = persistentKeepaliveInterval
    }

    override fun WireGuardBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        localAddress = DataStore.localAddress
        listenPort = DataStore.listenPort
        privateKey = DataStore.privateKey
        publicKey = DataStore.publicKey
        preSharedKey = DataStore.preSharedKey
        mtu = DataStore.serverMTU
        reserved = DataStore.serverReserved
        persistentKeepaliveInterval = DataStore.serverPersistentKeepaliveInterval
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.wireguard_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.setOnBindEditTextListener(
            EditTextPreferenceModifiers.Port
        )
        findPreference<EditTextPreference>(Key.LISTEN_PORT)!!.setOnBindEditTextListener(
            EditTextPreferenceModifiers.Port
        )
        findPreference<EditTextPreference>(Key.PRIVATE_KEY)!!.setSummaryProvider(
            PasswordSummaryProvider
        )
        findPreference<EditTextPreference>(Key.PRE_SHARED_KEY)!!.setSummaryProvider(
            PasswordSummaryProvider
        )
        findPreference<EditTextPreference>(Key.SERVER_MTU)!!.setOnBindEditTextListener(
            EditTextPreferenceModifiers.Number
        )
        findPreference<EditTextPreference>(Key.SERVER_PERSISTENT_KEEPALIVE_INTERVAL)!!.setOnBindEditTextListener(
            EditTextPreferenceModifiers.Number
        )
    }

}