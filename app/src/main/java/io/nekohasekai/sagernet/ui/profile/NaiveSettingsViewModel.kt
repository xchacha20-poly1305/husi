package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

internal class NaiveSettingsViewModel : ProfileSettingsViewModel<NaiveBean>() {
    override fun createBean() = NaiveBean().applyDefaultValues()

    override fun NaiveBean.writeToTempDatabase() {
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

    override fun NaiveBean.loadFromTempDatabase() {
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
}