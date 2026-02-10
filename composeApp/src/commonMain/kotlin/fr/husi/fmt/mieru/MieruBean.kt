package fr.husi.fmt.mieru

import kotlinx.serialization.Serializable as KxsSerializable
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters

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

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (protocol.isEmpty()) protocol = PROTOCOL_TCP
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(1)
        super.serialize(output)
        output.writeString(protocol)
        output.writeString(username)
        output.writeString(password)
        if (protocol == PROTOCOL_UDP) {
            output.writeInt(mtu)
        }
    }

    override fun deserialize(input: ByteBufferInput) {
        input.readInt()
        super.deserialize(input)
        protocol = input.readString()
        username = input.readString()
        password = input.readString()
        if (protocol == PROTOCOL_TCP) {
            mtu = input.readInt()
        }
    }

    override val canTCPing get() = protocol == PROTOCOL_TCP

    override fun clone(): MieruBean {
        return KryoConverters.deserialize(MieruBean(), KryoConverters.serialize(this))
    }
}
