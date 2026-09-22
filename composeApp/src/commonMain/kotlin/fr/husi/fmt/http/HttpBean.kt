package fr.husi.fmt.http

import kotlinx.serialization.Serializable as KxsSerializable
import fr.husi.fmt.BeanConverters
import fr.husi.fmt.ValidateResult
import fr.husi.fmt.v2ray.StandardV2RayBean
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput

@KxsSerializable
class HttpBean : StandardV2RayBean() {

    companion object {

        const val HTTP_VERSION_1 = 1
        const val HTTP_VERSION_2 = 2
        const val HTTP_VERSION_3 = 3

        fun isValidHttpVersion(version: Int) = version in HTTP_VERSION_1..HTTP_VERSION_3

        fun supportedHttpVersions(isTLS: Boolean) = if (isTLS) {
            listOf(HTTP_VERSION_1, HTTP_VERSION_2, HTTP_VERSION_3)
        } else {
            listOf(HTTP_VERSION_1, HTTP_VERSION_2)
        }

        @JvmField
        val CREATOR = object : CREATOR<HttpBean>() {
            override fun newInstance(): HttpBean {
                return HttpBean()
            }

            override fun newArray(size: Int): Array<HttpBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var username: String = ""
    var password: String = ""
    var httpVersion: Int = HTTP_VERSION_1
    var disableVersionFallback: Boolean = false

    override fun isInsecure(): ValidateResult {
        val result = super.isInsecure()
        if (shouldReturnFromInsecureCheck(result)) return result

        return validateTLSSettings(requireTLS = true, warnAllowInsecure = false)
    }

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (!isValidHttpVersion(httpVersion)) {
            httpVersion = HTTP_VERSION_1
        }
    }

    override fun serialize(output: BinaryOutput) {
        output.writeInt(3)

        // version 0
        super.serialize(output)
        output.writeString(username)
        output.writeString(password)

        // version 1
        output.writeString(host)
        output.writeString(path)
        output.writeString(headers)

        // version 3
        output.writeInt(httpVersion)
        output.writeBoolean(disableVersionFallback)
    }

    override fun deserialize(input: BinaryInput) {
        val version = input.readInt()
        super.deserialize(input)
        username = input.readString()
        password = input.readString()
        if (version >= 1) {
            host = input.readString()
            path = input.readString()
            headers = input.readString()
        }
        if (version == 2) {
            input.readBoolean() // removed udpOverTcp
        }
        if (version >= 3) {
            httpVersion = input.readInt()
            disableVersionFallback = input.readBoolean()
        }
    }

    override fun clone(): HttpBean {
        return BeanConverters.deserialize(HttpBean(), BeanConverters.serialize(this))
    }

    override val defaultPort get() = if (isTLS) 443 else 80
}
