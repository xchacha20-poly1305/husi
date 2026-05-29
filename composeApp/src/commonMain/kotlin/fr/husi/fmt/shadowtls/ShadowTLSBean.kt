package fr.husi.fmt.shadowtls

import kotlinx.serialization.Serializable as KxsSerializable
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.KryoConverters
import fr.husi.fmt.ValidateResult
import fr.husi.fmt.v2ray.StandardV2RayBean
import fr.husi.resources.Res
import fr.husi.resources.warn_shadowtls_legacy

@KxsSerializable
class ShadowTLSBean : StandardV2RayBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<ShadowTLSBean>() {
            override fun newInstance(): ShadowTLSBean {
                return ShadowTLSBean()
            }

            override fun newArray(size: Int): Array<ShadowTLSBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var protocolVersion: Int = 3
    var password: String = ""

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        security = "tls"
    }

    override fun isInsecure(): ValidateResult {
        val result = super.isInsecure()
        if (shouldReturnFromInsecureCheck(result)) return result

        val tlsResult = validateTLSSettings(requireTLS = false, warnAllowInsecure = true)
        if (shouldReturnFromInsecureCheck(tlsResult)) return tlsResult
        if (protocolVersion < 3) return ValidateResult.Deprecated(Res.string.warn_shadowtls_legacy)
        return ValidateResult.Secure.Continue
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeInt(protocolVersion)
        output.writeString(password)
    }

    override fun deserialize(input: ByteBufferInput) {
        input.readInt()
        super.deserialize(input)
        protocolVersion = input.readInt()
        password = input.readString()
    }

    override fun clone(): ShadowTLSBean {
        return KryoConverters.deserialize(ShadowTLSBean(), KryoConverters.serialize(this))
    }

    override val defaultPort get() = 443
}
