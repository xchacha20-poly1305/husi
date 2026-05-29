package fr.husi.fmt.shadowquic

import kotlinx.serialization.Serializable as KxsSerializable
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters
import fr.husi.fmt.ValidateResult
import fr.husi.resources.Res
import fr.husi.resources.warn_quic_0_rtt

@KxsSerializable
class ShadowQUICBean : AbstractBean() {

    companion object {
        const val SUB_PROTOCOL_SHADOW_QUIC = 0
        const val SUB_PROTOCOL_SUNNY_QUIC = 1

        const val CONGESTION_CONTROL_BRUTAL = "brutal"

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
    var blackholeDetection: Boolean = false
    var subProtocol: Int = SUB_PROTOCOL_SHADOW_QUIC

    // Sunny QUIC
    var extraPaths: String = ""
    var maxPaths: Int = 0
    var certificates: String = ""

    override fun isInsecure(): ValidateResult {
        val result = super.isInsecure()
        if (shouldReturnFromInsecureCheck(result)) return result

        if (zeroRTT) return ValidateResult.Insecure(Res.string.warn_quic_0_rtt)
        return ValidateResult.Secure.Continue
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(4)
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

        // version 3
        output.writeString(certificates)

        // version 4
        output.writeBoolean(blackholeDetection)
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
        congestionControl = input.readString().replace("_", "-") // Fix invalid old value `new_reno`
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

        if (version >= 3) {
            certificates = input.readString()
        }

        if (version >= 4) {
            blackholeDetection = input.readBoolean()
        }
    }

    override fun applyFeatureSettings(other: AbstractBean) {
        if (other !is ShadowQUICBean) return
        other.initialMTU = initialMTU
        other.minimumMTU = minimumMTU
        other.congestionControl = congestionControl
        other.zeroRTT = zeroRTT
        other.udpOverStream = udpOverStream
        other.gso = gso
        other.keepAliveInterval = keepAliveInterval
        other.mtuDiscovery = mtuDiscovery
        other.blackholeDetection = blackholeDetection
        other.subProtocol = subProtocol
        other.extraPaths = extraPaths
        other.maxPaths = maxPaths
        other.certificates = certificates
    }

    override fun clone(): AbstractBean {
        return KryoConverters.deserialize(ShadowQUICBean(), KryoConverters.serialize(this))
    }

    override val defaultPort get() = 443
    override val canTCPing get() = false

    fun displayType(): String {
        return if (subProtocol == SUB_PROTOCOL_SHADOW_QUIC) {
            "ShadowQUIC"
        } else {
            "SunnyQUIC"
        }
    }
}
