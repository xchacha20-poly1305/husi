package fr.husi.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.husi.ktx.emptyAsNull
import kotlinx.coroutines.flow.Flow
import java.util.Locale

internal expect fun LazyListScope.autoConnect()

internal interface AppLanguageController {
    var value: String
    val flow: Flow<String>
}

internal enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ARABIC("ar"),
    ENGLISH("en-US"),
    SPANISH("es"),
    PERSIAN("fa"),
    RUSSIAN("ru"),
    CHINESE_SIMPLIFIED("zh-Hans-CN"),
    CHINESE_TRADITIONAL_TW("zh-Hant-TW"),
    CHINESE_TRADITIONAL_HK("zh-Hant-HK");

    val nativeName: String?
        get() = tag.emptyAsNull()?.let {
            val locale = Locale.forLanguageTag(it)
            locale.getDisplayLanguage(locale)
        }

    companion object {
        private val tagMap = entries.associateBy { it.tag }
        fun fromTag(tag: String): AppLanguage? = tagMap[tag]
    }
}

@Composable
internal expect fun rememberApplyNightMode(): (Int) -> Unit

internal expect fun LazyListScope.platformGeneralOptions(needReload: () -> Unit)

internal expect fun LazyListScope.meteredNetworkSetting(needReload: () -> Unit)

internal expect fun LazyListScope.platformRouteOptions(
    needReload: () -> Unit,
    isVpnMode: Boolean,
)

internal expect fun LazyListScope.platformMiscOptions(needReload: () -> Unit)

@Composable
internal expect fun rememberThemeExtraColors(): List<Color>

@Composable
internal expect fun rememberAppLanguageController(defaultTag: String): AppLanguageController

internal expect fun LazyListScope.disableProcessText()

internal expect fun LazyListScope.httpProxyBypass(enabled: Boolean, needReload: () -> Unit)
