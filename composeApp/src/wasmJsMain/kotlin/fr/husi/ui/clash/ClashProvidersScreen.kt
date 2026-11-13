package fr.husi.ui.clash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.material3.Text
import fr.husi.remote.ClashProxyProvider
import fr.husi.remote.ClashRuleProvider
import fr.husi.ktx.formatMemoryBytes
import fr.husi.resources.available
import fr.husi.resources.Res
import fr.husi.resources.subscription_expire
import fr.husi.resources.clash_health_check
import fr.husi.resources.clash_providers
import fr.husi.resources.clash_proxy_providers
import fr.husi.resources.clash_rule_count
import fr.husi.resources.clash_rule_providers
import fr.husi.resources.clash_test_delay
import fr.husi.resources.clash_unsupported
import fr.husi.resources.subscription_last_updated
import fr.husi.resources.clash_usage
import fr.husi.resources.update
import fr.husi.resources.refresh
import fr.husi.resources.cached
import fr.husi.ui.settings.SettingsPage
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun ClashProvidersScreen(
    proxies: ClashResourceState<List<ClashProxyProvider>>,
    rules: ClashResourceState<List<ClashRuleProvider>>,
    onRefreshProxies: () -> Unit,
    onRefreshRules: () -> Unit,
    onUpdateProxyProvider: (String) -> Unit,
    onHealthCheck: (String) -> Unit,
    onTestDelay: (String, String) -> Unit,
    onUpdateRuleProvider: (String) -> Unit,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(Res.string.clash_providers, modifier = modifier, onBackPress = onBackPress) {
        item {
            ProviderSectionTitle(
                title = stringResource(Res.string.clash_proxy_providers),
                loading = proxies.loading,
                onRefresh = onRefreshProxies,
            )
        }
        clashStatus(proxies.loading, proxies.error, proxies.unsupported)
        val providers = proxies.data.orEmpty()
        if (providers.isEmpty() && !proxies.loading) {
            item {
                EmptyClashList()
            }
        }
        for (provider in providers) {
            val name = provider.name
            clashCard(key = "proxy-$name") {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        listOf(provider.type, provider.vehicleType)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                    )
                    val updatedAt = formatUpdatedAt(provider.updatedAt)
                    Text(
                        stringResource(Res.string.subscription_last_updated, updatedAt),
                    )
                    provider.subscription?.let { subscription ->
                        val usedBytes = subscription.upload + subscription.download
                        val used = usedBytes.formatMemoryBytes()
                        val total = subscription.total.formatMemoryBytes()
                        Text(
                            stringResource(Res.string.clash_usage, used, total),
                        )
                        subscription.expire.takeIf { it > 0 }?.let {
                            val expire = formatExpiry(it.toDouble())
                            Text(
                                stringResource(Res.string.subscription_expire, expire),
                            )
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProviderAction(proxies, "update-$name", Res.string.update) {
                        onUpdateProxyProvider(name)
                    }
                    ProviderAction(proxies, "health-$name", Res.string.clash_health_check) {
                        onHealthCheck(name)
                    }
                }
            }
            for (proxy in provider.proxies) {
                val tag = proxy.name
                val key = "delay-$name-$tag"
                clashCard(key = key) {
                    Text(tag, style = MaterialTheme.typography.titleSmall)
                    Text(proxy.type, style = MaterialTheme.typography.bodyMedium)
                    ProviderAction(proxies, key, Res.string.clash_test_delay) {
                        onTestDelay(name, tag)
                    }
                }
            }
        }
        item {
            ProviderSectionTitle(
                title = stringResource(Res.string.clash_rule_providers),
                loading = rules.loading,
                onRefresh = onRefreshRules,
            )
        }
        clashStatus(rules.loading, rules.error, rules.unsupported)
        val ruleProviders = rules.data.orEmpty()
        if (ruleProviders.isEmpty() && !rules.loading) {
            item {
                EmptyClashList()
            }
        }
        for (provider in ruleProviders) {
            val name = provider.name
            clashCard(key = "rules-$name") {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        listOf(
                            provider.type,
                            provider.behavior,
                            provider.format,
                        )
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                    )
                    Text(
                        stringResource(Res.string.clash_rule_count, provider.ruleCount),
                    )
                    Text(
                        stringResource(
                            Res.string.subscription_last_updated,
                            formatUpdatedAt(provider.updatedAt),
                        ),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProviderAction(rules, name, Res.string.update) {
                        onUpdateRuleProvider(name)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderAction(
    state: ClashResourceState<*>,
    key: String,
    label: StringResource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            enabled = state.pending[key] != true && state.unavailable[key] != true,
            onClick = onClick,
        ) {
            Text(stringResource(label))
        }
        when {
            state.unavailable[key] == true -> Text(stringResource(Res.string.clash_unsupported))
            state.delayResults[key] != null -> Text(
                stringResource(Res.string.available, state.delayResults.getValue(key)),
            )
        }
    }
}

@Composable
private fun ProviderSectionTitle(
    title: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        SimpleIconButton(
            imageVector = vectorResource(Res.drawable.cached),
            contentDescription = stringResource(Res.string.refresh),
            enabled = !loading,
            onClick = onRefresh,
        )
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("seconds => new Date(seconds * 1000).toLocaleString()")
private external fun formatExpiry(seconds: Double): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "value => {const time = Date.parse(value); return Number.isFinite(time) ? new Date(time).toLocaleString() : value;}",
)
private external fun formatUpdatedAt(value: String): String
