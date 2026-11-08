package fr.husi.ui

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
