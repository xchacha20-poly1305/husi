package fr.husi.fmt.snell

import kotlinx.serialization.Serializable as KxsSerializable
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters

@KxsSerializable
class SnellBean : AbstractBean() {

    companion object {
        const val VERSION_4 = 4
        const val VERSION_6 = 6

        @JvmField
        val CREATOR = object : CREATOR<SnellBean>() {
            override fun newInstance(): SnellBean {
                return SnellBean()
            }

            override fun newArray(size: Int): Array<SnellBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var version: Int = VERSION_4
    var psk: String = ""
    var userKey: String = ""
    var reuse: Boolean = false
    var obfsMode: String = ""
    var obfsHost: String = ""
    var mode: String = ""

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (version != VERSION_4 && version != VERSION_6) {
            version = VERSION_4
        }
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeInt(version)
        output.writeString(psk)
        output.writeString(userKey)
        output.writeBoolean(reuse)
        when (version) {
            VERSION_4 -> {
                output.writeString(obfsMode)
                output.writeString(obfsHost)
            }

            VERSION_6 -> {
                output.writeString(mode)
            }
        }
    }

    override fun deserialize(input: ByteBufferInput) {
        input.readInt()
        super.deserialize(input)
        version = input.readInt()
        psk = input.readString()
        userKey = input.readString()
        reuse = input.readBoolean()
        when (version) {
            VERSION_4 -> {
                obfsMode = input.readString()
                obfsHost = input.readString()
            }

            VERSION_6 -> {
                mode = input.readString()
            }
        }
    }

    override fun clone(): SnellBean {
        return KryoConverters.deserialize(SnellBean(), KryoConverters.serialize(this))
    }

    override fun applyFeatureSettings(other: AbstractBean) {
        if (other !is SnellBean) return
        other.version = version
        other.reuse = reuse
        other.obfsMode = obfsMode
        other.obfsHost = obfsHost
        other.mode = mode
    }

    override val defaultPort get() = 443
}
