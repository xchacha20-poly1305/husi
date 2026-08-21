package fr.husi.fmt.config

import kotlinx.serialization.Serializable as KxsSerializable
import fr.husi.fmt.BeanConverters
import fr.husi.fmt.internal.InternalBean
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput

/**
 * Custom config
 */
@KxsSerializable
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

    override fun serialize(output: BinaryOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeInt(type)
        output.writeString(config)
    }

    override fun deserialize(input: BinaryInput) {
        input.readInt()
        super.deserialize(input)
        type = input.readInt()
        config = input.readString()
    }

    override fun displayName(): String {
        if (name.isEmpty()) {
            return "Custom ${kotlin.math.abs(hashCode())}"
        }
        return name
    }

    fun displayType(): String {
        return if (type == TYPE_CONFIG) "sing-box config" else "sing-box outbound"
    }

    override fun clone(): ConfigBean {
        return BeanConverters.deserialize(ConfigBean(), BeanConverters.serialize(this))
    }
}
