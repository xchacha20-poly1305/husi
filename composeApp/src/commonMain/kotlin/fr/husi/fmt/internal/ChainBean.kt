package fr.husi.fmt.internal

import kotlinx.serialization.Serializable as KxsSerializable
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.KryoConverters

@KxsSerializable
class ChainBean : InternalBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<ChainBean>() {
            override fun newInstance(): ChainBean {
                return ChainBean()
            }

            override fun newArray(size: Int): Array<ChainBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    @JvmField
    var proxies: List<Long> = emptyList()

    override fun displayName(): String {
        if (name.isEmpty()) {
            return "Chain ${kotlin.math.abs(hashCode())}"
        }
        return name
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(1)
        output.writeInt(proxies.size)
        for (proxy in proxies) {
            output.writeLong(proxy)
        }
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        if (version < 1) {
            input.readString()
            input.readInt()
        }
        val length = input.readInt()
        val proxies = ArrayList<Long>(length)
        for (i in 0 until length) {
            proxies.add(input.readLong())
        }
        this.proxies = proxies
    }

    override fun clone(): ChainBean {
        return KryoConverters.deserialize(ChainBean(), KryoConverters.serialize(this))
    }
}
