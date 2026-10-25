package fr.husi.database

import fr.husi.test.HusiKoinTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdvancedFeatureFlowTest : HusiKoinTest() {

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    private suspend fun featureOf(list: String, key: String): String? {
        DataStore.advancedFeatures.set(list)
        return DataStore.advancedFeatureFlow(key).first()
    }

    @Test
    fun `bare key reads back as an empty value`() = runTest {
        assertEquals("", featureOf("debug", "debug"))
    }

    @Test
    fun `key value line keeps everything after the first separator`() = runTest {
        assertEquals("127.0.0.1:6060=x", featureOf(" debugListen = 127.0.0.1:6060=x ", "debugListen"))
    }

    @Test
    fun `unlisted key is null`() = runTest {
        assertNull(featureOf("\n  \n=orphan\r\ndebug\r\n", "anchorSSID"))
    }

    @Test
    fun `later line overrides an earlier one with the same key`() = runTest {
        assertEquals("b", featureOf("customPluginPrefix=a\ncustomPluginPrefix=b", "customPluginPrefix"))
    }

    @Test
    fun `flow follows list changes`() = runTest {
        DataStore.advancedFeatures.set("")
        assertNull(DataStore.advancedFeature("debug"))

        DataStore.advancedFeatures.set("debug")
        assertEquals("", DataStore.advancedFeature("debug"))
    }
}
