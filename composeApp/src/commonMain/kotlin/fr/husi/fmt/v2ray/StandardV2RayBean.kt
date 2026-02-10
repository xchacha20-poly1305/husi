package fr.husi.fmt.v2ray

import kotlinx.serialization.Serializable as KxsSerializable
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.AbstractBean

@KxsSerializable
abstract class StandardV2RayBean : AbstractBean() {

    companion object {
        const val PACKET_ENCODING_NONE = 0
        const val PACKET_ENCODING_PACKETADDR = 1
        const val PACKET_ENCODING_XUDP = 2
    }

    var uuid: String = ""
    var encryption: String = ""

    // "V2Ray Transport" tcp/http/ws/quic/grpc/httpupgrade
    var v2rayTransport: String = ""
    var host: String = ""
    var path: String = ""
    var headers: String = ""
    var security: String = ""
    var sni: String = ""
    var alpn: String = ""
    var utlsFingerprint: String = ""
    var allowInsecure: Boolean = false
    var disableSNI: Boolean = false
    var fragment: Boolean = false
    var fragmentFallbackDelay: String = "500ms"
    var recordFragment: Boolean = false
    var certificates: String = ""
    var certPublicKeySha256: String = ""
    var clientCert: String = ""
    var clientKey: String = ""
    var realityPublicKey: String = ""
    var realityShortID: String = ""
    var ech: Boolean = false
    var echConfig: String = ""
    var echQueryServerName: String = ""
    var wsMaxEarlyData: Int = 0
    var earlyDataHeaderName: String = ""
    var packetEncoding: Int = PACKET_ENCODING_NONE

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()

        if (v2rayTransport == "tcp") v2rayTransport = ""
        if (v2rayTransport == "h2") v2rayTransport = "http"
        v2rayTransport = v2rayTransport.lowercase()

        if (fragmentFallbackDelay.isEmpty()) fragmentFallbackDelay = "500ms"
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(11)
        super.serialize(output)

        output.writeString(uuid)
        output.writeString(encryption)
        when (this) {
            is VMessBean -> output.writeInt(alterId)
            is VLESSBean -> output.writeString(flow)
        }

        output.writeString(v2rayTransport)
        when (v2rayTransport) {
            "", "tcp", "quic" -> {}

            "ws" -> {
                output.writeString(host)
                output.writeString(path)
                output.writeInt(wsMaxEarlyData)
                output.writeString(earlyDataHeaderName)
                output.writeString(headers)
            }

            "http" -> {
                output.writeString(host)
                output.writeString(path)
                output.writeString(headers)
            }

            "grpc" -> {
                output.writeString(path)
                output.writeString(host)
                output.writeString(path)
                output.writeString(headers)
            }

            "httpupgrade" -> {
                output.writeString(host)
                output.writeString(path)
                output.writeString(headers)
            }
        }

        output.writeString(security)
        if (security == "tls") {
            output.writeString(sni)
            output.writeString(alpn)
            output.writeString(certificates)
            output.writeBoolean(allowInsecure)
            output.writeString(utlsFingerprint)
            output.writeString(realityPublicKey)
            output.writeString(realityShortID)
            output.writeBoolean(ech)
            output.writeString(echConfig)
            output.writeBoolean(fragment)
            output.writeString(fragmentFallbackDelay)
            output.writeBoolean(recordFragment)
            output.writeBoolean(disableSNI)

            output.writeString(certPublicKeySha256)
            output.writeString(clientCert)
            output.writeString(clientKey)
            output.writeString(echQueryServerName)
        }

        output.writeInt(packetEncoding)

        if (this is VMessBean) {
            output.writeBoolean(authenticatedLength)
        }
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        super.deserialize(input)
        uuid = input.readString()
        encryption = input.readString()
        if (this is VMessBean) {
            alterId = input.readInt()
        }
        if (version >= 9 && this is VLESSBean) {
            flow = input.readString()
        }

        v2rayTransport = input.readString()
        when (v2rayTransport) {
            "", "tcp" -> v2rayTransport = ""
        }
        when (v2rayTransport) {
            "", "quic" -> {
                Unit
            }

            "ws" -> {
                host = input.readString()
                path = input.readString()
                wsMaxEarlyData = input.readInt()
                earlyDataHeaderName = input.readString()
                if (version >= 5) headers = input.readString()
            }

            "http" -> {
                host = input.readString()
                path = input.readString()
                if (version >= 5) headers = input.readString()
            }

            "grpc" -> {
                path = input.readString()
                host = input.readString()
                path = input.readString()
                if (version >= 5) headers = input.readString()
            }

            "httpupgrade" -> {
                host = input.readString()
                path = input.readString()
                if (version >= 5) headers = input.readString()
            }
        }

        security = input.readString()
        if (security == "tls") {
            sni = input.readString()
            alpn = input.readString()
            certificates = input.readString()
            allowInsecure = input.readBoolean()
            utlsFingerprint = input.readString()
            if (version < 4 && utlsFingerprint.startsWith("chrome")) {
                utlsFingerprint = "chrome"
            }
            realityPublicKey = input.readString()
            realityShortID = input.readString()
            ech = input.readBoolean()
            echConfig = input.readString()

            if (version >= 6) {
                fragment = input.readBoolean()
                fragmentFallbackDelay = input.readString()
                recordFragment = input.readBoolean()
            }

            if (version >= 7) {
                disableSNI = input.readBoolean()
            }

            if (version >= 8) {
                certPublicKeySha256 = input.readString()
            }

            if (version >= 10) {
                clientCert = input.readString()
                clientKey = input.readString()
            }

            if (version >= 11) {
                echQueryServerName = input.readString()
            }
        }

        packetEncoding = input.readInt()

        if (this is VMessBean) {
            if (version >= 1) authenticatedLength = input.readBoolean()
        }

        if (version < 3) {
            input.readInt()
            return
        }
    }

    override fun applyFeatureSettings(other: AbstractBean) {
        if (other !is StandardV2RayBean) return
        other.allowInsecure = allowInsecure
        other.disableSNI = disableSNI
        other.utlsFingerprint = utlsFingerprint
        other.packetEncoding = packetEncoding
        other.ech = ech
        other.echConfig = echConfig
        other.fragment = fragment
        other.fragmentFallbackDelay = fragmentFallbackDelay
        other.recordFragment = recordFragment
    }

    override val canTCPing get() = v2rayTransport != "quic"

    val isTLS get() = security == "tls"
}
