package fr.husi.fmt.http

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.KryoConverters
import fr.husi.fmt.v2ray.StandardV2RayBean

class HttpBean : StandardV2RayBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<HttpBean>() {
            override fun newInstance(): HttpBean {
                return HttpBean()
            }

            override fun newArray(size: Int): Array<HttpBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var username: String = ""
    var password: String = ""
    var udpOverTcp: Boolean = false

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(2)

        // version 0
        super.serialize(output)
        output.writeString(username)
        output.writeString(password)

        // version 1
        output.writeString(host)
        output.writeString(path)
        output.writeString(headers)

        // version 2
        output.writeBoolean(udpOverTcp)
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        super.deserialize(input)
        username = input.readString()
        password = input.readString()
        if (version >= 1) {
            host = input.readString()
            path = input.readString()
            headers = input.readString()
        }
        if (version >= 2) {
            udpOverTcp = input.readBoolean()
        }
    }

    override fun clone(): HttpBean {
        return KryoConverters.deserialize(HttpBean(), KryoConverters.serialize(this))
    }

    override val defaultPort get() = if (isTLS) 443 else 80
    override val needUDPOverTCP get() = udpOverTcp
}
