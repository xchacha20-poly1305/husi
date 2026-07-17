package fr.husi.fmt.openvpn

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters
import kotlinx.serialization.Serializable as KxsSerializable

@KxsSerializable
class OpenVPNBean : AbstractBean() {

    var network: String = "udp"
    var username: String = ""
    var password: String = ""
    var serverName: String = ""
    var certificate: String = ""
    var clientCertificate: String = ""
    var clientKey: String = ""
    var serverNameType: String = ""
    var peerFingerprint: String = ""
    var remoteCertificateKU: String = ""
    var remoteCertificateEKU: String = ""
    var controlWrapType: String = ""
    var controlWrapKey: String = ""
    var controlWrapDirection: String = ""
    var dataCiphers: String = ""
    var auth: String = ""
    var compression: String = ""
    var redirectGateway: Boolean = false
    var mtu: Int = 1500

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeString(network)
        output.writeString(username)
        output.writeString(password)
        output.writeString(serverName)
        output.writeString(certificate)
        output.writeString(clientCertificate)
        output.writeString(clientKey)
        output.writeString(controlWrapType)
        output.writeString(controlWrapKey)
        output.writeString(controlWrapDirection)
        output.writeString(dataCiphers)
        output.writeString(auth)
        output.writeString(compression)
        output.writeBoolean(redirectGateway)
        output.writeInt(mtu)
        output.writeString(serverNameType)
        output.writeString(peerFingerprint)
        output.writeString(remoteCertificateKU)
        output.writeString(remoteCertificateEKU)
    }

    override fun deserialize(input: ByteBufferInput) {
        input.readInt()
        super.deserialize(input)
        network = input.readString().orEmpty()
        username = input.readString().orEmpty()
        password = input.readString().orEmpty()
        serverName = input.readString().orEmpty()
        certificate = input.readString().orEmpty()
        clientCertificate = input.readString().orEmpty()
        clientKey = input.readString().orEmpty()
        controlWrapType = input.readString().orEmpty()
        controlWrapKey = input.readString().orEmpty()
        controlWrapDirection = input.readString().orEmpty()
        dataCiphers = input.readString().orEmpty()
        auth = input.readString().orEmpty()
        compression = input.readString().orEmpty()
        redirectGateway = input.readBoolean()
        mtu = input.readInt()
        serverNameType = input.readString().orEmpty()
        peerFingerprint = input.readString().orEmpty()
        remoteCertificateKU = input.readString().orEmpty()
        remoteCertificateEKU = input.readString().orEmpty()
    }

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (network !in setOf("tcp", "udp")) network = "udp"
    }

    override fun clone() = KryoConverters.deserialize(OpenVPNBean(), KryoConverters.serialize(this))

    override val defaultPort get() = 1194
}
