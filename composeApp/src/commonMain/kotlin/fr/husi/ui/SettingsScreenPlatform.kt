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
