package io.nekohasekai.sagernet.fmt.anytls

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters

class AnyTLSBean : AbstractBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<AnyTLSBean>() {
            override fun newInstance(): AnyTLSBean {
                return AnyTLSBean()
            }

            override fun newArray(size: Int): Array<AnyTLSBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var password: String = ""
    var idleSessionCheckInterval: String = "30s"
    var idleSessionTimeout: String = "30s"
    var minIdleSession: Int = 0
    var serverName: String = ""
    var alpn: String = ""
    var certificates: String = ""
    var certPublicKeySha256: String = ""
    var utlsFingerprint: String = ""
    var allowInsecure: Boolean = false
    var disableSNI: Boolean = false
    var tlsFragment: Boolean = false
    var tlsFragmentFallbackDelay: String = "500ms"
    var tlsRecordFragment: Boolean = false
    var ech: Boolean = false
    var echConfig: String = ""
    var echQueryServerName: String = ""
    var clientCert: String = ""
    var clientKey: String = ""

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (idleSessionCheckInterval.isEmpty()) idleSessionCheckInterval = "30s"
        if (idleSessionTimeout.isEmpty()) idleSessionTimeout = "30s"
        if (tlsFragmentFallbackDelay.isEmpty()) tlsFragmentFallbackDelay = "500ms"
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(7)

        // version 0
        super.serialize(output)
        output.writeString(password)
        output.writeString(serverName)
        output.writeString(alpn)
        output.writeString(certificates)
        output.writeString(utlsFingerprint)
        output.writeBoolean(allowInsecure)
        output.writeString(echConfig)

        // version 1
        output.writeString(idleSessionCheckInterval)
        output.writeString(idleSessionTimeout)
        output.writeInt(minIdleSession)

        // version 2
        output.writeBoolean(ech)

        // version 3
        output.writeBoolean(tlsFragment)
        output.writeString(tlsFragmentFallbackDelay)
        output.writeBoolean(tlsRecordFragment)

        // version 4
        output.writeBoolean(disableSNI)

        // version 5
        output.writeString(certPublicKeySha256)

        // version 6
        output.writeString(clientCert)
        output.writeString(clientKey)

        // version 7
        output.writeString(echQueryServerName)
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        super.deserialize(input)
        password = input.readString()
        serverName = input.readString()
        alpn = input.readString()
        certificates = input.readString()
        utlsFingerprint = input.readString()
        allowInsecure = input.readBoolean()
        echConfig = input.readString()

        if (version >= 1) {
            idleSessionCheckInterval = input.readString()
            idleSessionTimeout = input.readString()
            minIdleSession = input.readInt()
        }

        if (version >= 2) {
            ech = input.readBoolean()
        }

        if (version >= 3) {
            tlsFragment = input.readBoolean()
            tlsFragmentFallbackDelay = input.readString()
            tlsRecordFragment = input.readBoolean()
        }

        if (version >= 4) {
            disableSNI = input.readBoolean()
        }

        if (version >= 5) {
            certPublicKeySha256 = input.readString()
        }

        if (version >= 6) {
            clientCert = input.readString()
            clientKey = input.readString()
        }

        if (version >= 7) {
            echQueryServerName = input.readString()
        }
    }

    override fun clone(): AnyTLSBean {
        return KryoConverters.deserialize(AnyTLSBean(), KryoConverters.serialize(this))
    }

    override val defaultPort = 443
}
