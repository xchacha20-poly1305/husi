package fr.husi.fmt.direct

import kotlinx.serialization.Serializable as KxsSerializable
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.BeanConverters
import fr.husi.fmt.SingBoxOptions
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput

@KxsSerializable
class DirectBean : AbstractBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<DirectBean>() {
            override fun newInstance(): DirectBean {
                return DirectBean()
            }

            override fun newArray(size: Int): Array<DirectBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    override fun serialize(output: BinaryOutput) {
        output.writeInt(0)
        super.serialize(output)
    }

    override fun deserialize(input: BinaryInput) {
        input.readInt()
        super.deserialize(input)
    }

    override fun clone(): DirectBean {
        return BeanConverters.deserialize(DirectBean(), BeanConverters.serialize(this))
    }

    override fun displayName(): String {
        if (name.isEmpty()) {
            return SingBoxOptions.TYPE_DIRECT
        }
        return name
    }

    override fun displayAddress(): String {
        return ""
    }

    override val canICMPing get() = true
    override val canTCPing get() = true
}
