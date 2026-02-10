package fr.husi.fmt.shadowsocks

import kotlinx.serialization.Serializable as KxsSerializable
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters

@KxsSerializable
class ShadowsocksBean : AbstractBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<ShadowsocksBean>() {
            override fun newInstance(): ShadowsocksBean {
                return ShadowsocksBean()
            }

            override fun newArray(size: Int): Array<ShadowsocksBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var method: String = "aes-256-gcm"
    var password: String = ""
    var plugin: String = ""
    var udpOverTcp: Boolean = false

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (method.isBlank()) method = "aes-256-gcm"
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(2)
        super.serialize(output)
        output.writeString(method)
        output.writeString(password)
        output.writeString(plugin)
        output.writeBoolean(udpOverTcp)
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        super.deserialize(input)
        method = input.readString()
        password = input.readString()
        plugin = input.readString()
        udpOverTcp = input.readBoolean()
        if (version < 2) {
            input.readInt() // old mux
            return
        }
    }

    override fun applyFeatureSettings(other: AbstractBean) {
        if (other !is ShadowsocksBean) return
        other.udpOverTcp = udpOverTcp
    }

    override fun clone(): ShadowsocksBean {
        return KryoConverters.deserialize(ShadowsocksBean(), KryoConverters.serialize(this))
    }

    override val defaultPort get() = 8388
    override val needUDPOverTCP get() = udpOverTcp
}
