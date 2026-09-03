package fr.husi.fmt.mieru

import kotlinx.serialization.Serializable as KxsSerializable
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.BeanConverters
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput

@KxsSerializable
class MieruBean : AbstractBean() {

    companion object {
        const val PROTOCOL_TCP = "TCP"
        const val PROTOCOL_UDP = "UDP"

        @JvmField
        val CREATOR = object : CREATOR<MieruBean>() {
            override fun newInstance(): MieruBean {
                return MieruBean()
            }

            override fun newArray(size: Int): Array<MieruBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var protocol: String = PROTOCOL_TCP
    var username: String = ""
    var password: String = ""
    var mtu: Int = 1400
    var trafficPattern: String = ""

    override val canSelfProtect get() = true

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (protocol.isEmpty()) protocol = PROTOCOL_TCP
    }

    override fun serialize(output: BinaryOutput) {
        output.writeInt(2)
        super.serialize(output)
        output.writeString(protocol)
        output.writeString(username)
        output.writeString(password)
        if (protocol == PROTOCOL_UDP) {
            output.writeInt(mtu)
        }
        output.writeString(trafficPattern)
    }

    override fun deserialize(input: BinaryInput) {
        val version = input.readInt()
        super.deserialize(input)
        protocol = input.readString().uppercase()
        username = input.readString()
        password = input.readString()
        if (protocol == PROTOCOL_UDP) {
            mtu = input.readInt()
        }
        if (version >= 2) {
            trafficPattern = input.readString()
        }
    }

    override val canTCPing get() = protocol == PROTOCOL_TCP

    override fun clone(): MieruBean {
        return BeanConverters.deserialize(MieruBean(), BeanConverters.serialize(this))
    }
}
