package fr.husi.fmt.config

import android.text.TextUtils
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.KryoConverters
import fr.husi.fmt.internal.InternalBean

/**
 * Custom config
 */
class ConfigBean : InternalBean() {

    companion object {
        const val TYPE_CONFIG = 0
        const val TYPE_OUTBOUND = 1

        @JvmField
        val CREATOR = object : CREATOR<ConfigBean>() {
            override fun newInstance(): ConfigBean {
                return ConfigBean()
            }

            override fun newArray(size: Int): Array<ConfigBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var type: Int = TYPE_CONFIG
    var config: String = ""

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeInt(type)
        output.writeString(config)
    }

    override fun deserialize(input: ByteBufferInput) {
        input.readInt()
        super.deserialize(input)
        type = input.readInt()
        config = input.readString()
    }

    override fun displayName(): String {
        if (TextUtils.isEmpty(name)) {
            return "Custom ${kotlin.math.abs(hashCode())}"
        }
        return name
    }

    fun displayType(): String {
        return if (type == TYPE_CONFIG) "sing-box config" else "sing-box outbound"
    }

    override fun clone(): ConfigBean {
        return KryoConverters.deserialize(ConfigBean(), KryoConverters.serialize(this))
    }
}
