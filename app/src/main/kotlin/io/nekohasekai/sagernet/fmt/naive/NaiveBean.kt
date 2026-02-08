package io.nekohasekai.sagernet.fmt.naive

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters

class NaiveBean : AbstractBean() {

    companion object {
        const val PROTO_HTTPS = "https"
        const val PROTO_QUIC = "quic"

        @JvmField
        val CREATOR = object : CREATOR<NaiveBean>() {
            override fun newInstance(): NaiveBean {
                return NaiveBean()
            }

            override fun newArray(size: Int): Array<NaiveBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var proto: String = PROTO_HTTPS
    var username: String = ""
    var password: String = ""
    var extraHeaders: String = ""
    var sni: String = ""
    var insecureConcurrency: Int = 0
    // sing-box server
    var udpOverTcp: Boolean = false
    // https://github.com/klzgrad/naiveproxy/blob/76e7bbed0fdd349fb8a8890cd082e90072dab734/USAGE.txt#L110
    // https://tldr.fail/
    var noPostQuantum: Boolean = false
    var quicCongestionControl: String = ""
    var enableEch: Boolean = false
    var echConfig: String = ""
    var echQueryServerName: String = ""

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (proto.isEmpty()) proto = PROTO_HTTPS
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(3)
        super.serialize(output)

        // version 0
        output.writeString(proto)
        output.writeString(username)
        output.writeString(password)
        output.writeString(extraHeaders)
        output.writeString(sni)
        output.writeInt(insecureConcurrency)
        output.writeBoolean(udpOverTcp)

        // version 1
        output.writeBoolean(noPostQuantum)

        // version 2
        output.writeBoolean(enableEch)
        output.writeString(echConfig)
        output.writeString(echQueryServerName)

        // version 3
        output.writeString(quicCongestionControl)
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        super.deserialize(input)
        proto = input.readString()
        username = input.readString()
        password = input.readString()
        extraHeaders = input.readString()
        sni = input.readString()
        insecureConcurrency = input.readInt()
        udpOverTcp = input.readBoolean()

        if (version >= 1) {
            noPostQuantum = input.readBoolean()
        }

        if (version >= 2) {
            enableEch = input.readBoolean()
            echConfig = input.readString()
            echQueryServerName = input.readString()
        }

        if (version >= 3) {
            quicCongestionControl = input.readString()
        }
    }

    override fun clone(): NaiveBean {
        return KryoConverters.deserialize(NaiveBean(), KryoConverters.serialize(this))
    }

    override val defaultPort = 443
    override val canTCPing get() = proto != PROTO_QUIC
    override val needUDPOverTCP get() =  udpOverTcp
}
