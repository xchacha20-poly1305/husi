package fr.husi.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.compose.PreferenceType
import fr.husi.database.DataStore
import fr.husi.resources.*
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SwitchPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

internal actual fun LazyListScope.appSelectPreference(
    packages: Set<String>,
    onSelectApps: (Set<String>) -> Unit,
) {
    item("apps") {
        Preference(
            title = { Text(stringResource(Res.string.apps)) },
            icon = { Icon(vectorResource(Res.drawable.legend_toggle), null) },
            summary = {
                val text = when (val size = packages.size) {
                    0 -> stringResource(Res.string.not_set)
                    in 1..5 -> packages.joinToString("\n")
                    else -> stringResource(Res.string.apps_message, size)
                }
                Text(text)
            },
            onClick = {
                onSelectApps(packages)
            },
        )
    }
}

internal actual fun LazyListScope.proxyAppsPreferences(
    openAppManager: () -> Unit,
) {
    item(Key.PROXY_APPS, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.PROXY_APPS, false)
            .collectAsStateWithLifecycle(false)
        SwitchPreference(
            value = value,
            onValueChange = {
                openAppManager()
            },
            title = { Text(stringResource(Res.string.proxied_apps)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.apps),
                    null,
                )
            },
            summary = { Text(stringResource(Res.string.proxied_apps_summary)) },
        )
    }
    item(Key.UPDATE_PROXY_APPS_WHEN_INSTALL, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.UPDATE_PROXY_APPS_WHEN_INSTALL, false)
            .collectAsStateWithLifecycle(false)
        SwitchPreference(
            value = value,
            onValueChange = { DataStore.updateProxyAppsWhenInstall = it },
            title = { Text(stringResource(Res.string.update_proxy_apps_when_install)) },
            icon = {
                Icon(
                    vectorResource(Res.drawable.keyboard_tab),
                    null,
                )
            },
        )
    }
}
