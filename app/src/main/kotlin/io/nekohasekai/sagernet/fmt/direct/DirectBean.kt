package io.nekohasekai.sagernet.fmt.direct

import android.text.TextUtils
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.SingBoxOptions

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

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(0)
        super.serialize(output)
    }

    override fun deserialize(input: ByteBufferInput) {
        input.readInt()
        super.deserialize(input)
    }

    override fun clone(): DirectBean {
        return KryoConverters.deserialize(DirectBean(), KryoConverters.serialize(this))
    }

    override fun displayName(): String {
        if (TextUtils.isEmpty(name)) {
            return SingBoxOptions.TYPE_DIRECT
        }
        return name
    }

    override fun displayAddress(): String {
        return ""
    }

    override val canICMPing = true
    override val canTCPing = true
}
