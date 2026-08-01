package fr.husi.fmt.hysteria

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters
import fr.husi.fmt.ValidateResult
import fr.husi.ktx.wrapIPV6Host
import fr.husi.resources.Res
import fr.husi.resources.warn_hysteria_legacy
import fr.husi.resources.warn_insecure
import kotlinx.serialization.Serializable as KxsSerializable

@KxsSerializable
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

        const val CONGESTION_CONTROL_BBR = "bbr"
        const val CONGESTION_CONTROL_RENO = "reno"

        const val BBR_PROFILE_CONSERVATIVE = 0
        const val BBR_PROFILE_STANDARD = 1
        const val BBR_PROFILE_AGGRESSIVE = 2

        const val OBFS_TYPE_NONE = ""
        const val OBFS_TYPE_SALAMANDER = "salamander"
        const val OBFS_TYPE_GECKO = "gecko"

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

    // Hy2
    var congestionControl: String = CONGESTION_CONTROL_BBR
    var bbrProfile: Int = BBR_PROFILE_STANDARD

    var disableChromeParrot: Boolean = true

    // Hy2 obfuscation
    var obfsType: String = OBFS_TYPE_NONE
    var obfsPassword: String = ""
    var geckoMinPacketSize: Int = 0
    var geckoMaxPacketSize: Int = 0

    // QUIC
    var idleTimeout: String = ""
    var keepAlivePeriod: String = ""
    var maxConcurrentStreams: Int = 0
    var initialPacketSize: Int = 0

    override val canMapping get() = protocol != PROTOCOL_FAKETCP

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (hopInterval.isEmpty()) hopInterval = "10s"
        if (serverPorts.isEmpty()) serverPorts = "443"
    }

    override fun isInsecure(): ValidateResult {
        val result = super.isInsecure()
        if (shouldReturnFromInsecureCheck(result)) return result

        if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
        if (protocolVersion < PROTOCOL_VERSION_2) {
            return ValidateResult.Deprecated(Res.string.warn_hysteria_legacy)
        }
        return ValidateResult.Secure.Continue
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(9)
        super.serialize(output)

        output.writeInt(protocolVersion)

        output.writeInt(authPayloadType)
        output.writeString(authPayload)
        output.writeInt(protocol)
        output.writeString(obfsPassword)
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

        // version 6
        output.writeString(congestionControl)
        output.writeInt(bbrProfile)

        // version 7
        output.writeString(idleTimeout)
        output.writeString(keepAlivePeriod)
        output.writeInt(maxConcurrentStreams)
        output.writeInt(initialPacketSize)

        // version 8
        output.writeString(obfsType)
        output.writeInt(geckoMinPacketSize)
        output.writeInt(geckoMaxPacketSize)

        // version 9
        output.writeBoolean(disableChromeParrot)
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        super.deserialize(input)
        protocolVersion = input.readInt()

        authPayloadType = input.readInt()
        authPayload = input.readString()
        protocol = input.readInt()
        obfsPassword = input.readString()
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

        if (version >= 6) {
            congestionControl = input.readString()
            bbrProfile = input.readInt()
        }

        if (version >= 7) {
            idleTimeout = input.readString()
            keepAlivePeriod = input.readString()
            maxConcurrentStreams = input.readInt()
            initialPacketSize = input.readInt()
        }

        if (version >= 8) {
            obfsType = input.readString()
            geckoMinPacketSize = input.readInt()
            geckoMaxPacketSize = input.readInt()
        } else {
            if (obfsPassword.isNotEmpty()) {
                obfsType = OBFS_TYPE_SALAMANDER
            }
        }

        if (version >= 9) {
            disableChromeParrot = input.readBoolean()
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
        other.congestionControl = congestionControl
        other.bbrProfile = bbrProfile
        other.disableChromeParrot = disableChromeParrot
        other.idleTimeout = idleTimeout
        other.keepAlivePeriod = keepAlivePeriod
        other.maxConcurrentStreams = maxConcurrentStreams
        other.initialPacketSize = initialPacketSize
        other.geckoMinPacketSize = geckoMinPacketSize
        other.geckoMaxPacketSize = geckoMaxPacketSize
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
