package fr.husi.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow

internal interface AppLanguageController {
    var value: String
    val flow: Flow<String>
}

internal enum class AppLanguage(
    val tag: String,
    val displayName: String?,
) {
    SYSTEM("", null),
    ARABIC("ar", "العربية"),
    ENGLISH("en-US", "English"),
    SPANISH("es", "Español"),
    PERSIAN("fa", "فارسی"),
    RUSSIAN("ru", "Русский"),
    CHINESE_SIMPLIFIED("zh-Hans-CN", "简体中文"),
    CHINESE_TRADITIONAL_TW("zh-Hant-TW", "繁體中文（台灣）"),
    CHINESE_TRADITIONAL_HK("zh-Hant-HK", "繁體中文（香港）");

    companion object {
        private val tagMap = entries.associateBy { it.tag }
        fun fromTag(tag: String): AppLanguage? = tagMap[tag]
    }
}

@Composable
internal expect fun rememberApplyNightMode(): (Int) -> Unit

@Composable
internal expect fun rememberThemeExtraColors(): List<Color>

@Composable
internal expect fun rememberAppLanguageController(defaultTag: String): AppLanguageController

@Composable
internal expect fun AutoConnectPreference()

/**
 * Desktop-only system-daemon rows (install / update / start-at-boot).
 * Android renders nothing; the Daemon settings page is only reachable on desktop.
 */
@Composable
internal expect fun PlatformDaemonSettingsGroup(showMessage: (String) -> Unit)

@Composable
internal expect fun PlatformGeneralOptions(needReload: () -> Unit)

@Composable
internal expect fun PlatformRouteOptions(needReload: () -> Unit, isVpnMode: Boolean)

@Composable
internal expect fun ProxyAppsPreferences(openAppManager: () -> Unit)

@Composable
internal expect fun PlatformSecurityOptions()

@Composable
internal expect fun MeteredNetworkPreference(needReload: () -> Unit)

@Composable
internal expect fun HttpProxyBypassPreference(enabled: Boolean, needReload: () -> Unit)

@Composable
internal expect fun PlatformMiscOptions(needReload: () -> Unit)

@Composable
internal expect fun DisableProcessTextPreference()

@Composable
internal expect fun HideLauncherIconPreference()
