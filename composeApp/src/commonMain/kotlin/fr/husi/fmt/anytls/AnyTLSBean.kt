package fr.husi.fmt.anytls

import kotlinx.serialization.Serializable as KxsSerializable
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters
import fr.husi.fmt.ValidateResult
import fr.husi.resources.Res
import fr.husi.resources.warn_insecure

@KxsSerializable
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
    var disableReuse: Boolean = false // TODO: add it one day
    var clientMetadata: String = ""
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
    var tlsSpoof: String = ""
    var tlsSpoofMethod: String = ""

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (idleSessionCheckInterval.isEmpty()) idleSessionCheckInterval = "30s"
        if (idleSessionTimeout.isEmpty()) idleSessionTimeout = "30s"
        if (tlsFragmentFallbackDelay.isEmpty()) tlsFragmentFallbackDelay = "500ms"
    }

    override fun isInsecure(): ValidateResult {
        val result = super.isInsecure()
        if (shouldReturnFromInsecureCheck(result)) return result

        if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
        return ValidateResult.Secure.Continue
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(9)

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

        // version 8
        output.writeString(tlsSpoof)
        output.writeString(tlsSpoofMethod)

        // version 9
        output.writeBoolean(disableReuse)
        output.writeString(clientMetadata)
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

        if (version >= 8) {
            tlsSpoof = input.readString()
            tlsSpoofMethod = input.readString()
        }

        if (version >= 9) {
            disableReuse = input.readBoolean()
            clientMetadata = input.readString()
        }
    }

    override fun clone(): AnyTLSBean {
        return KryoConverters.deserialize(AnyTLSBean(), KryoConverters.serialize(this))
    }

    override fun applyFeatureSettings(other: AbstractBean) {
        if (other !is AnyTLSBean) return
        other.allowInsecure = allowInsecure
        other.disableSNI = disableSNI
        other.utlsFingerprint = utlsFingerprint
        other.ech = ech
        other.echConfig = echConfig
        other.tlsFragment = tlsFragment
        other.tlsFragmentFallbackDelay = tlsFragmentFallbackDelay
        other.tlsRecordFragment = tlsRecordFragment
        other.idleSessionCheckInterval = idleSessionCheckInterval
        other.idleSessionTimeout = idleSessionTimeout
        other.minIdleSession = minIdleSession
        other.disableReuse = disableReuse
        other.clientMetadata = clientMetadata
    }

    override val defaultPort get() = 443
}
