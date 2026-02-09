package fr.husi.fmt.shadowtls

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.KryoConverters
import fr.husi.fmt.v2ray.StandardV2RayBean

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
