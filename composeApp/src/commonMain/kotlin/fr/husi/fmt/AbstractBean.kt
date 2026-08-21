package fr.husi.fmt

import kotlinx.serialization.Serializable as KxsSerializable
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput
import fr.husi.ktx.isIpAddress
import fr.husi.ktx.unwrapIPV6Host
import fr.husi.ktx.wrapIPV6Host

@KxsSerializable
abstract class AbstractBean : Serializable() {

    open val defaultPort get() = 1080

    var serverAddress: String = "127.0.0.1"
    var serverPort: Int = defaultPort
    var name: String = ""
    var customOutboundJson: String = ""
    var customConfigJson: String = ""
    var serverMux: Boolean = false
    var serverBrutal: Boolean = false
    var serverMuxType: Int = 0
    var serverMuxNumber: Int = 8
    var serverMuxPadding: Boolean = false
    var serverMuxStrategy: Int = 0
    var finalAddress: String = ""
    var finalPort: Int = 0

    open fun displayName(): String {
        return name.ifEmpty {
            displayAddress()
        }
    }

    open fun displayAddress(): String {
        return "${serverAddress.wrapIPV6Host()}:$serverPort"
    }

    open fun network(): String {
        return "tcp,udp"
    }

    open val canICMPing get() = true
    open val canTCPing get() = true
    open val needUDPOverTCP get() = false
    open val canMapping get() = true

    open fun isInsecure(): ValidateResult {
        if (serverAddress.isIpAddress()) {
            if (serverAddress.startsWith("127.") || serverAddress.startsWith("::")) {
                return ValidateResult.Secure.Stop
            }
        }
        return ValidateResult.Secure.Continue
    }

    protected fun shouldReturnFromInsecureCheck(result: ValidateResult): Boolean {
        return result !is ValidateResult.Secure || !result.continueChecking
    }

    override fun initializeDefaultValues() {
        if (serverAddress.isEmpty()) {
            serverAddress = "127.0.0.1"
        } else if (serverAddress.startsWith("[") && serverAddress.endsWith("]")) {
            serverAddress = serverAddress.unwrapIPV6Host()
        }
        if (serverPort !in 1..65535) {
            serverPort = defaultPort
        }
        finalAddress = serverAddress
        finalPort = serverPort
    }

    override fun serializeToBuffer(output: BinaryOutput) {
        serialize(output)

        output.writeInt(4)
        output.writeString(name)
        output.writeString(customOutboundJson)
        output.writeString(customConfigJson)

        output.writeBoolean(serverBrutal)
        output.writeBoolean(serverMux)
        output.writeInt(serverMuxType)
        output.writeInt(serverMuxNumber)
        output.writeBoolean(serverMuxPadding)
        output.writeInt(serverMuxStrategy)
    }

    override fun deserializeFromBuffer(input: BinaryInput) {
        deserialize(input)

        val extraVersion = input.readInt()

        name = input.readString()
        customOutboundJson = input.readString()
        customConfigJson = input.readString()

        if (extraVersion >= 2) {
            serverBrutal = input.readBoolean()
        }
        if (extraVersion >= 3) {
            serverMux = input.readBoolean()
            serverMuxType = input.readInt()
            serverMuxNumber = input.readInt()
            serverMuxPadding = input.readBoolean()
        }
        if (extraVersion >= 4) {
            serverMuxStrategy = input.readInt()
        }
    }

    open fun serialize(output: BinaryOutput) {
        output.writeString(serverAddress)
        output.writeInt(serverPort)
    }

    open fun deserialize(input: BinaryInput) {
        serverAddress = input.readString()
        serverPort = input.readInt()
    }

    abstract fun clone(): AbstractBean

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        return BeanConverters.serialize(this)
            .contentEquals(BeanConverters.serialize(other as AbstractBean))
    }

    override fun hashCode(): Int {
        return BeanConverters.serialize(this).contentHashCode()
    }

    override fun toString(): String {
        return "${javaClass.simpleName} ${toJsonStringKxs()}"
    }

    open fun applyFeatureSettings(other: AbstractBean) {}

}
