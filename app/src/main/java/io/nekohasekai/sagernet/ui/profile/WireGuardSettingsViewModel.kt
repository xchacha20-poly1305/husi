package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

internal class WireGuardSettingsViewModel : ProfileSettingsViewModel<WireGuardBean>() {
    override fun createBean() = WireGuardBean().applyDefaultValues()

    override fun WireGuardBean.writeToTempDatabase() {
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

    override fun WireGuardBean.loadFromTempDatabase() {
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
}