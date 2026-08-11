package fr.husi.bg.proto

import fr.husi.database.ProxyEntity
import fr.husi.fmt.ConfigBuildResult
import fr.husi.fmt.internal.ProxySetBean
import fr.husi.fmt.shadowsocks.ShadowsocksBean
import fr.husi.test.FakeCoreClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * F1: initial groups snapshot with selected ≠ main must un-ignore the selected
 * proxy-set member so its traffic contributes to speed sums immediately.
 */
class TrafficLooperSelectionTest {

    @Test
    fun `updateSelectedTag with empty old un-ignores selected member`() {
        val main = entity(id = 1L, tag = "node-main")
        val selected = entity(id = 2L, tag = "node-selected")
        val proxySet = ProxyEntity(
            id = 10L,
            type = ProxyEntity.TYPE_PROXY_SET,
            proxySetBean = ProxySetBean().apply {
                name = "selector"
            },
        )
        val config = ConfigBuildResult(
            mainTag = "node-main",
            config = "{}",
            externalIndex = emptyList(),
            trafficMap = mapOf(
                "selector" to listOf(proxySet, main, selected),
            ),
            tagToID = mapOf(
                "node-main" to 1L,
                "node-selected" to 2L,
                "selector" to 10L,
            ),
        )
        val looper = TrafficLooper(
            coreClient = FakeCoreClient(),
            config = config,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
        // Mirror loop() init: proxy-set non-main members start ignored.
        looper.seedIdMapForTest(
            mapOf(
                10L to false,
                1L to false, // main
                2L to true, // selected-but-ignored until groups snapshot
            ),
        )

        // Same call the previous==null && selected.isNotEmpty() branch makes.
        looper.updateSelectedTag("selector", "", "node-selected")

        val flags = looper.ignoreByEntityId()
        assertFalse(flags.getValue(2L), "selected member must not be ignored")
        assertEquals(false, flags[1L], "main ignore flag unchanged by empty-old path")
    }

    private fun entity(id: Long, tag: String): ProxyEntity =
        ProxyEntity(
            id = id,
            type = ProxyEntity.TYPE_SS,
            ssBean = ShadowsocksBean().apply {
                name = tag
                serverAddress = "127.0.0.1"
                serverPort = 1
                method = "none"
                password = "x"
            },
        )
}
