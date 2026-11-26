package fr.husi.ui.profile

import fr.husi.database.DataStore
import fr.husi.database.ProxyEntity
import fr.husi.database.ProxyGroup
import fr.husi.database.SagerDatabase
import fr.husi.database.preference.reset
import fr.husi.fmt.plugin.CustomPluginBean
import fr.husi.ktx.applyDefaultValues
import fr.husi.test.HusiKoinMainDispatcherTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class CustomPluginSettingsViewModelTest : HusiKoinMainDispatcherTest() {

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
        SagerDatabase.proxyDao.reset()
        SagerDatabase.groupDao.reset()
    }

    private fun newViewModel(editingId: Long = -1L, isSubscription: Boolean = false) =
        CustomPluginSettingsViewModel().also {
            it.initialize(editingId = editingId, isSubscription = isSubscription)
        }

    @Test
    fun `initial state has expected defaults before and after new profile initialize`() =
        runTest(dispatcher.scheduler) {
            val viewModel = CustomPluginSettingsViewModel()

            assertEquals(CustomPluginUiState(), viewModel.uiState.value)
            assertFalse(viewModel.isDirty.value)

            viewModel.initialize(editingId = -1L, isSubscription = false)
            advanceUntilIdle()

            assertTrue(viewModel.isNew)
            assertEquals(-1L, viewModel.editingId)
            assertFalse(viewModel.isSubscription)
            val state = viewModel.uiState.value
            assertEquals("", state.name)
            assertEquals("", state.pluginId)
            assertEquals("", state.executablePath)
            assertFalse(state.supportProtect)
            assertEquals("", state.serverAddress)
            assertEquals(0, state.serverPort)
            assertEquals("", state.commandArgs)
            assertEquals(CustomPluginBean.DEFAULT_CONFIG_FILE_NAME, state.configFileName)
            assertEquals("", state.config)
            assertEquals("", state.environment)
            assertEquals("", state.customConfig)
            assertEquals("", state.customOutbound)
        }

    @Test
    fun `setters update uiState fields accurately and independently`() =
        runTest(dispatcher.scheduler) {
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.setName(NAME_A)
            assertEquals(NAME_A, viewModel.uiState.value.name)
            assertEquals("", viewModel.uiState.value.pluginId)

            viewModel.setPluginId(PLUGIN_ID_A)
            viewModel.setExecutablePath(EXECUTABLE_PATH_A)
            viewModel.setSupportProtect(true)
            viewModel.setServerAddress(SERVER_ADDRESS_A)
            viewModel.setServerPort(SERVER_PORT_A)
            viewModel.setCommandArgs(COMMAND_ARGS_A)
            viewModel.setConfigFileName(CONFIG_FILE_NAME_A)
            viewModel.setConfig(CONFIG_A)
            viewModel.setEnvironment(ENVIRONMENT_A)
            viewModel.setCustomConfig(CUSTOM_CONFIG_A)
            viewModel.setCustomOutbound(CUSTOM_OUTBOUND_A)

            val state = viewModel.uiState.value
            assertEquals(NAME_A, state.name)
            assertEquals(PLUGIN_ID_A, state.pluginId)
            assertEquals(EXECUTABLE_PATH_A, state.executablePath)
            assertTrue(state.supportProtect)
            assertEquals(SERVER_ADDRESS_A, state.serverAddress)
            assertEquals(SERVER_PORT_A, state.serverPort)
            assertEquals(COMMAND_ARGS_A, state.commandArgs)
            assertEquals(CONFIG_FILE_NAME_A, state.configFileName)
            assertEquals(CONFIG_A, state.config)
            assertEquals(ENVIRONMENT_A, state.environment)
            assertEquals(CUSTOM_CONFIG_A, state.customConfig)
            assertEquals(CUSTOM_OUTBOUND_A, state.customOutbound)

            viewModel.setSupportProtect(false)
            assertFalse(viewModel.uiState.value.supportProtect)
            assertEquals(NAME_A, viewModel.uiState.value.name)
        }

    @Test
    fun `isDirty starts false turns true on mutation reverts to false and resets on reinitialize`() =
        runTest(dispatcher.scheduler) {
            val viewModel = CustomPluginSettingsViewModel()
            backgroundScope.launch { viewModel.isDirty.collect {} }
            advanceUntilIdle()
            assertFalse(viewModel.isDirty.value)

            viewModel.initialize(editingId = -1L, isSubscription = false)
            advanceUntilIdle()
            assertFalse(viewModel.isDirty.value)

            viewModel.setName(NAME_A)
            advanceUntilIdle()
            assertTrue(viewModel.isDirty.value)

            viewModel.setName("")
            advanceUntilIdle()
            assertFalse(viewModel.isDirty.value)

            viewModel.setSupportProtect(true)
            viewModel.setServerPort(SERVER_PORT_A)
            advanceUntilIdle()
            assertTrue(viewModel.isDirty.value)

            viewModel.initialize(editingId = -1L, isSubscription = false)
            advanceUntilIdle()
            assertFalse(viewModel.isDirty.value)
            assertEquals(CustomPluginUiState(), viewModel.uiState.value)
        }

    @Test
    fun `writeToUiState and loadFromUiState round-trip CustomPluginBean through initialize and save`() =
        runTest(dispatcher.scheduler) {
            val group = createGroup()
            val initialBean = CustomPluginBean().apply {
                name = NAME_A
                pluginId = PLUGIN_ID_A
                executablePath = EXECUTABLE_PATH_A
                supportProtect = true
                serverAddress = SERVER_ADDRESS_A
                serverPort = SERVER_PORT_A
                commandArgs = COMMAND_ARGS_A
                configFileName = CONFIG_FILE_NAME_A
                config = CONFIG_A
                environment = ENVIRONMENT_A
                customConfigJson = CUSTOM_CONFIG_A
                customOutboundJson = CUSTOM_OUTBOUND_A
            }.applyDefaultValues()
            val entity = createCustomPluginEntity(group.id, initialBean)

            val viewModel = CustomPluginSettingsViewModel()
            backgroundScope.launch { viewModel.isDirty.collect {} }
            viewModel.initialize(editingId = entity.id, isSubscription = true)
            awaitState("CustomPluginSettingsViewModel did not finish initializing") {
                viewModel.uiState.value.name == NAME_A
            }

            assertFalse(viewModel.isNew)
            assertEquals(entity.id, viewModel.editingId)
            assertTrue(viewModel.isSubscription)
            assertFalse(viewModel.isDirty.value)

            val loadedState = viewModel.uiState.value
            assertEquals(NAME_A, loadedState.name)
            assertEquals(PLUGIN_ID_A, loadedState.pluginId)
            assertEquals(EXECUTABLE_PATH_A, loadedState.executablePath)
            assertTrue(loadedState.supportProtect)
            assertEquals(SERVER_ADDRESS_A, loadedState.serverAddress)
            assertEquals(SERVER_PORT_A, loadedState.serverPort)
            assertEquals(COMMAND_ARGS_A, loadedState.commandArgs)
            assertEquals(CONFIG_FILE_NAME_A, loadedState.configFileName)
            assertEquals(CONFIG_A, loadedState.config)
            assertEquals(ENVIRONMENT_A, loadedState.environment)
            assertEquals(CUSTOM_CONFIG_A, loadedState.customConfig)
            assertEquals(CUSTOM_OUTBOUND_A, loadedState.customOutbound)

            viewModel.setName(NAME_B)
            viewModel.setPluginId(PLUGIN_ID_B)
            viewModel.setExecutablePath(EXECUTABLE_PATH_B)
            viewModel.setSupportProtect(false)
            viewModel.setServerAddress(SERVER_ADDRESS_B)
            viewModel.setServerPort(SERVER_PORT_B)
            viewModel.setCommandArgs(COMMAND_ARGS_B)
            viewModel.setConfigFileName(CONFIG_FILE_NAME_B)
            viewModel.setConfig(CONFIG_B)
            viewModel.setEnvironment(ENVIRONMENT_B)
            viewModel.setCustomConfig(CUSTOM_CONFIG_B)
            viewModel.setCustomOutbound(CUSTOM_OUTBOUND_B)
            advanceUntilIdle()
            assertTrue(viewModel.isDirty.value)

            viewModel.save().join()

            val persistedEntity = withContext(Dispatchers.IO) {
                SagerDatabase.proxyDao.getById(entity.id)
            }
            val persistedBean = assertIs<CustomPluginBean>(assertNotNull(persistedEntity).requireBean())
            assertEquals(NAME_B, persistedBean.name)
            assertEquals(PLUGIN_ID_B, persistedBean.pluginId)
            assertEquals(EXECUTABLE_PATH_B, persistedBean.executablePath)
            assertFalse(persistedBean.supportProtect)
            assertEquals(SERVER_ADDRESS_B, persistedBean.serverAddress)
            assertEquals(SERVER_PORT_B, persistedBean.serverPort)
            assertEquals(COMMAND_ARGS_B, persistedBean.commandArgs)
            assertEquals(CONFIG_FILE_NAME_B, persistedBean.configFileName)
            assertEquals(CONFIG_B, persistedBean.config)
            assertEquals(ENVIRONMENT_B, persistedBean.environment)
            assertEquals(CUSTOM_CONFIG_B, persistedBean.customConfigJson)
            assertEquals(CUSTOM_OUTBOUND_B, persistedBean.customOutboundJson)

            val reloadedViewModel = newViewModel(editingId = entity.id, isSubscription = false)
            awaitState("Reloaded CustomPluginSettingsViewModel did not finish initializing") {
                reloadedViewModel.uiState.value.name == NAME_B
            }
            assertEquals(viewModel.uiState.value, reloadedViewModel.uiState.value)
        }

    @Test
    fun `save new profile persists CustomPluginBean from uiState`() =
        runTest(dispatcher.scheduler) {
            val group = createGroup()
            DataStore.selectedGroup.set(group.id)

            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.setName(NAME_A)
            viewModel.setPluginId(PLUGIN_ID_A)
            viewModel.setExecutablePath(EXECUTABLE_PATH_A)
            viewModel.setSupportProtect(true)
            viewModel.setServerAddress(SERVER_ADDRESS_A)
            viewModel.setServerPort(SERVER_PORT_A)
            viewModel.setCommandArgs(COMMAND_ARGS_A)
            viewModel.setConfigFileName(CONFIG_FILE_NAME_A)
            viewModel.setConfig(CONFIG_A)
            viewModel.setEnvironment(ENVIRONMENT_A)
            viewModel.setCustomConfig(CUSTOM_CONFIG_A)
            viewModel.setCustomOutbound(CUSTOM_OUTBOUND_A)

            viewModel.save().join()

            val savedProxies = withContext(Dispatchers.IO) {
                SagerDatabase.proxyDao.getByGroup(group.id).first()
            }
            assertEquals(1, savedProxies.size)
            val savedBean = assertIs<CustomPluginBean>(savedProxies.single().requireBean())
            assertEquals(NAME_A, savedBean.name)
            assertEquals(PLUGIN_ID_A, savedBean.pluginId)
            assertEquals(EXECUTABLE_PATH_A, savedBean.executablePath)
            assertTrue(savedBean.supportProtect)
            assertEquals(SERVER_ADDRESS_A, savedBean.serverAddress)
            assertEquals(SERVER_PORT_A, savedBean.serverPort)
            assertEquals(COMMAND_ARGS_A, savedBean.commandArgs)
            assertEquals(CONFIG_FILE_NAME_A, savedBean.configFileName)
            assertEquals(CONFIG_A, savedBean.config)
            assertEquals(ENVIRONMENT_A, savedBean.environment)
            assertEquals(CUSTOM_CONFIG_A, savedBean.customConfigJson)
            assertEquals(CUSTOM_OUTBOUND_A, savedBean.customOutboundJson)
        }

    private suspend fun TestScope.awaitState(
        failureMessage: String,
        predicate: () -> Boolean,
    ) {
        repeat(100) {
            advanceUntilIdle()
            if (predicate()) return
            withContext(Dispatchers.IO) { delay(5.milliseconds) }
        }
        fail(failureMessage)
    }

    private suspend fun createGroup(name: String = "group"): ProxyGroup {
        val group = ProxyGroup(name = name).applyDefaultValues()
        group.id = withContext(Dispatchers.IO) {
            SagerDatabase.groupDao.createGroup(group)
        }
        return group
    }

    private suspend fun createCustomPluginEntity(groupId: Long, bean: CustomPluginBean): ProxyEntity {
        val entity = ProxyEntity(groupId = groupId).putBean(bean)
        entity.id = withContext(Dispatchers.IO) {
            SagerDatabase.proxyDao.addProxy(entity)
        }
        return entity
    }

    private companion object {
        const val NAME_A = "Custom Plugin A"
        const val PLUGIN_ID_A = "fr.husi.plugin.custom.a"
        const val EXECUTABLE_PATH_A = "/usr/local/bin/custom-plugin-a"
        const val SERVER_ADDRESS_A = "203.0.113.10"
        const val SERVER_PORT_A = 10808
        const val COMMAND_ARGS_A = "-c %c -l 127.0.0.1:%p"
        const val CONFIG_FILE_NAME_A = "plugin-a.json"
        const val CONFIG_A = """{"listen":"127.0.0.1:%p"}"""
        const val ENVIRONMENT_A = "FOO=bar\nBAZ=qux"
        const val CUSTOM_CONFIG_A = """{"log":{"level":"debug"}}"""
        const val CUSTOM_OUTBOUND_A = """{"tag":"custom-a"}"""

        const val NAME_B = "Custom Plugin B"
        const val PLUGIN_ID_B = "fr.husi.plugin.custom.b"
        const val EXECUTABLE_PATH_B = "/opt/plugins/custom-plugin-b"
        const val SERVER_ADDRESS_B = "198.51.100.20"
        const val SERVER_PORT_B = 20808
        const val COMMAND_ARGS_B = "--config=%c --socks-port=%p"
        const val CONFIG_FILE_NAME_B = "plugin-b.toml"
        const val CONFIG_B = "port = %p"
        const val ENVIRONMENT_B = "MODE=strict"
        const val CUSTOM_CONFIG_B = """{"log":{"level":"info"}}"""
        const val CUSTOM_OUTBOUND_B = """{"tag":"custom-b"}"""
    }
}
