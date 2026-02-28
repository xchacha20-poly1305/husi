package fr.husi.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.compose.PreferenceType
import fr.husi.database.DataStore
import fr.husi.resources.Res
import fr.husi.resources.arrow_and_edge
import fr.husi.resources.tun_strict_route
import me.zhanghai.compose.preference.SwitchPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

internal actual fun LazyListScope.autoConnect() {
}

@Composable
internal actual fun rememberApplyNightMode(): (Int) -> Unit = {}

internal actual fun LazyListScope.platformGeneralOptions(needReload: () -> Unit) {
}

internal actual fun LazyListScope.meteredNetworkSetting(needReload: () -> Unit) {
}

internal actual fun LazyListScope.platformRouteOptions(needReload: () -> Unit, isVpnMode: Boolean) {
    item(Key.TUN_STRICT_ROUTE, PreferenceType.SWITCH) {
        val value by DataStore.configurationStore
            .booleanFlow(Key.TUN_STRICT_ROUTE, true)
            .collectAsStateWithLifecycle(true)
        SwitchPreference(
            value = value,
            onValueChange = {
                DataStore.tunStrictRoute = it
                needReload()
            },
            title = { Text(stringResource(Res.string.tun_strict_route)) },
            icon = { Icon(vectorResource(Res.drawable.arrow_and_edge), null) },
            enabled = isVpnMode,
        )
    }
}

internal actual fun LazyListScope.platformMiscOptions(needReload: () -> Unit) {
}

@Composable
internal actual fun rememberThemeExtraColors(): List<Color> = emptyList()

@Composable
internal actual fun rememberAppLanguageController(defaultTag: String): AppLanguageController {
    val flow = DataStore.configurationStore
        .stringFlow(Key.APP_LANGUAGE, defaultTag)
    val state by flow.collectAsStateWithLifecycle(defaultTag)
    return object : AppLanguageController {
        override var value: String
            get() = state.ifBlank { defaultTag }
            set(value) {
                DataStore.appLanguage = value
            }
        override val flow = flow
    }
}

internal actual fun LazyListScope.disableProcessText() {
}

internal actual fun LazyListScope.httpProxyBypass(enabled: Boolean, needReload: () -> Unit) {
}
