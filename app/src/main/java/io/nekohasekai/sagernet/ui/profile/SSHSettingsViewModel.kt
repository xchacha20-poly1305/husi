package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.ssh.SSHBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

internal class SSHSettingsViewModel : ProfileSettingsViewModel<SSHBean>() {
    override fun createBean() = SSHBean().applyDefaultValues()

    override fun SSHBean.writeToTempDatabase() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = username
        DataStore.serverAuthType = authType
        DataStore.serverPassword = password
        DataStore.serverPrivateKey = privateKey
        DataStore.serverPassword1 = privateKeyPassphrase
        DataStore.serverCertificates = publicKey
    }

    override fun SSHBean.loadFromTempDatabase() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        username = DataStore.serverUsername
        authType = DataStore.serverAuthType
        when (authType) {
            SSHBean.AUTH_TYPE_NONE -> {
            }

            SSHBean.AUTH_TYPE_PASSWORD -> {
                password = DataStore.serverPassword
            }

            SSHBean.AUTH_TYPE_PRIVATE_KEY -> {
                privateKey = DataStore.serverPrivateKey
                privateKeyPassphrase = DataStore.serverPassword1
            }
        }
        publicKey = DataStore.serverCertificates
    }
}