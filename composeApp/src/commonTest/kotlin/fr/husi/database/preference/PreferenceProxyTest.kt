package fr.husi.database.preference

import fr.husi.database.DataStore
import fr.husi.database.callingUserIndex
import fr.husi.test.HusiKoinTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreferenceProxyTest : HusiKoinTest() {

    private val store get() = DataStore.configurationStore

    override suspend fun postStartKoin() {
        store.reset()
    }

    @Test
    fun `get returns default when unset`() = runTest {
        val proxy = store.string("proxy_test_string") { "fallback" }

        assertEquals("fallback", proxy.get())
        assertNull(proxy.getOrNull())
    }

    @Test
    fun `set then get round-trips`() = runTest {
        val proxy = store.string("proxy_test_roundtrip") { "fallback" }

        proxy.set("written")

        assertEquals("written", proxy.get())
        assertEquals("written", proxy.getOrNull())
    }

    @Test
    fun `update reads default then writes transformed value`() = runTest {
        val proxy = store.int("proxy_test_update") { 10 }

        proxy.update { it + 5 }
        assertEquals(15, proxy.get())

        proxy.update { it * 2 }
        assertEquals(30, proxy.get())
    }

    @Test
    fun `blocking accessors match suspend accessors`() = runTest {
        val proxy = store.boolean("proxy_test_blocking") { false }

        assertEquals(false, proxy.getBlocking())
        assertEquals(proxy.get(), proxy.getBlocking())

        proxy.setBlocking(true)

        assertEquals(true, proxy.get())
        assertEquals(true, proxy.getBlocking())
    }

    @Test
    fun `flow emits default then written value`() = runTest {
        val proxy = store.string("proxy_test_flow") { "unset" }

        assertEquals("unset", proxy.flow().first())

        proxy.set("set")

        assertEquals("set", proxy.flow().first())
    }

    @Test
    fun `int proxy stores values as Long`() = runTest {
        val asInt = store.int("proxy_test_int_long") { 1 }
        asInt.set(42)

        assertEquals(42, asInt.get())
        val asLong = store.long("proxy_test_int_long") { -1L }
        assertEquals(42L, asLong.get())
    }

    @Test
    fun `stringSet round-trips`() = runTest {
        val proxy = store.stringSet("proxy_test_set") { emptySet() }
        assertEquals(emptySet(), proxy.get())

        val values = setOf("alpha", "beta")
        proxy.set(values)

        assertEquals(values, proxy.get())
    }

    @Test
    fun `port proxy stores as string and parses default`() = runTest {
        val proxy = store.port("proxy_test_port", 2080)

        assertNull(proxy.getOrNull())
        assertEquals(2080 + callingUserIndex(), proxy.get())

        proxy.set(3456)

        assertEquals(3456, proxy.get())
        assertEquals("3456", store.getString("proxy_test_port"))
    }

    @Test
    fun `port proxy default 0 is not offset by user index`() = runTest {
        val proxy = store.port("proxy_test_port_zero", 0)

        assertEquals(0, proxy.get())
        assertEquals(0, proxy.getBlocking())
    }
}
