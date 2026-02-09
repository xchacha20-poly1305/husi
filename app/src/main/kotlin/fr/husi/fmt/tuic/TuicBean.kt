package fr.husi.fmt.tuic

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters

class TuicBean : AbstractBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<TuicBean>() {
            override fun newInstance(): TuicBean {
                return TuicBean()
            }

            override fun newArray(size: Int): Array<TuicBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var token: String = ""
    var certificates: String = ""
    var certPublicKeySha256: String = ""
    var udpRelayMode: String = "native"
    var congestionController: String = "cubic"
    var alpn: String = ""
    var disableSNI: Boolean = false
    var zeroRTT: Boolean = false
    var mtu: Int = 1400

    // TUIC zep
    var sni: String = ""
    var allowInsecure: Boolean = false
    var customJSON: String = ""
    var uuid: String = ""

    var ech: Boolean = false
    var echConfig: String = ""
    var echQueryServerName: String = ""

    // mTLS
    var clientCert: String = ""
    var clientKey: String = ""

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (udpRelayMode.isEmpty()) udpRelayMode = "native"
        if (congestionController.isEmpty()) congestionController = "bbr"
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(3)

        // version 0
        super.serialize(output)
        output.writeString(token)
        output.writeString(certificates)
        output.writeString(udpRelayMode)
        output.writeString(congestionController)
        output.writeString(alpn)
        output.writeBoolean(disableSNI)
        output.writeBoolean(zeroRTT)
        output.writeInt(mtu)
        output.writeString(sni)
        output.writeBoolean(allowInsecure)
        output.writeString(customJSON)
        output.writeString(uuid)
        output.writeBoolean(ech)
        output.writeString(echConfig)

        // version 1
        output.writeString(certPublicKeySha256)

        // version 2
        output.writeString(clientCert)
        output.writeString(clientKey)

        // version 3
        output.writeString(echQueryServerName)
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        super.deserialize(input)
        token = input.readString()
        certificates = input.readString()
        udpRelayMode = input.readString()
        congestionController = input.readString()
        alpn = input.readString()
        disableSNI = input.readBoolean()
        zeroRTT = input.readBoolean()
        mtu = input.readInt()
        sni = input.readString()
        allowInsecure = input.readBoolean()
        customJSON = input.readString()
        uuid = input.readString()

        ech = input.readBoolean()
        echConfig = input.readString()

        if (version >= 1) {
            certPublicKeySha256 = input.readString()
        }

        if (version >= 2) {
            clientCert = input.readString()
            clientKey = input.readString()
        }

        if (version >= 3) {
            echQueryServerName = input.readString()
        }
    }

    override fun applyFeatureSettings(other: AbstractBean) {
        if (other !is TuicBean) return
    }

    override val defaultPort get() = 443

    override val canTCPing get() = false

    override fun clone(): TuicBean {
        return KryoConverters.deserialize(TuicBean(), KryoConverters.serialize(this))
    }

}
