package fr.husi.fmt.socks

import kotlinx.serialization.Serializable as KxsSerializable
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.BeanConverters
import fr.husi.fmt.ValidateResult
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput
import fr.husi.resources.Res
import fr.husi.resources.warn_not_encrypted

@KxsSerializable
class SOCKSBean : AbstractBean() {

    companion object {
        const val PROTOCOL_SOCKS4 = 0
        const val PROTOCOL_SOCKS4A = 1
        const val PROTOCOL_SOCKS5 = 2

        @JvmField
        val CREATOR = object : CREATOR<SOCKSBean>() {
            override fun newInstance(): SOCKSBean {
                return SOCKSBean()
            }

            override fun newArray(size: Int): Array<SOCKSBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var protocol: Int = PROTOCOL_SOCKS5
    var udpOverTcp: Boolean = false
    var username: String = ""
    var password: String = ""

    fun protocolVersion(): Int {
        return when (protocol) {
            PROTOCOL_SOCKS4,
            PROTOCOL_SOCKS4A,
                -> 4

            else -> 5
        }
    }

    fun protocolName(): String {
        return when (protocol) {
            PROTOCOL_SOCKS4 -> "SOCKS4"
            PROTOCOL_SOCKS4A -> "SOCKS4A"
            else -> "SOCKS5"
        }
    }

    fun protocolVersionName(): String {
        return when (protocol) {
            PROTOCOL_SOCKS4 -> "4"
            PROTOCOL_SOCKS4A -> "4a"
            else -> "5"
        }
    }

    override fun network(): String {
        if (protocol < PROTOCOL_SOCKS5) return "tcp"
        return super.network()
    }

    override fun isInsecure(): ValidateResult {
        val result = super.isInsecure()
        if (shouldReturnFromInsecureCheck(result)) return result

        return ValidateResult.Insecure(Res.string.warn_not_encrypted)
    }

    override fun serialize(output: BinaryOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeInt(protocol)
        output.writeString(username)
        output.writeString(password)
        output.writeBoolean(udpOverTcp)
    }

    override fun deserialize(input: BinaryInput) {
        input.readInt()
        super.deserialize(input)
        protocol = input.readInt()
        username = input.readString()
        password = input.readString()
        udpOverTcp = input.readBoolean()
    }

    override fun clone(): SOCKSBean {
        return BeanConverters.deserialize(SOCKSBean(), BeanConverters.serialize(this))
    }

    override val defaultPort get() = 1080

    override val needUDPOverTCP get() = udpOverTcp
}
