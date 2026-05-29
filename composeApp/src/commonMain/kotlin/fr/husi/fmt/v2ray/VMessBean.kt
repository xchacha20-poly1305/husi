package fr.husi.fmt.v2ray

import kotlinx.serialization.Serializable as KxsSerializable
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters
import fr.husi.fmt.ValidateResult
import fr.husi.resources.Res
import fr.husi.resources.warn_vmess_md5_auth

@KxsSerializable
class VMessBean : StandardV2RayBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<VMessBean>() {
            override fun newInstance(): VMessBean {
                return VMessBean()
            }

            override fun newArray(size: Int): Array<VMessBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var alterId: Int = 0
    var authenticatedLength: Boolean = false

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (encryption.isBlank()) encryption = "auto"
    }

    override fun isInsecure(): ValidateResult {
        val result = super.isInsecure()
        if (shouldReturnFromInsecureCheck(result)) return result

        if (alterId > 0) return ValidateResult.Insecure(Res.string.warn_vmess_md5_auth)
        if (encryption == "none" || encryption == "zero") {
            val tlsResult = validateTLSSettings(requireTLS = true, warnAllowInsecure = false)
            if (shouldReturnFromInsecureCheck(tlsResult)) return tlsResult
        }
        return validateTLSSettings(requireTLS = false, warnAllowInsecure = true)
    }

    override fun applyFeatureSettings(other: AbstractBean) {
        super.applyFeatureSettings(other)
        if (other !is VMessBean) return
        if (authenticatedLength) {
            other.authenticatedLength = true
        }
    }

    override fun clone(): VMessBean {
        return KryoConverters.deserialize(VMessBean(), KryoConverters.serialize(this))
    }

    override val defaultPort get() = 10086
}
