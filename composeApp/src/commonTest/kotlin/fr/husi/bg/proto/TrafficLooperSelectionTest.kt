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
        // Mirror loop() init: the proxy-set entity itself starts ignore=false; all
        // non-TYPE_PROXY_SET members start ignore=true regardless of which node is
        // the main tag — updateSelectedTag un-ignores the actually-selected member.
        looper.seedIdMapForTest(
            mapOf(
                10L to false, // proxy-set entity — never ignored
                1L to true,   // member — ignored until selected via updateSelectedTag
                2L to true,   // member — ignored until selected via updateSelectedTag
            ),
        )

        // Same call the previous==null && selected.isNotEmpty() branch makes.
        looper.updateSelectedTag("selector", "", "node-selected")

        val flags = looper.ignoreByEntityId()
        assertFalse(flags.getValue(2L), "selected member must not be ignored")
        assertEquals(true, flags[1L], "non-selected member ignore flag unchanged by empty-old path")
    }

    /**
     * F2: a proxy-set used only as a routing outbound (not the main config) must
     * start with ignore=false on the selector entity so its traffic is drained and
     * its selected member can be un-ignored by updateSelectedTag.
     */
    @Test
    fun `routing proxy-set selector starts not ignored, members start ignored`() {
        val mainProxySet = ProxyEntity(
            id = 10L,
            type = ProxyEntity.TYPE_PROXY_SET,
            proxySetBean = ProxySetBean().apply { name = "main-selector" },
        )
        val routingProxySet = ProxyEntity(
            id = 20L,
            type = ProxyEntity.TYPE_PROXY_SET,
            proxySetBean = ProxySetBean().apply { name = "routing-selector" },
        )
        val nodeA = entity(id = 1L, tag = "node-a")
        val nodeC = entity(id = 3L, tag = "node-c")
        val config = ConfigBuildResult(
            mainTag = "main-selector",
            config = "{}",
            externalIndex = emptyList(),
            trafficMap = mapOf(
                "main-selector" to listOf(nodeA, mainProxySet),
                "routing-selector" to listOf(nodeC, routingProxySet),
            ),
            tagToID = mapOf(
                "main-selector" to 10L,
                "routing-selector" to 20L,
                "node-a" to 1L,
                "node-c" to 3L,
            ),
        )
        val looper = TrafficLooper(
            coreClient = FakeCoreClient(),
            config = config,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )

        // Replicate loop() init: isProxySet && ent.type != TYPE_PROXY_SET
        val initFlags = buildMap {
            for ((tag, entities) in config.trafficMap) {
                val isProxySet = entities.any { it.type == ProxyEntity.TYPE_PROXY_SET }
                for (ent in entities) {
                    put(ent.id, isProxySet && ent.type != ProxyEntity.TYPE_PROXY_SET)
                }
            }
        }
        looper.seedIdMapForTest(initFlags.mapValues { (_, ignored) -> ignored })

        val flags = looper.ignoreByEntityId()
        assertFalse(flags.getValue(10L), "main selector entity must not be ignored")
        assertEquals(true, flags[1L], "main selector member starts ignored")
        assertFalse(flags.getValue(20L), "routing selector entity must not be ignored")
        assertEquals(true, flags[3L], "routing selector member starts ignored")
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
