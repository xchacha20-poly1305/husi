@file:Suppress("ERROR_SUPPRESSION", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@file:OptIn(InternalResourceApi::class)

package fr.husi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale
import org.jetbrains.compose.resources.ComposeEnvironment
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LanguageQualifier
import org.jetbrains.compose.resources.LocalComposeEnvironment
import org.jetbrains.compose.resources.RegionQualifier
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.ScriptQualifier
import org.jetbrains.compose.resources.getResourceEnvironment
import org.jetbrains.compose.resources.getSystemEnvironment

// Skiko platforms take the resource locale from the system only, so the in-app language choice
// is applied by replacing the resource environment.
// Inspired by https://youtrack.jetbrains.com/issue/CMP-6614/iOS-Localization-strings-for-language-qualifiers-that-are-not-the-same-between-platforms-appear-not-translated#focus=Comments-27-10849123.0-0

private var appLanguageTagProvider: () -> String = { "" }

/** Applies the selected language to non-composable resource lookups as well. */
internal fun installAppLanguageEnvironment(appLanguageTag: () -> String) {
    appLanguageTagProvider = appLanguageTag
    getResourceEnvironment = ::appResourceEnvironment
}

private fun appResourceEnvironment(): ResourceEnvironment =
    mapResourceEnvironment(getSystemEnvironment(), appLanguageTagProvider())

/** Resolves composable resources in [appLanguageTag]; an empty tag keeps the system language. */
@Composable
internal fun ProvideAppLanguageEnvironment(
    appLanguageTag: String,
    content: @Composable () -> Unit,
) {
    val default = LocalComposeEnvironment.current
    CompositionLocalProvider(
        LocalComposeEnvironment provides object : ComposeEnvironment {
            @Composable
            override fun rememberEnvironment(): ResourceEnvironment {
                val environment = default.rememberEnvironment()
                return remember(environment, appLanguageTag) {
                    mapResourceEnvironment(environment, appLanguageTag)
                }
            }
        },
        content = content,
    )
}

private fun mapResourceEnvironment(
    environment: ResourceEnvironment,
    appLanguageTag: String,
): ResourceEnvironment {
    val languageTag = appLanguageTag.trim()
    if (languageTag.isEmpty()) return environment

    val locale = Locale(languageTag)
    val language = locale.language
    if (language.isEmpty()) return environment

    val region = locale.region.ifEmpty {
        when (language) {
            "zh" if locale.script.equals("Hans", ignoreCase = true) -> "CN"
            "zh" if locale.script.equals("Hant", ignoreCase = true) -> "TW"
            else -> ""
        }
    }

    return ResourceEnvironment(
        language = LanguageQualifier(language),
        script = ScriptQualifier(locale.script),
        region = RegionQualifier(region),
        theme = environment.theme,
        density = environment.density,
    )
}
