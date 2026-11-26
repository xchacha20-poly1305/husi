package fr.husi.fmt.plugin

import fr.husi.fmt.AbstractBean
import fr.husi.fmt.BeanConverters
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput
import fr.husi.ktx.joinAddress
import fr.husi.ktx.unwrapIPV6Host
import fr.husi.platform.PlatformInfo
import kotlinx.serialization.Serializable as KxsSerializable

@KxsSerializable
class CustomPluginBean : AbstractBean() {

    companion object {
        const val DEFAULT_CONFIG_FILE_NAME = "config.json"
        private const val DEFAULT_DISPLAY_ADDRESS = "Custom Plugin"

        @JvmField
        val CREATOR = object : CREATOR<CustomPluginBean>() {
            override fun newInstance(): CustomPluginBean {
                return CustomPluginBean()
            }

            override fun newArray(size: Int): Array<CustomPluginBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var pluginId: String = ""
    var executablePath: String = ""
    var commandArgs: String = ""
    var configFileName: String = DEFAULT_CONFIG_FILE_NAME
    var config: String = ""
    var environment: String = ""
    var supportProtect: Boolean = false

    init {
        serverAddress = ""
    }

    override val defaultPort get() = 0
    override val canMapping get() = serverAddress.isNotBlank() && serverPort in 1..65535
    override val canSelfProtect get() = supportProtect
    override val canICMPing get() = serverAddress.isNotBlank()
    override val canTCPing get() = serverAddress.isNotBlank() && serverPort in 1..65535

    override fun initializeDefaultValues() {
        if (serverAddress.startsWith("[") && serverAddress.endsWith("]")) {
            serverAddress = serverAddress.unwrapIPV6Host()
        }
        if (serverPort !in 1..65535) {
            serverPort = defaultPort
        }
        finalAddress = serverAddress
        finalPort = serverPort
        if (configFileName.isBlank()) {
            configFileName = DEFAULT_CONFIG_FILE_NAME
        }
    }

    override fun displayAddress(): String {
        if (serverAddress.isNotBlank() && serverPort in 1..65535) {
            return joinAddress(serverAddress, serverPort)
        }
        return if (PlatformInfo.isAndroid) {
            pluginId.ifBlank { DEFAULT_DISPLAY_ADDRESS }
        } else {
            executablePath
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .ifBlank { DEFAULT_DISPLAY_ADDRESS }
        }
    }

    override fun serialize(output: BinaryOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeString(pluginId)
        output.writeString(executablePath)
        output.writeString(commandArgs)
        output.writeString(configFileName)
        output.writeString(config)
        output.writeString(environment)
        output.writeBoolean(supportProtect)
    }

    override fun deserialize(input: BinaryInput) {
        input.readInt()
        super.deserialize(input)
        pluginId = input.readString()
        executablePath = input.readString()
        commandArgs = input.readString()
        configFileName = input.readString()
        config = input.readString()
        environment = input.readString()
        supportProtect = input.readBoolean()
    }

    override fun clone(): CustomPluginBean {
        return BeanConverters.deserialize(CustomPluginBean(), BeanConverters.serialize(this))
    }
}
