package fr.husi.fmt.v2ray

import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters

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

    override fun applyFeatureSettings(other: AbstractBean) {
        if (other !is VLESSBean) return
        other.flow = flow
    }

    override fun clone(): VLESSBean {
        return KryoConverters.deserialize(VLESSBean(), KryoConverters.serialize(this))
    }

    override val defaultPort = 443
}
