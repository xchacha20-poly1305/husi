package io.nekohasekai.sagernet.fmt.wireguard

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters

class WireGuardBean : AbstractBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<WireGuardBean>() {
            override fun newInstance(): WireGuardBean {
                return WireGuardBean()
            }

            override fun newArray(size: Int): Array<WireGuardBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var localAddress: String = ""
    var privateKey: String = ""
    var publicKey: String = ""
    var preSharedKey: String = ""
    var mtu: Int = 1420
    var reserved: String = ""

    /**
     * Enable listen if it > 0
     */
    var listenPort: Int = 0
    var persistentKeepaliveInterval: Int = 0

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (mtu !in 1000..65535) mtu = 1420
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(2)
        super.serialize(output)
        output.writeString(localAddress)
        output.writeString(privateKey)
        output.writeString(publicKey)
        output.writeString(preSharedKey)
        output.writeInt(mtu)
        output.writeString(reserved)
        output.writeInt(listenPort)
        output.writeInt(persistentKeepaliveInterval)
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        super.deserialize(input)
        localAddress = input.readString()
        privateKey = input.readString()
        publicKey = input.readString()
        preSharedKey = input.readString()
        mtu = input.readInt()
        reserved = input.readString()
        if (version >= 1) {
            listenPort = input.readInt()
        }
        if (version >= 2) {
            persistentKeepaliveInterval = input.readInt()
        }
    }

    override val canTCPing = false

    override fun clone(): WireGuardBean {
        return KryoConverters.deserialize(WireGuardBean(), KryoConverters.serialize(this))
    }

    override val defaultPort = 51820
}
