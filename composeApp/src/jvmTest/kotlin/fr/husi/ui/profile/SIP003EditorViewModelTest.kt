package fr.husi.ui.profile

import fr.husi.test.MainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SIP003EditorViewModelTest : MainDispatcherTest() {

    @Test
    fun `obfs-local parses initialOpts into uiState`() {
        val viewModel = SIP003EditorViewModel(
            pluginName = SIP003_OBFS_LOCAL,
            initialOpts = "obfs=tls;obfs-host=example.org",
        )
        val state = viewModel.uiState.value
        assertEquals(ObfsMode.Tls, state.obfs)
        assertEquals("example.org", state.obfsHost)
    }

    @Test
    fun `obfs-local falls back to defaults when initialOpts is empty`() {
        val viewModel = SIP003EditorViewModel(SIP003_OBFS_LOCAL, "")
        val state = viewModel.uiState.value
        assertEquals(ObfsMode.Default, state.obfs)
        assertEquals("", state.obfsHost)
    }

    @Test
    fun `obfs-local unknown obfs value falls back to default`() {
        val viewModel = SIP003EditorViewModel(SIP003_OBFS_LOCAL, "obfs=garbage")
        assertEquals(ObfsMode.Default, viewModel.uiState.value.obfs)
    }

    @Test
    fun `obfs-local serialize emits configured keys`() {
        val viewModel = SIP003EditorViewModel(SIP003_OBFS_LOCAL, "")
        viewModel.setObfs(ObfsMode.Tls)
        viewModel.setObfsHost("h.example")
        assertEquals("obfs=tls;obfs-host=h.example", viewModel.serialize())
    }

    @Test
    fun `obfs-local serialize omits empty obfs-host`() {
        val viewModel = SIP003EditorViewModel(SIP003_OBFS_LOCAL, "")
        viewModel.setObfs(ObfsMode.Http)
        assertEquals("obfs=http", viewModel.serialize())
    }

    @Test
    fun `v2ray-plugin parses tls flag from bare key`() {
        val viewModel = SIP003EditorViewModel(SIP003_V2RAY_PLUGIN, "tls;mode=quic")
        val state = viewModel.uiState.value
        assertTrue(state.tls)
        assertEquals(V2RayMode.Quic, state.mode)
    }

    @Test
    fun `v2ray-plugin treats tls= empty as disabled`() {
        val viewModel = SIP003EditorViewModel(SIP003_V2RAY_PLUGIN, "tls=")
        assertFalse(viewModel.uiState.value.tls)
    }

    @Test
    fun `v2ray-plugin parses mux as integer with default fallback`() {
        val viewModel = SIP003EditorViewModel(SIP003_V2RAY_PLUGIN, "mux=4")
        assertEquals(4, viewModel.uiState.value.mux)

        val withGarbage = SIP003EditorViewModel(SIP003_V2RAY_PLUGIN, "mux=not-a-number")
        assertEquals(DEFAULT_V2RAY_MUX, withGarbage.uiState.value.mux)
    }

    @Test
    fun `v2ray-plugin serialize writes tls equals 1 when enabled`() {
        val viewModel = SIP003EditorViewModel(SIP003_V2RAY_PLUGIN, "")
        viewModel.setTls(true)
        viewModel.setMode(V2RayMode.Websocket)
        viewModel.setHost("h")
        viewModel.setPath("/p")
        viewModel.setMux(2)
        assertEquals("tls=1;mode=websocket;host=h;path=/p;mux=2", viewModel.serialize())
    }

    @Test
    fun `v2ray-plugin serialize omits tls when disabled and certRaw when blank`() {
        val viewModel = SIP003EditorViewModel(SIP003_V2RAY_PLUGIN, "")
        viewModel.setMode(V2RayMode.Quic)
        viewModel.setHost("h")
        // tls left as default (false), certRaw left as default (empty)
        assertEquals("mode=quic;host=h;mux=$DEFAULT_V2RAY_MUX", viewModel.serialize())
    }

    @Test
    fun `v2ray-plugin round-trips a non-trivial opts string`() {
        val opts = "tls=1;mode=websocket;host=cloudfront.com;path=/ws;mux=4"
        val first = SIP003EditorViewModel(SIP003_V2RAY_PLUGIN, opts)
        val rebuilt = first.serialize()
        val second = SIP003EditorViewModel(SIP003_V2RAY_PLUGIN, rebuilt)
        assertEquals(first.uiState.value, second.uiState.value)
    }

    @Test
    fun `unknown plugin preserves the original opts string verbatim`() {
        val opts = "anything=goes;here"
        val viewModel = SIP003EditorViewModel("some-other-plugin", opts)
        assertEquals(opts, viewModel.serialize())
    }

    @Test
    fun `setters update state independently`() {
        val viewModel = SIP003EditorViewModel(SIP003_V2RAY_PLUGIN, "")
        viewModel.setHost("first")
        assertEquals("first", viewModel.uiState.value.host)
        viewModel.setPath("/p")
        assertEquals("first", viewModel.uiState.value.host)
        assertEquals("/p", viewModel.uiState.value.path)
    }

    @Test
    fun `isDirty starts false and turns true after a mutation`() = runTest(dispatcher.scheduler) {
        val viewModel = SIP003EditorViewModel(SIP003_V2RAY_PLUGIN, "tls=1;mode=quic;host=h")
        backgroundScope.launch { viewModel.isDirty.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.isDirty.value)

        viewModel.setHost("changed")
        advanceUntilIdle()
        assertTrue(viewModel.isDirty.value)
    }

    @Test
    fun `isDirty returns to false when state is reverted to initial`() = runTest(dispatcher.scheduler) {
        val viewModel = SIP003EditorViewModel(SIP003_OBFS_LOCAL, "obfs=http;obfs-host=h")
        backgroundScope.launch { viewModel.isDirty.collect {} }
        advanceUntilIdle()

        viewModel.setObfsHost("changed")
        advanceUntilIdle()
        assertTrue(viewModel.isDirty.value)

        viewModel.setObfsHost("h")
        advanceUntilIdle()
        assertFalse(viewModel.isDirty.value)
    }
}
