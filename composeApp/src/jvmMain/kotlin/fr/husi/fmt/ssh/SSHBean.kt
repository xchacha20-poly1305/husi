package fr.husi.fmt.ssh

import kotlinx.serialization.Serializable as KxsSerializable
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.BeanConverters
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput

@KxsSerializable
class SSHBean : AbstractBean() {

    companion object {
        const val AUTH_TYPE_NONE = 0
        const val AUTH_TYPE_PASSWORD = 1
        const val AUTH_TYPE_PRIVATE_KEY = 2

        @JvmField
        val CREATOR = object : CREATOR<SSHBean>() {
            override fun newInstance(): SSHBean {
                return SSHBean()
            }

            override fun newArray(size: Int): Array<SSHBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var username: String = "root"
    var authType: Int = AUTH_TYPE_PASSWORD
    var password: String = ""
    var privateKey: String = ""
    var privateKeyPassphrase: String = ""
    var publicKey: String = ""

    override fun initializeDefaultValues() {
        if (serverPort !in 1..65535) serverPort = defaultPort
        super.initializeDefaultValues()

        if (username.isEmpty()) username = "root"
    }

    override fun serialize(output: BinaryOutput) {
        output.writeInt(0)
        super.serialize(output)
        output.writeString(username)
        output.writeInt(authType)
        when (authType) {
            AUTH_TYPE_NONE -> Unit
            AUTH_TYPE_PASSWORD -> output.writeString(password)
            AUTH_TYPE_PRIVATE_KEY -> {
                output.writeString(privateKey)
                output.writeString(privateKeyPassphrase)
            }
        }
        output.writeString(publicKey)
    }

    override fun deserialize(input: BinaryInput) {
        input.readInt()
        super.deserialize(input)
        username = input.readString()
        authType = input.readInt()
        when (authType) {
            AUTH_TYPE_NONE -> Unit
            AUTH_TYPE_PASSWORD -> password = input.readString()
            AUTH_TYPE_PRIVATE_KEY -> {
                privateKey = input.readString()
                privateKeyPassphrase = input.readString()
            }
        }
        publicKey = input.readString()
    }

    override fun clone(): SSHBean {
        return BeanConverters.deserialize(SSHBean(), BeanConverters.serialize(this))
    }

    override val defaultPort get() = 22
}
