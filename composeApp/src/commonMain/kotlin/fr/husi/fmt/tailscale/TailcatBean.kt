package fr.husi.fmt.tailscale

import kotlinx.serialization.Serializable as KxsSerializable
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.BeanConverters
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput
import kotlin.math.absoluteValue

@KxsSerializable
class TailcatBean : AbstractBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<TailcatBean>() {
            override fun newInstance(): TailcatBean {
                return TailcatBean()
            }

            override fun newArray(size: Int): Array<TailcatBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var privateKey: String = ""
    var serverPublicKey: String = ""
    var serverDiscoKey: String = ""
    var preSharedKey: String = ""
    var derpMapURL: String = ""

    var derpRegion: Int = 0

    /** One DERP host per line. Conflicts with [derpMapURL] and [derpRegion]. */
    var derpServers: String = ""

    override fun serialize(output: BinaryOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeString(privateKey)
        output.writeString(serverPublicKey)
        output.writeString(serverDiscoKey)
        output.writeString(preSharedKey)
        output.writeString(derpMapURL)
        output.writeInt(derpRegion)
        output.writeString(derpServers)
    }

    override fun deserialize(input: BinaryInput) {
        input.readInt()
        super.deserialize(input)
        privateKey = input.readString()
        serverPublicKey = input.readString()
        serverDiscoKey = input.readString()
        preSharedKey = input.readString()
        derpMapURL = input.readString()
        derpRegion = input.readInt()
        derpServers = input.readString()
    }

    override fun displayName(): String {
        return name.ifEmpty {
            val hash = hashCode().absoluteValue
            "Tailcat $hash"
        }
    }

    override fun displayAddress(): String {
        return ""
    }

    override val canICMPing get() = false
    override val canTCPing get() = false

    override fun clone(): TailcatBean {
        return BeanConverters.deserialize(TailcatBean(), BeanConverters.serialize(this))
    }
}
