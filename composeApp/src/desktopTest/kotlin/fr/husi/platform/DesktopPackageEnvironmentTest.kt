package fr.husi.platform

import fr.husi.Key
import fr.husi.database.DataStore
import fr.husi.database.preference.boolean
import fr.husi.database.preference.reset
import fr.husi.test.HusiKoinTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopPackageEnvironmentTest : HusiKoinTest() {
    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    private fun detect(environment: Map<String, String> = emptyMap(), flatpakInfo: Boolean = false) =
        DesktopPackageEnvironment.detect(Platform.Linux, environment) { flatpakInfo }

    @Test
    fun `each package marker independently enables NetworkManager by default`() {
        for (name in listOf("FLATPAK_ID", "APPIMAGE", "APPDIR")) {
            val detected = detect(mapOf(name to "/package"))
            assertTrue(detected.defaultNetworkManagerTun, name)
            assertEquals(name == "FLATPAK_ID", detected.isFlatpak)
        }
        assertTrue(detect(flatpakInfo = true).defaultNetworkManagerTun)
    }

    @Test
    fun `blank markers and ordinary Linux keep the native default`() {
        assertFalse(detect().defaultNetworkManagerTun)
        assertFalse(detect(mapOf("FLATPAK_ID" to "", "APPIMAGE" to " ", "APPDIR" to "")).defaultNetworkManagerTun)
    }

    @Test
    fun `non Linux platforms ignore package markers and do not inspect Flatpak files`() {
        for (platform in Platform.entries.filter { it != Platform.Linux }) {
            val detected = DesktopPackageEnvironment.detect(
                platform,
                mapOf("FLATPAK_ID" to "fr.husi.Husi", "APPIMAGE" to "/app", "APPDIR" to "/dir"),
            ) { error("Must not inspect Linux files on $platform") }
            assertFalse(detected.defaultNetworkManagerTun)
            assertTrue(detected.canInstallSystemDaemon)
        }
    }

    @Test
    fun `only Flatpak prevents installing a daemon`() {
        assertFalse(detect(flatpakInfo = true).canInstallSystemDaemon)
        assertTrue(detect(mapOf("APPIMAGE" to "/app")).canInstallSystemDaemon)
        assertTrue(detect().canInstallSystemDaemon)
    }

    @Test
    fun `saved backend preferences override package defaults including explicit false`() = runTest {
        for (packaged in listOf(false, true)) {
            DataStore.configurationStore.reset()
            val detected = detect(flatpakInfo = packaged)
            val preference = DataStore.configurationStore.boolean(Key.NETWORK_MANAGER_TUN) {
                detected.defaultNetworkManagerTun
            }
            assertNull(preference.getOrNull())
            assertEquals(packaged, preference.get())
            assertEquals(packaged, preference.flow().first())
            for (saved in listOf(false, true)) {
                DataStore.networkManagerTun.set(saved)
                assertEquals(saved, preference.get())
                assertEquals(saved, preference.flow().first())
                assertEquals(saved, DataStore.networkManagerTun.get())
            }
        }
    }
}
