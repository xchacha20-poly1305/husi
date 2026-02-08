package io.nekohasekai.sagernet.fmt.trusttunnel

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters

class TrustTunnelBean : AbstractBean() {
    companion object {
        @JvmField
        val CREATOR = object : CREATOR<TrustTunnelBean>() {
            override fun newInstance(): TrustTunnelBean {
                return TrustTunnelBean()
            }

            override fun newArray(size: Int): Array<TrustTunnelBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var username: String = ""
    var password: String = ""
    var healthCheck: Boolean = false

    var quic: Boolean = false
    var quicCongestionControl: String = "bbr"

    var serverName: String = ""
    var alpn: String = ""
    var certificates: String = ""
    var certPublicKeySha256: String = ""
    var utlsFingerprint: String = ""
    var allowInsecure: Boolean = false
    var tlsFragment: Boolean = false
    var tlsFragmentFallbackDelay: String = ""
    var tlsRecordFragment: Boolean = false
    var ech: Boolean = false
    var echConfig: String = ""
    var echQueryServerName: String = ""
    var clientCert: String = ""
    var clientKey: String = ""

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeString(username)
        output.writeString(password)
        output.writeBoolean(healthCheck)
        output.writeBoolean(quic)
        output.writeString(quicCongestionControl)
        output.writeString(serverName)
        output.writeString(alpn)
        output.writeString(certificates)
        output.writeString(certPublicKeySha256)
        output.writeString(utlsFingerprint)
        output.writeBoolean(allowInsecure)
        output.writeBoolean(tlsFragment)
        output.writeString(tlsFragmentFallbackDelay)
        output.writeBoolean(tlsRecordFragment)
        output.writeBoolean(ech)
        output.writeString(echConfig)
        output.writeString(echQueryServerName)
        output.writeString(clientCert)
        output.writeString(clientKey)
    }

    override fun deserialize(input: ByteBufferInput) {
        input.readInt()
        super.deserialize(input)
        username = input.readString()
        password = input.readString()
        healthCheck = input.readBoolean()
        quic = input.readBoolean()
        quicCongestionControl = input.readString()
        serverName = input.readString()
        alpn = input.readString()
        certificates = input.readString()
        certPublicKeySha256 = input.readString()
        utlsFingerprint = input.readString()
        allowInsecure = input.readBoolean()
        tlsFragment = input.readBoolean()
        tlsFragmentFallbackDelay = input.readString()
        tlsRecordFragment = input.readBoolean()
        ech = input.readBoolean()
        echConfig = input.readString()
        echQueryServerName = input.readString()
        clientCert = input.readString()
        clientKey = input.readString()
    }

    override fun clone(): TrustTunnelBean {
        return KryoConverters.deserialize(TrustTunnelBean(), KryoConverters.serialize(this))
    }

    override val defaultPort = 443
}
