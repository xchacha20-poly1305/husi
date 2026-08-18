package fr.husi.ui.profile

import fr.husi.database.DataStore
import fr.husi.database.ProxyEntity
import fr.husi.database.ProxyGroup
import fr.husi.database.SagerDatabase
import fr.husi.fmt.internal.ChainBean
import fr.husi.fmt.internal.ProxySetBean
import fr.husi.fmt.socks.SOCKSBean
import fr.husi.ktx.applyDefaultValues
import fr.husi.resources.Res
import fr.husi.resources.circular_reference
import fr.husi.resources.error_title
import fr.husi.test.HusiKoinMainDispatcherTest
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileReferenceValidationTest : HusiKoinMainDispatcherTest() {

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
        SagerDatabase.proxyDao.reset()
        SagerDatabase.groupDao.reset()
    }

    @Test
    fun `chain editor rejects a proxy set that references the edited chain`() =
        runTest(dispatcher.scheduler) {
            val group = createGroup()
            val editedChain = createChain(group.id, "chain")
            val candidateSet = createProxySet(group.id, "set", listOf(editedChain.id))
            val viewModel = ChainSettingsViewModel()
            viewModel.initialize(editedChain.id, isSubscription = false)
            awaitState { viewModel.uiState.value.name == "chain" }
            val event = backgroundScope.async { viewModel.uiEvent.first() }

            viewModel.onSelectProfile(candidateSet.id)
            advanceUntilIdle()

            assertCircularReference(event.await())
            assertTrue(viewModel.uiState.value.profiles.isEmpty())
        }

    @Test
    fun `chain editor rejects a candidate whose group front references the edited chain`() =
        runTest(dispatcher.scheduler) {
            val group = createGroup()
            val editedChain = createChain(group.id, "chain")
            val frontChain = createChain(group.id, "front", listOf(editedChain.id))
            val candidate = createChain(group.id, "candidate")
            group.frontProxy = frontChain.id
            SagerDatabase.groupDao.updateGroup(group)
            val viewModel = ChainSettingsViewModel()
            viewModel.initialize(editedChain.id, isSubscription = false)
            awaitState { viewModel.uiState.value.name == "chain" }
            val event = backgroundScope.async { viewModel.uiEvent.first() }

            viewModel.onSelectProfile(candidate.id)
            advanceUntilIdle()

            assertCircularReference(event.await())
            assertTrue(viewModel.uiState.value.profiles.isEmpty())
        }

    @Test
    fun `chain editor rejects a candidate when group front shares a main member`() =
        runTest(dispatcher.scheduler) {
            val group = createGroup()
            val shared = createSocksProxy(group.id, "shared")
            val editedChain = createChain(group.id, "chain", listOf(shared.id))
            val frontChain = createChain(group.id, "front", listOf(shared.id))
            val candidate = createSocksProxy(group.id, "candidate")
            group.frontProxy = frontChain.id
            SagerDatabase.groupDao.updateGroup(group)
            val viewModel = ChainSettingsViewModel()
            viewModel.initialize(editedChain.id, isSubscription = false)
            awaitState { viewModel.uiState.value.profiles.map { it.id } == listOf(shared.id) }
            val event = backgroundScope.async { viewModel.uiEvent.first() }

            viewModel.onSelectProfile(candidate.id)
            advanceUntilIdle()

            assertCircularReference(event.await())
            assertEquals(listOf(shared.id), viewModel.uiState.value.profiles.map { it.id })
        }

    @Test
    fun `chain editor allows a nested reference to an existing member`() =
        runTest(dispatcher.scheduler) {
            val group = createGroup()
            val shared = createSocksProxy(group.id, "shared")
            val editedChain = createChain(group.id, "chain", listOf(shared.id))
            val nested = createChain(group.id, "nested", listOf(shared.id))
            val viewModel = ChainSettingsViewModel()
            viewModel.initialize(editedChain.id, isSubscription = false)
            awaitState { viewModel.uiState.value.profiles.map { it.id } == listOf(shared.id) }

            viewModel.onSelectProfile(nested.id)
            awaitState {
                viewModel.uiState.value.profiles.map { it.id } == listOf(shared.id, nested.id)
            }

            assertEquals(
                listOf(shared.id, nested.id),
                viewModel.uiState.value.profiles.map { it.id },
            )
        }

    @Test
    fun `proxy set editor rejects a chain that references the edited set`() =
        runTest(dispatcher.scheduler) {
            val group = createGroup()
            val editedSet = createProxySet(group.id, "set")
            val candidateChain = createChain(group.id, "chain", listOf(editedSet.id))
            val viewModel = ProxySetSettingsViewModel()
            viewModel.initialize(editedSet.id, isSubscription = false)
            awaitState { viewModel.uiState.value.name == "set" }
            val event = backgroundScope.async { viewModel.uiEvent.first() }

            viewModel.onSelectProfile(candidateChain.id)
            advanceUntilIdle()

            assertCircularReference(event.await())
            assertTrue(viewModel.uiState.value.providers.isEmpty())
        }

    @Test
    fun `proxy set editor rejects a collected group that references the edited set`() =
        runTest(dispatcher.scheduler) {
            val editedGroup = createGroup("edited")
            val collectedGroup = createGroup("collected")
            val editedSet = createProxySet(editedGroup.id, "set")
            createChain(collectedGroup.id, "chain", listOf(editedSet.id))
            val viewModel = ProxySetSettingsViewModel()
            viewModel.initialize(editedSet.id, isSubscription = false)
            awaitState { viewModel.uiState.value.name == "set" }
            val event = backgroundScope.async { viewModel.uiEvent.first() }

            viewModel.addGroupProvider(collectedGroup.id, "")
            advanceUntilIdle()

            assertCircularReference(event.await())
            assertTrue(viewModel.uiState.value.providers.isEmpty())
        }

    @Test
    fun `proxy set editor rejects a collected group that overlaps its group front`() =
        runTest(dispatcher.scheduler) {
            val editedGroup = createGroup("edited")
            val collectedGroup = createGroup("collected")
            val editedSet = createProxySet(editedGroup.id, "set")
            val shared = createSocksProxy(collectedGroup.id, "shared")
            val front = createChain(editedGroup.id, "front", listOf(shared.id))
            editedGroup.frontProxy = front.id
            SagerDatabase.groupDao.updateGroup(editedGroup)
            val viewModel = ProxySetSettingsViewModel()
            viewModel.initialize(editedSet.id, isSubscription = false)
            awaitState { viewModel.uiState.value.name == "set" }
            val event = backgroundScope.async { viewModel.uiEvent.first() }

            viewModel.addGroupProvider(collectedGroup.id, "")
            advanceUntilIdle()

            assertCircularReference(event.await())
            assertTrue(viewModel.uiState.value.providers.isEmpty())
        }

    @Test
    fun `proxy set editor revalidates a collected group when its filter changes`() =
        runTest(dispatcher.scheduler) {
            val editedGroup = createGroup("edited")
            val collectedGroup = createGroup("collected")
            val editedSet = createProxySet(editedGroup.id, "set")
            createChain(collectedGroup.id, "cycle", listOf(editedSet.id))
            createChain(collectedGroup.id, "safe")
            val viewModel = ProxySetSettingsViewModel()
            viewModel.initialize(editedSet.id, isSubscription = false)
            awaitState { viewModel.uiState.value.name == "set" }

            viewModel.addGroupProvider(collectedGroup.id, "safe")
            awaitState { viewModel.uiState.value.providers.isNotEmpty() }
            assertEquals(
                listOf(ProxySetBean.Provider.Group(collectedGroup.id, "safe")),
                viewModel.uiState.value.providers.map { it.toProvider() },
            )

            val event = backgroundScope.async { viewModel.uiEvent.first() }
            viewModel.setGroupProvider(0, collectedGroup.id, "")
            advanceUntilIdle()

            assertCircularReference(event.await())
            assertEquals(
                listOf(ProxySetBean.Provider.Group(collectedGroup.id, "safe")),
                viewModel.uiState.value.providers.map { it.toProvider() },
            )
        }

    @Test
    fun `proxy set editor rejects an invalid group filter regex`() =
        runTest(dispatcher.scheduler) {
            val group = createGroup()
            val editedSet = createProxySet(group.id, "set")
            val viewModel = ProxySetSettingsViewModel()
            viewModel.initialize(editedSet.id, isSubscription = false)
            awaitState { viewModel.uiState.value.name == "set" }
            val event = backgroundScope.async { viewModel.uiEvent.first() }

            viewModel.addGroupProvider(group.id, "[")
            advanceUntilIdle()

            val alert = assertIs<ProfileEditorUiEvent.Alert>(event.await())
            assertEquals(StringOrRes.Res(Res.string.error_title), alert.title)
            assertTrue(viewModel.uiState.value.providers.isEmpty())
        }

    private suspend fun TestScope.awaitState(predicate: () -> Boolean) {
        repeat(100) {
            advanceUntilIdle()
            if (predicate()) return
            withContext(Dispatchers.IO) { delay(5.milliseconds) }
        }
        fail("ViewModel state did not initialize")
    }

    private suspend fun createGroup(name: String = "group"): ProxyGroup {
        val group = ProxyGroup(name = name).applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        return group
    }

    private suspend fun createChain(
        groupId: Long,
        name: String,
        proxies: List<Long> = emptyList(),
    ): ProxyEntity {
        val chain = ProxyEntity(groupId = groupId).putBean(
            ChainBean().apply {
                this.name = name
                this.proxies = proxies
            }.applyDefaultValues(),
        )
        chain.id = SagerDatabase.proxyDao.addProxy(chain)
        return chain
    }

    private suspend fun createProxySet(
        groupId: Long,
        name: String,
        proxies: List<Long> = emptyList(),
    ): ProxyEntity {
        val proxySet = ProxyEntity(groupId = groupId).putBean(
            ProxySetBean().apply {
                this.name = name
                this.providers = proxies.map { ProxySetBean.Provider.Single(it) }
            }.applyDefaultValues(),
        )
        proxySet.id = SagerDatabase.proxyDao.addProxy(proxySet)
        return proxySet
    }

    private suspend fun createSocksProxy(groupId: Long, name: String): ProxyEntity {
        val proxy = ProxyEntity(groupId = groupId).putBean(
            SOCKSBean().apply {
                this.name = name
                serverAddress = "1.1.1.1"
                serverPort = 1080
            }.applyDefaultValues(),
        )
        proxy.id = SagerDatabase.proxyDao.addProxy(proxy)
        return proxy
    }

    private fun assertCircularReference(event: ProfileEditorUiEvent) {
        val alert = assertIs<ProfileEditorUiEvent.Alert>(event)
        assertEquals(StringOrRes.Res(Res.string.circular_reference), alert.title)
    }
}
