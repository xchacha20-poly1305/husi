package fr.husi.fmt.v2ray

import kotlinx.serialization.Serializable as KxsSerializable
import fr.husi.fmt.KryoConverters
import fr.husi.fmt.ValidateResult

@KxsSerializable
class VLESSBean : StandardV2RayBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<VLESSBean>() {
            override fun newInstance(): VLESSBean {
                return VLESSBean()
            }

            override fun newArray(size: Int): Array<VLESSBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var flow: String = ""

    override fun isInsecure(): ValidateResult {
        val result = super.isInsecure()
        if (shouldReturnFromInsecureCheck(result)) return result

        if (encryption.isEmpty() || encryption == "none") {
            return validateTLSSettings(requireTLS = true, warnAllowInsecure = true)
        }
        return ValidateResult.Secure.Continue
    }

    override fun clone(): VLESSBean {
        return KryoConverters.deserialize(VLESSBean(), KryoConverters.serialize(this))
    }

    override val defaultPort get() = 443
}
