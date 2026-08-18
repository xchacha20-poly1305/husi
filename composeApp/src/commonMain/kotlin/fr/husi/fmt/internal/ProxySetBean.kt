package fr.husi.fmt.internal

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.CONNECTION_TEST_URL
import fr.husi.database.ProxyEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.KryoConverters
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.onIoDispatcher
import fr.husi.ktx.readList
import fr.husi.ktx.writeList
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable as KxsSerializable

@KxsSerializable
class ProxySetBean : InternalBean() {

    companion object {
        const val MANAGEMENT_SELECTOR = 0
        const val MANAGEMENT_URLTEST = 1

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

            fun deserialize(input: ByteBufferInput): Provider {
                return when (val type = input.readByte()) {
                    TYPE_SINGLE -> Single.deserialize(input)
                    TYPE_GROUP -> Group.deserialize(input)
                    else -> error("Unsupported type: $type")
                }
            }
        }

        val type: Byte

        suspend fun entities(): List<ProxyEntity>

        fun serialize(output: ByteBufferOutput) {
            output.writeByte(type)
        }

        @KxsSerializable
        data class Single(val id: Long) : Provider {
            companion object {
                fun deserialize(input: ByteBufferInput): Single {
                    val id = input.readLong()
                    return Single(id)
                }
            }

            override val type: Byte get() = TYPE_SINGLE

            override suspend fun entities(): List<ProxyEntity> = onIoDispatcher {
                SagerDatabase.proxyDao.getEntities(listOf(id))
            }

            override fun serialize(output: ByteBufferOutput) {
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
                fun deserialize(input: ByteBufferInput): Group {
                    val groupID = input.readLong()
                    val filterNotRegex = input.readString().orEmpty()
                    return Group(groupID, filterNotRegex)
                }
            }

            override val type: Byte get() = TYPE_GROUP

            override suspend fun entities(): List<ProxyEntity> {
                val filter = filterNotRegex.blankAsNull()?.toRegex()
                val entities = onIoDispatcher {
                    SagerDatabase.proxyDao.getByGroup(groupID).first()
                }
                if (filter == null) return entities
                return entities.filter { filter.containsMatchIn(it.displayName()) }
            }

            override fun serialize(output: ByteBufferOutput) {
                super.serialize(output)
                output.writeLong(groupID)
                output.writeString(filterNotRegex)
            }
        }

    }

    var management: Int = MANAGEMENT_SELECTOR
    var providers: List<Provider> = emptyList()

    // Selector + URLTest
    var interruptExistConnections: Boolean = false

    // URLTest
    var testURL: String = CONNECTION_TEST_URL
    var testInterval: String = "3m"
    var testIdleTimeout: String = "3m"
    var testTolerance: Int = 50

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (management != MANAGEMENT_SELECTOR && management != MANAGEMENT_URLTEST) {
            management = MANAGEMENT_SELECTOR
        }
        if (testURL.isEmpty()) testURL = CONNECTION_TEST_URL
        if (testInterval.isEmpty()) testInterval = "3m"
        if (testIdleTimeout.isEmpty()) testIdleTimeout = "3m"
    }

    override fun displayName(): String {
        return name.ifEmpty {
            val hash = kotlin.math.abs(hashCode())
            when (management) {
                MANAGEMENT_SELECTOR -> "Selector $hash"
                MANAGEMENT_URLTEST -> "URLTest $hash"
                else -> "Unknown $hash"
            }
        }
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(2)
        output.writeInt(management)
        output.writeBoolean(interruptExistConnections)
        output.writeString(testURL)
        output.writeString(testInterval)
        output.writeString(testIdleTimeout)
        output.writeInt(testTolerance)

        output.writeList(providers, Provider::serialize)
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        management = input.readInt()
        interruptExistConnections = input.readBoolean()
        testURL = input.readString().orEmpty()
        testInterval = input.readString().orEmpty()
        testIdleTimeout = input.readString().orEmpty()
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
                        filterNotRegex = input.readString().orEmpty()
                    }
                    listOf(Provider.Group(groupID, filterNotRegex))
                }

                else -> error("unknown legacy proxy set type $type")
            }
        }
    }

    override fun clone(): ProxySetBean {
        return KryoConverters.deserialize(ProxySetBean(), KryoConverters.serialize(this))
    }

    fun displayType(): String {
        return when (management) {
            MANAGEMENT_SELECTOR -> "Selector"
            MANAGEMENT_URLTEST -> "URLTest"
            else -> "Unknown"
        }
    }
}
