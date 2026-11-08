package fr.husi.fmt.masque

import fr.husi.fmt.AbstractBean
import fr.husi.fmt.BeanConverters
import fr.husi.fmt.HttpVersion
import fr.husi.fmt.ValidateResult
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput
import fr.husi.resources.Res
import fr.husi.resources.warn_insecure
import fr.husi.resources.warn_not_encrypted
import kotlinx.serialization.Serializable as KxsSerializable

@KxsSerializable
class MASQUEBean : AbstractBean() {

    companion object {
        const val DEFAULT_MTU = 1280

        @JvmField
        val CREATOR = object : CREATOR<MASQUEBean>() {
            override fun newInstance(): MASQUEBean {
                return MASQUEBean()
            }

            override fun newArray(size: Int): Array<MASQUEBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var username: String = ""
    var password: String = ""
    var path: String = ""
    var headers: String = ""
    var httpVersion: Int = HttpVersion.HTTP_3
    var disableVersionFallback: Boolean = false
    var enableTLS: Boolean = true
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
    var mtu: Int = DEFAULT_MTU

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (!HttpVersion.isValid(httpVersion)) {
            httpVersion = HttpVersion.HTTP_3
        }
        if (!enableTLS && httpVersion > HttpVersion.HTTP_2) {
            httpVersion = HttpVersion.HTTP_2
        }
        if (tlsFragmentFallbackDelay.isEmpty()) tlsFragmentFallbackDelay = "500ms"
    }

    override fun isInsecure(): ValidateResult {
        val result = super.isInsecure()
        if (shouldReturnFromInsecureCheck(result)) return result

        if (!enableTLS) return ValidateResult.Insecure(Res.string.warn_not_encrypted)
        if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
        return ValidateResult.Secure.Continue
    }

    override fun serialize(output: BinaryOutput) {
        output.writeInt(0)

        // version 0
        super.serialize(output)
        output.writeString(username)
        output.writeString(password)
        output.writeString(path)
        output.writeString(headers)
        output.writeInt(httpVersion)
        output.writeBoolean(disableVersionFallback)
        output.writeBoolean(enableTLS)
        output.writeString(serverName)
        output.writeString(alpn)
        output.writeString(certificates)
        output.writeString(certPublicKeySha256)
        output.writeString(utlsFingerprint)
        output.writeBoolean(allowInsecure)
        output.writeBoolean(disableSNI)
        output.writeBoolean(tlsFragment)
        output.writeString(tlsFragmentFallbackDelay)
        output.writeBoolean(tlsRecordFragment)
        output.writeBoolean(ech)
        output.writeString(echConfig)
        output.writeString(echQueryServerName)
        output.writeString(clientCert)
        output.writeString(clientKey)
        output.writeString(tlsSpoof)
        output.writeString(tlsSpoofMethod)
        output.writeInt(mtu)
    }

    override fun deserialize(input: BinaryInput) {
        input.readInt()
        super.deserialize(input)
        username = input.readString()
        password = input.readString()
        path = input.readString()
        headers = input.readString()
        httpVersion = input.readInt()
        disableVersionFallback = input.readBoolean()
        enableTLS = input.readBoolean()
        serverName = input.readString()
        alpn = input.readString()
        certificates = input.readString()
        certPublicKeySha256 = input.readString()
        utlsFingerprint = input.readString()
        allowInsecure = input.readBoolean()
        disableSNI = input.readBoolean()
        tlsFragment = input.readBoolean()
        tlsFragmentFallbackDelay = input.readString()
        tlsRecordFragment = input.readBoolean()
        ech = input.readBoolean()
        echConfig = input.readString()
        echQueryServerName = input.readString()
        clientCert = input.readString()
        clientKey = input.readString()
        tlsSpoof = input.readString()
        tlsSpoofMethod = input.readString()
        mtu = input.readInt()
    }

    override fun clone(): MASQUEBean {
        return BeanConverters.deserialize(MASQUEBean(), BeanConverters.serialize(this))
    }

    override fun applyFeatureSettings(other: AbstractBean) {
        if (other !is MASQUEBean) return
        other.enableTLS = enableTLS
        other.httpVersion = httpVersion
        other.disableVersionFallback = disableVersionFallback
        other.mtu = mtu
        other.allowInsecure = allowInsecure
        other.disableSNI = disableSNI
        other.utlsFingerprint = utlsFingerprint
        other.ech = ech
        other.echConfig = echConfig
        other.tlsFragment = tlsFragment
        other.tlsFragmentFallbackDelay = tlsFragmentFallbackDelay
        other.tlsRecordFragment = tlsRecordFragment
    }

    override val defaultPort get() = 443
}
