package fr.husi.fmt.internal

import com.esotericsoftware.kryo.io.ByteBufferOutput
import fr.husi.fmt.KryoConverters
import fr.husi.ktx.byteBuffer
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxySetBeanTest {

    @Test
    fun `serialize should round trip every provider`() {
        val bean = ProxySetBean().apply {
            name = "set"
            management = ProxySetBean.MANAGEMENT_URLTEST
            interruptExistConnections = true
            testTolerance = 42
            providers = listOf(
                ProxySetBean.Provider.Single(7L),
                ProxySetBean.Provider.Group(9L, "^premium"),
                ProxySetBean.Provider.Single(11L),
            )
        }

        val restored = KryoConverters.proxySetDeserialize(KryoConverters.serialize(bean))!!

        assertEquals(bean.providers, restored.providers)
        assertEquals(bean.name, restored.name)
        assertEquals(bean.management, restored.management)
        assertEquals(bean.testTolerance, restored.testTolerance)
    }

    @Test
    fun `deserialize should migrate a legacy list proxy set`() {
        val bytes = legacyProxySetBytes(version = 0) {
            it.writeInt(0)
            it.writeInt(2)
            it.writeLong(3L)
            it.writeLong(5L)
        }

        val bean = KryoConverters.proxySetDeserialize(bytes)!!

        assertEquals(
            listOf(ProxySetBean.Provider.Single(3L), ProxySetBean.Provider.Single(5L)),
            bean.providers,
        )
    }

    @Test
    fun `deserialize should migrate a legacy group proxy set`() {
        val bytes = legacyProxySetBytes(version = 1) {
            it.writeInt(1)
            it.writeLong(13L)
            it.writeString("^keep")
        }

        val bean = KryoConverters.proxySetDeserialize(bytes)!!

        assertEquals(listOf(ProxySetBean.Provider.Group(13L, "^keep")), bean.providers)
    }

    /** Writes what [ProxySetBean.serialize] used to emit, plus the shared bean tail. */
    private fun legacyProxySetBytes(
        version: Int,
        writeMembers: (ByteBufferOutput) -> Unit,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = out.byteBuffer()

        buffer.writeInt(version)
        buffer.writeInt(ProxySetBean.MANAGEMENT_SELECTOR)
        buffer.writeBoolean(false)
        buffer.writeString("https://example.org")
        buffer.writeString("3m")
        buffer.writeString("3m")
        buffer.writeInt(50)
        writeMembers(buffer)

        buffer.writeInt(4)
        buffer.writeString("legacy")
        buffer.writeString("")
        buffer.writeString("")
        buffer.writeBoolean(false)
        buffer.writeBoolean(false)
        buffer.writeInt(0)
        buffer.writeInt(0)
        buffer.writeBoolean(false)
        buffer.writeInt(0)

        buffer.flush()
        buffer.close()
        return out.toByteArray()
    }
}
