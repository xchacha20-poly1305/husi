package fr.husi.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.database.DataStore

internal actual fun LazyListScope.autoConnect() {
}

@Composable
internal actual fun rememberApplyNightMode(): (Int) -> Unit = {}

internal actual fun LazyListScope.androidGeneralOptions(needReload: () -> Unit) {
}

internal actual fun LazyListScope.meteredNetworkSetting(needReload: () -> Unit) {
}

internal actual fun LazyListScope.androidRouteOptions(needReload: () -> Unit) {
}

internal actual fun LazyListScope.androidMiscOptions(needReload: () -> Unit) {
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