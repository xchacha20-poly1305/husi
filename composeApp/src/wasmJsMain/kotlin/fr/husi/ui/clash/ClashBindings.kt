package fr.husi.ui.clash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import fr.husi.remote.ClashSession
import fr.husi.remote.RemoteState
import fr.husi.resources.Res
import fr.husi.resources.clash_smart_flush_config_success
import fr.husi.resources.clash_smart_flush_success
import fr.husi.resources.clash_smart_upgrade_lgbm_success
import fr.husi.ui.LocalNavigator
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun ClashRulesBinding(modifier: Modifier = Modifier) {
    val remote = koinInject<RemoteState>()
    val session = remote.currentSession as? ClashSession ?: return
    val model = session.rules
    key(session) {
        val scope = rememberCoroutineScope()
        val navigator = LocalNavigator.current
        LaunchedEffect(model) { model.refresh() }
        ClashRulesScreen(
            state = model.state,
            onRefresh = { scope.launch { model.refresh() } },
            onToggle = { rule -> scope.launch { model.toggle(rule) } },
            onBackPress = { navigator.popBackStack() },
            modifier = modifier,
        )
    }
}

@Composable
internal fun ClashProvidersBinding(modifier: Modifier = Modifier) {
    val remote = koinInject<RemoteState>()
    val session = remote.currentSession as? ClashSession ?: return
    key(session) {
        val model = remember(session) { ClashProvidersModel(session.api) }
        val scope = rememberCoroutineScope()
        val navigator = LocalNavigator.current
        LaunchedEffect(model) {
            launch { model.proxies.refresh() }
            launch { model.rules.refresh() }
        }
        ClashProvidersScreen(
            proxies = model.proxies.state,
            rules = model.rules.state,
            onRefreshProxies = { scope.launch { model.proxies.refresh() } },
            onRefreshRules = { scope.launch { model.rules.refresh() } },
            onUpdateProxyProvider = { name -> scope.launch { model.updateProxyProvider(name) } },
            onHealthCheck = { name -> scope.launch { model.healthCheck(name) } },
            onTestDelay = { name, tag -> scope.launch { model.testDelay(name, tag) } },
            onUpdateRuleProvider = { name -> scope.launch { model.updateRuleProvider(name) } },
            onBackPress = { navigator.popBackStack() },
            modifier = modifier,
        )
    }
}

@Composable
internal fun ClashDnsBinding(modifier: Modifier = Modifier) {
    val remote = koinInject<RemoteState>()
    val session = remote.currentSession as? ClashSession ?: return
    key(session) {
        val model = remember(session) { ClashDnsModel(session.api) }
        val scope = rememberCoroutineScope()
        val navigator = LocalNavigator.current
        ClashDnsScreen(
            state = model.result.state,
            onQuery = { name, type -> scope.launch { model.query(name, type) } },
            onBackPress = { navigator.popBackStack() },
            modifier = modifier,
        )
    }
}

@Composable
internal fun ClashSmartBinding(modifier: Modifier = Modifier) {
    val remote = koinInject<RemoteState>()
    val session = remote.currentSession as? ClashSession ?: return
    key(session) {
        val model = remember(session) { ClashSmartModel(session.api) { session.refreshProxies() } }
        val scope = rememberCoroutineScope()
        val navigator = LocalNavigator.current
        val snackbar = LocalSnackbarEmitter.current
        LaunchedEffect(model) { model.refresh() }
        ClashSmartScreen(
            state = model.state,
            onRefresh = { scope.launch { model.refresh() } },
            onRefreshGroupWeights = { name -> scope.launch { model.refreshGroupWeights(name) } },
            onClearFixed = { name ->
                scope.launch {
                    model.clearFixed(name, onError = snackbar::show)
                }
            },
            onFlushAllCache = {
                scope.launch {
                    model.flushAllCache(
                        onSuccess = { snackbar.show(StringOrRes.Res(Res.string.clash_smart_flush_success)) },
                        onError = snackbar::show,
                    )
                }
            },
            onFlushConfigCache = { config ->
                scope.launch {
                    model.flushConfigCache(
                        rawConfig = config,
                        onSuccess = {
                            snackbar.show(
                                StringOrRes.ResWithParams(Res.string.clash_smart_flush_config_success, it),
                            )
                        },
                        onError = snackbar::show,
                    )
                }
            },
            onUpgradeLgbm = {
                scope.launch {
                    model.upgradeLgbm(
                        onSuccess = { snackbar.show(StringOrRes.Res(Res.string.clash_smart_upgrade_lgbm_success)) },
                        onError = snackbar::show,
                    )
                }
            },
            onBackPress = { navigator.popBackStack() },
            modifier = modifier,
        )
    }
}
