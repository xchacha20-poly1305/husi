package fr.husi.fmt.hysteria

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters
import fr.husi.ktx.wrapIPV6Host

class HysteriaBean : AbstractBean() {
    companion object {
        const val PROTOCOL_VERSION_1 = 1
        const val PROTOCOL_VERSION_2 = 2

        const val TYPE_NONE = 0
        const val TYPE_STRING = 1
        const val TYPE_BASE64 = 2
        const val PROTOCOL_UDP = 0
        const val PROTOCOL_FAKETCP = 1
        const val PROTOCOL_WECHAT_VIDEO = 2

        @JvmField
        val CREATOR = object : CREATOR<HysteriaBean>() {
            override fun newInstance(): HysteriaBean {
                return HysteriaBean()
            }

            override fun newArray(size: Int): Array<HysteriaBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var protocolVersion: Int = PROTOCOL_VERSION_2

    // Use serverPorts instead of serverPort
    var serverPorts: String = "443"
    var ech: Boolean = false
    var echConfig: String = ""
    var echQueryServerName: String = ""
    var authPayload: String = ""
    var obfuscation: String = ""
    var sni: String = ""
    var certificates: String = ""
    var certPublicKeySha256: String = ""
    var disableSNI: Boolean = false

    // HY1
    var allowInsecure: Boolean = false
    var streamReceiveWindow: Int = 0
    var connectionReceiveWindow: Int = 0
    var disableMtuDiscovery: Boolean = false

    // Since serialize version 1, hopInterval change to string.
    var hopInterval: String = "10s"
    var alpn: String = ""
    var authPayloadType: Int = TYPE_NONE
    var protocol: Int = PROTOCOL_UDP
    var clientCert: String = ""
    var clientKey: String = ""

    override val canMapping get() = protocol != PROTOCOL_FAKETCP

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (hopInterval.isEmpty()) hopInterval = "10s"
        if (serverPorts.isEmpty()) serverPorts = "443"
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(5)
        super.serialize(output)

        output.writeInt(protocolVersion)

        output.writeInt(authPayloadType)
        output.writeString(authPayload)
        output.writeInt(protocol)
        output.writeString(obfuscation)
        output.writeString(sni)
        output.writeString(alpn)

        output.writeBoolean(allowInsecure)

        output.writeString(certificates)
        output.writeInt(streamReceiveWindow)
        output.writeInt(connectionReceiveWindow)
        output.writeBoolean(disableMtuDiscovery)
        output.writeString(hopInterval)
        output.writeString(serverPorts)

        output.writeBoolean(ech)
        output.writeString(echConfig)

        output.writeBoolean(disableSNI)

        // version 3
        output.writeString(clientCert)
        output.writeString(clientKey)

        // version 4
        output.writeString(certPublicKeySha256)

        // version 5
        output.writeString(echQueryServerName)
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        super.deserialize(input)
        protocolVersion = input.readInt()

        authPayloadType = input.readInt()
        authPayload = input.readString()
        protocol = input.readInt()
        obfuscation = input.readString()
        sni = input.readString()
        alpn = input.readString()
        allowInsecure = input.readBoolean()
        certificates = input.readString()
        streamReceiveWindow = input.readInt()
        connectionReceiveWindow = input.readInt()
        disableMtuDiscovery = input.readBoolean()
        hopInterval = if (version < 1) {
            "${input.readInt()}s"
        } else {
            input.readString()
        }
        serverPorts = input.readString()

        ech = input.readBoolean()
        echConfig = input.readString()

        if (version >= 2) {
            disableSNI = input.readBoolean()
        }

        if (version >= 3) {
            clientCert = input.readString()
            clientKey = input.readString()
        }

        if (version >= 4) {
            certPublicKeySha256 = input.readString()
        }

        if (version >= 5) {
            echQueryServerName = input.readString()
        }
    }

    override fun applyFeatureSettings(other: AbstractBean) {
        if (other !is HysteriaBean) return
        other.allowInsecure = allowInsecure
        other.disableSNI = disableSNI
        other.disableMtuDiscovery = disableMtuDiscovery
        other.hopInterval = hopInterval
        other.ech = ech
        other.echConfig = echConfig
    }

    override val defaultPort get() = 443

    override val canTCPing
        get() = when (protocolVersion) {
            PROTOCOL_VERSION_1 -> protocol == PROTOCOL_FAKETCP
            else -> false
        }

    override fun displayAddress(): String {
        return "${serverAddress.wrapIPV6Host()}:$serverPorts"
    }

    override fun clone(): HysteriaBean {
        return KryoConverters.deserialize(HysteriaBean(), KryoConverters.serialize(this))
    }

}
