package fr.husi.fmt.tailscale

import kotlinx.serialization.Serializable as KxsSerializable
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.BeanConverters
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput
import kotlin.math.absoluteValue

@KxsSerializable
class TailscaleBean : AbstractBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<TailscaleBean>() {
            override fun newInstance(): TailscaleBean {
                return TailscaleBean()
            }

            override fun newArray(size: Int): Array<TailscaleBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var stateDirectory: String = ""
    var authKey: String = ""
    var controlURL: String = ""
    var ephemeral: Boolean = false
    var hostname: String = ""
    var acceptRoutes: Boolean = false
    var exitNode: String = ""
    var exitNodeAllowLANAccess: Boolean = false
    var advertiseRoutes: String = ""
    var advertiseExitNode: Boolean = false
    var advertiseTags: String = ""
    var listenPort: Int = 0
    var sshServer: Boolean = false

    var advertiseFakeDNSRoutes: Boolean = false

    /** Desktop only */
    var taildropDirectory: String = ""

    override fun serialize(output: BinaryOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeString(stateDirectory)
        output.writeString(authKey)
        output.writeString(controlURL)
        output.writeBoolean(ephemeral)
        output.writeString(hostname)
        output.writeBoolean(acceptRoutes)
        output.writeString(exitNode)
        output.writeBoolean(exitNodeAllowLANAccess)
        output.writeString(advertiseRoutes)
        output.writeBoolean(advertiseExitNode)
        output.writeString(advertiseTags)
        output.writeInt(listenPort)
        output.writeBoolean(sshServer)
        output.writeString(taildropDirectory)
        output.writeBoolean(advertiseFakeDNSRoutes)
    }

    override fun deserialize(input: BinaryInput) {
        input.readInt()
        super.deserialize(input)
        stateDirectory = input.readString()
        authKey = input.readString()
        controlURL = input.readString()
        ephemeral = input.readBoolean()
        hostname = input.readString()
        acceptRoutes = input.readBoolean()
        exitNode = input.readString()
        exitNodeAllowLANAccess = input.readBoolean()
        advertiseRoutes = input.readString()
        advertiseExitNode = input.readBoolean()
        advertiseTags = input.readString()
        listenPort = input.readInt()
        sshServer = input.readBoolean()
        taildropDirectory = input.readString()
        advertiseFakeDNSRoutes = input.readBoolean()
    }

    override fun displayName(): String {
        return name.ifEmpty { hostname }.ifEmpty {
            val hash = hashCode().absoluteValue
            "Tailscale $hash"
        }
    }

    override fun displayAddress(): String {
        return controlURL
    }

    override val canICMPing get() = false
    override val canTCPing get() = false

    override fun clone(): TailscaleBean {
        return BeanConverters.deserialize(TailscaleBean(), BeanConverters.serialize(this))
    }
}
