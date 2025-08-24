package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

internal class JuicitySettingsViewModel : ProfileSettingsViewModel<JuicityBean>() {
    override fun createBean() = JuicityBean().applyDefaultValues()

    override fun JuicityBean.writeToTempDatabase() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = uuid
        DataStore.serverPassword = password
        DataStore.serverSNI = sni
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverPinnedCertificateChain = pinSHA256
    }

    override fun JuicityBean.loadFromTempDatabase() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        uuid = DataStore.serverUsername
        password = DataStore.serverPassword
        sni = DataStore.serverSNI
        allowInsecure = DataStore.serverAllowInsecure
        pinSHA256 = DataStore.serverPinnedCertificateChain
    }
}