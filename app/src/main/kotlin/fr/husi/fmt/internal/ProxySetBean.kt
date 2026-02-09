package fr.husi.fmt.internal

import android.text.TextUtils
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.CONNECTION_TEST_URL
import fr.husi.fmt.KryoConverters

class ProxySetBean : InternalBean() {

    companion object {
        const val MANAGEMENT_SELECTOR = 0
        const val MANAGEMENT_URLTEST = 1

        const val TYPE_LIST = 0
        const val TYPE_GROUP = 1

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

    var management: Int = MANAGEMENT_SELECTOR
    var type: Int = TYPE_LIST
    var proxies: List<Long> = emptyList()
    var groupId: Long = 0L
    var groupFilterNotRegex: String = ""

    // Selector + URLTest
    var interruptExistConnections: Boolean = false

    // URLTest
    var testURL: String = CONNECTION_TEST_URL
    var testInterval: String = "3m"
    var testIdleTimeout: String = "3m"
    var testTolerance: Int = 50

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (management != MANAGEMENT_SELECTOR && management != MANAGEMENT_URLTEST) management =
            MANAGEMENT_SELECTOR
        if (type != TYPE_LIST && type != TYPE_GROUP) type = TYPE_LIST
        if (testURL.isEmpty()) testURL = CONNECTION_TEST_URL
        if (testInterval.isEmpty()) testInterval = "3m"
        if (testIdleTimeout.isEmpty()) testIdleTimeout = "3m"
    }

    override fun displayName(): String {
        if (TextUtils.isEmpty(name)) {
            val hash = kotlin.math.abs(hashCode())
            return when (management) {
                MANAGEMENT_SELECTOR -> "Selector $hash"
                MANAGEMENT_URLTEST -> "URLTest $hash"
                else -> "Unknown $hash"
            }
        }
        return name
    }

    override fun serialize(output: ByteBufferOutput) {
        output.writeInt(1)
        output.writeInt(management)
        output.writeBoolean(interruptExistConnections)
        output.writeString(testURL)
        output.writeString(testInterval)
        output.writeString(testIdleTimeout)
        output.writeInt(testTolerance)

        output.writeInt(type)
        when (type) {
            TYPE_LIST -> {
                output.writeInt(proxies.size)
                for (proxy in proxies) {
                    output.writeLong(proxy)
                }
            }

            TYPE_GROUP -> {
                output.writeLong(groupId)
                output.writeString(groupFilterNotRegex)
            }
        }
    }

    override fun deserialize(input: ByteBufferInput) {
        val version = input.readInt()
        management = input.readInt()
        interruptExistConnections = input.readBoolean()
        testURL = input.readString() ?: ""
        testInterval = input.readString() ?: ""
        testIdleTimeout = input.readString() ?: ""
        testTolerance = input.readInt()

        type = input.readInt()
        when (type) {
            TYPE_LIST -> {
                val length = input.readInt()
                val list = ArrayList<Long>(length)
                for (i in 0 until length) {
                    list.add(input.readLong())
                }
                proxies = list
            }

            TYPE_GROUP -> {
                groupId = input.readLong()
                if (version >= 1) {
                    groupFilterNotRegex = input.readString() ?: ""
                }
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
