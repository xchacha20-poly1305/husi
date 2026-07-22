package fr.husi.fmt.openconnect

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.KryoConverters
import kotlinx.serialization.Serializable as KxsSerializable

@KxsSerializable
data class OpenConnectFormEntry(
    val formId: String = "",
    val submissionKey: String = "",
    val name: String = "",
    val value: String = "",
) {

    fun serialize(output: ByteBufferOutput) {
        output.writeString(formId)
        output.writeString(submissionKey)
        output.writeString(name)
        output.writeString(value)
    }

    companion object {
        fun deserialize(input: ByteBufferInput): OpenConnectFormEntry {
            return OpenConnectFormEntry(
                formId = input.readString().orEmpty(),
                submissionKey = input.readString().orEmpty(),
                name = input.readString().orEmpty(),
                value = input.readString().orEmpty(),
            )
        }
    }
}

@KxsSerializable
class OpenConnectBean : AbstractBean() {

    var server: String = "https://127.0.0.1"
    var flavor: String = ""
    var username: String = ""
    var password: String = ""
    var authGroup: String = ""
    var reportedOS: String = ""
    var userAgent: String = ""
    var localHostname: String = ""
    var allowInsecureCrypto: Boolean = false
    var tlsInsecure: Boolean = false
    var tlsServerName: String = ""
    var tlsPeerFingerprint: String = ""
    var certificateAuthority: String = ""
    var clientCertificate: String = ""
    var clientKey: String = ""
    var clientKeyPassword: String = ""
    var mcaCertificate: String = ""
    var mcaKey: String = ""
    var mcaKeyPassword: String = ""

    /** Answers remembered from interactive authentication, replayed on reconnect. */
    var formEntries: List<OpenConnectFormEntry> = emptyList()

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(2)
        super.serialize(output)
        output.writeString(server)
        output.writeString(flavor)
        output.writeString(username)
        output.writeString(password)
        output.writeString(authGroup)
        output.writeString(reportedOS)
        output.writeString(userAgent)
        output.writeBoolean(allowInsecureCrypto)
        output.writeString(certificateAuthority)
        output.writeString(clientCertificate)
        output.writeString(clientKey)
        output.writeString(clientKeyPassword)
        output.writeString(mcaCertificate)
        output.writeString(mcaKey)
        output.writeString(mcaKeyPassword)

        // version 1
        output.writeInt(formEntries.size)
        for (entry in formEntries) {
            entry.serialize(output)
        }

        // version 2
        output.writeString(localHostname)
        output.writeBoolean(tlsInsecure)
        output.writeString(tlsServerName)
        output.writeString(tlsPeerFingerprint)
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        super.deserialize(input)
        server = input.readString().orEmpty()
        flavor = input.readString().orEmpty()
        username = input.readString().orEmpty()
        password = input.readString().orEmpty()
        authGroup = input.readString().orEmpty()
        reportedOS = input.readString().orEmpty()
        userAgent = input.readString().orEmpty()
        allowInsecureCrypto = input.readBoolean()
        certificateAuthority = input.readString().orEmpty()
        clientCertificate = input.readString().orEmpty()
        clientKey = input.readString().orEmpty()
        clientKeyPassword = input.readString().orEmpty()
        mcaCertificate = input.readString().orEmpty()
        mcaKey = input.readString().orEmpty()
        mcaKeyPassword = input.readString().orEmpty()

        if (version >= 1) {
            val count = input.readInt()
            formEntries = List(count) {
                OpenConnectFormEntry.deserialize(input)
            }
        }

        if (version >= 2) {
            localHostname = input.readString().orEmpty()
            tlsInsecure = input.readBoolean()
            tlsServerName = input.readString().orEmpty()
            tlsPeerFingerprint = input.readString().orEmpty()
        }
    }

    override fun displayAddress() = server

    override fun clone() = KryoConverters.deserialize(OpenConnectBean(), KryoConverters.serialize(this))
}
