package fr.husi.fmt.internal

import fr.husi.CONNECTION_TEST_URL
import fr.husi.fmt.BeanConverters
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput
import fr.husi.io.readList
import fr.husi.io.writeList
import kotlinx.serialization.Serializable as KxsSerializable

@KxsSerializable
class ProxySetBean : InternalBean() {

    companion object {
        const val MANAGEMENT_SELECTOR = 0
        const val MANAGEMENT_URLTEST = 1
        const val MANAGEMENT_BALANCER = 2

        val MANAGEMENTS = listOf(
            MANAGEMENT_SELECTOR,
            MANAGEMENT_URLTEST,
            MANAGEMENT_BALANCER,
        )

        fun isValidManagement(management: Int): Boolean = management in MANAGEMENTS

        @JvmField
        val CREATOR = object : CREATOR<ProxySetBean>() {
            override fun newInstance(): ProxySetBean {
                return ProxySetBean()
            }

            override fun newArray(size: Int): Array<ProxySetBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    @KxsSerializable
    sealed interface Provider {

        companion object {
            const val TYPE_SINGLE = 0.toByte()
            const val TYPE_GROUP = 1.toByte()
            const val TYPE_MEMBERS = 2.toByte()

            fun deserialize(input: BinaryInput): Provider {
                return when (val type = input.readByte()) {
                    TYPE_SINGLE -> Single.deserialize(input)
                    TYPE_GROUP -> Group.deserialize(input)
                    TYPE_MEMBERS -> Members.deserialize(input)
                    else -> error("Unsupported type: $type")
                }
            }
        }

        val type: Byte

        fun serialize(output: BinaryOutput) {
            output.writeByte(type)
        }

        @KxsSerializable
        data class Single(val id: Long) : Provider {
            companion object {
                fun deserialize(input: BinaryInput): Single {
                    val id = input.readLong()
                    return Single(id)
                }
            }

            override val type: Byte get() = TYPE_SINGLE

            override fun serialize(output: BinaryOutput) {
                super.serialize(output)
                output.writeLong(id)
            }
        }

        @KxsSerializable
        data class Group(
            val groupID: Long,
            val filterNotRegex: String,
        ) : Provider {
            companion object {
                fun deserialize(input: BinaryInput): Group {
                    val groupID = input.readLong()
                    val filterNotRegex = input.readString()
                    return Group(groupID, filterNotRegex)
                }
            }

            override val type: Byte get() = TYPE_GROUP

            override fun serialize(output: BinaryOutput) {
                super.serialize(output)
                output.writeLong(groupID)
                output.writeString(filterNotRegex)
            }
        }

        /** Expands to the members of another proxy set, instead of nesting it as one outbound. */
        @KxsSerializable
        data class Members(val proxySetID: Long) : Provider {
            companion object {
                fun deserialize(input: BinaryInput): Members {
                    val proxySetID = input.readLong()
                    return Members(proxySetID)
                }
            }

            override val type: Byte get() = TYPE_MEMBERS

            override fun serialize(output: BinaryOutput) {
                super.serialize(output)
                output.writeLong(proxySetID)
            }
        }

    }

    var management: Int = MANAGEMENT_SELECTOR
    var providers: List<Provider> = emptyList()

    // Selector + URLTest
    var interruptExistConnections: Boolean = false

    // URLTest
    var testURL: String = CONNECTION_TEST_URL
    var interval: String = "3m" // URL test interval or balancer rotate interval
    var testIdleTimeout: String = "3m"
    var testTolerance: Int = 50

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (!isValidManagement(management)) {
            management = MANAGEMENT_SELECTOR
        }
        if (testURL.isEmpty()) testURL = CONNECTION_TEST_URL
        if (interval.isEmpty()) interval = "3m"
        if (testIdleTimeout.isEmpty()) testIdleTimeout = "3m"
    }

    override fun displayName(): String {
        return name.ifEmpty {
            val hash = kotlin.math.abs(hashCode())
            when (management) {
                MANAGEMENT_SELECTOR -> "Selector $hash"
                MANAGEMENT_URLTEST -> "URLTest $hash"
                MANAGEMENT_BALANCER -> "Balancer $hash"
                else -> "Unknown $hash"
            }
        }
    }

    override fun serialize(output: BinaryOutput) {
        output.writeInt(2)
        output.writeInt(management)
        output.writeBoolean(interruptExistConnections)
        output.writeString(testURL)
        output.writeString(interval)
        output.writeString(testIdleTimeout)
        output.writeInt(testTolerance)

        output.writeList(providers, Provider::serialize)
    }

    override fun deserialize(input: BinaryInput) {
        val version = input.readInt()
        management = input.readInt()
        interruptExistConnections = input.readBoolean()
        testURL = input.readString()
        interval = input.readString()
        testIdleTimeout = input.readString()
        testTolerance = input.readInt()

        if (version >= 2) {
            providers = input.readList(Provider::deserialize)
        } else {
            val type = input.readInt()
            providers = when (type) {
                0 -> input.readList {
                    val id = it.readLong()
                    Provider.Single(id)
                }

                1 -> {
                    val groupID = input.readLong()
                    var filterNotRegex = ""
                    if (version >= 1) {
                        filterNotRegex = input.readString()
                    }
                    listOf(Provider.Group(groupID, filterNotRegex))
                }

                else -> error("unknown legacy proxy set type $type")
            }
        }
    }

    override fun clone(): ProxySetBean {
        return BeanConverters.deserialize(ProxySetBean(), BeanConverters.serialize(this))
    }

    fun displayType(): String {
        return when (management) {
            MANAGEMENT_SELECTOR -> "Selector"
            MANAGEMENT_URLTEST -> "URLTest"
            MANAGEMENT_BALANCER -> "Balancer"
            else -> "Unknown"
        }
    }
}
