package io.nekohasekai.sagernet.fmt.shadowquic

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters

class ShadowQUICBean : AbstractBean() {

    companion object {
        const val SUB_PROTOCOL_SHADOW_QUIC = 0
        const val SUB_PROTOCOL_SUNNY_QUIC = 1

        @JvmField
        val CREATOR = object : CREATOR<ShadowQUICBean>() {
            override fun newInstance(): ShadowQUICBean {
                return ShadowQUICBean()
            }

            override fun newArray(size: Int): Array<ShadowQUICBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var username: String = "" // JLS IV
    var password: String = "" // JLS password
    var sni: String = ""
    var alpn: String = ""
    var initialMTU: Int = 1300
    var minimumMTU: Int = 1290
    var congestionControl: String = "bbr"
    var zeroRTT: Boolean = false
    var udpOverStream: Boolean = false
    var gso: Boolean = false
    var keepAliveInterval: Int = 0
    var mtuDiscovery: Boolean = false
    var subProtocol: Int = SUB_PROTOCOL_SHADOW_QUIC

    // Sunny QUIC
    var extraPaths: String = ""
    var maxPaths: Int = 0

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(2)
        super.serialize(output)
        output.writeString(password)
        output.writeString(username)
        output.writeString(sni)
        output.writeString(alpn)
        output.writeInt(initialMTU)
        output.writeInt(minimumMTU)
        output.writeString(congestionControl)
        output.writeBoolean(zeroRTT)
        output.writeBoolean(udpOverStream)
        output.writeBoolean(gso)

        // version 1
        output.writeInt(subProtocol)

        // version 2
        output.writeInt(keepAliveInterval)
        output.writeBoolean(mtuDiscovery)
        output.writeString(extraPaths)
        output.writeInt(maxPaths)
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        super.deserialize(input)
        password = input.readString()
        username = input.readString()
        sni = input.readString()
        alpn = input.readString()
        initialMTU = input.readInt()
        minimumMTU = input.readInt()
        congestionControl = input.readString()
        zeroRTT = input.readBoolean()
        udpOverStream = input.readBoolean()

        if (version >= 1) {
            gso = input.readBoolean()
            subProtocol = input.readInt()
        }

        if (version >= 2) {
            keepAliveInterval = input.readInt()
            mtuDiscovery = input.readBoolean()
            extraPaths = input.readString()
            maxPaths = input.readInt()
        }
    }

    override fun applyFeatureSettings(other: AbstractBean) {
        if (other !is ShadowQUICBean) return
        other.password = password
        other.username = username
        other.sni = sni
        other.alpn = alpn
        other.initialMTU = initialMTU
        other.minimumMTU = minimumMTU
        other.congestionControl = congestionControl
        other.zeroRTT = zeroRTT
        other.udpOverStream = udpOverStream
    }

    override fun clone(): AbstractBean {
        return KryoConverters.deserialize(ShadowQUICBean(), KryoConverters.serialize(this))
    }

    override val defaultPort = 443
    override val canTCPing = false

    fun displayType(): String {
        return if (subProtocol == SUB_PROTOCOL_SHADOW_QUIC) {
            "ShadowQUIC"
        } else {
            "SunnyQUIC"
        }
    }
}
