package fr.husi.fmt.juicity

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters
import fr.husi.fmt.tuic.TuicBean

class JuicityBean : AbstractBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<JuicityBean>() {
            override fun newInstance(): JuicityBean {
                return JuicityBean()
            }

            override fun newArray(size: Int): Array<JuicityBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var uuid: String = ""
    var password: String = ""
    var sni: String = ""
    var allowInsecure: Boolean = false

    // Only BBR???
    // https://github.com/daeuniverse/softwind/blob/6daa40f6b7a5cb9a0c44ea252e86fcb3440a7a0e/protocol/tuic/common/congestion.go#L15
    // public String congestionControl;
    var pinSHA256: String = ""

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeString(uuid)
        output.writeString(password)
        output.writeString(sni)
        output.writeBoolean(allowInsecure)
        output.writeString(pinSHA256)
    }

    override fun deserialize(input: ByteBufferInput) {
        input.readInt()
        super.deserialize(input)
        uuid = input.readString()
        password = input.readString()
        sni = input.readString()
        allowInsecure = input.readBoolean()
        pinSHA256 = input.readString()
    }

    override fun applyFeatureSettings(other: AbstractBean) {
        if (other !is JuicityBean) return
    }

    override val canTCPing = false

    override fun clone(): AbstractBean {
        return KryoConverters.deserialize(TuicBean(), KryoConverters.serialize(this))
    }

    override val defaultPort = 443
}
