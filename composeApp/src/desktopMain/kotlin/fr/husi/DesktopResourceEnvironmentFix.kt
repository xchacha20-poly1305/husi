@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@file:OptIn(ExperimentalResourceApi::class, InternalResourceApi::class)

package fr.husi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import fr.husi.database.DataStore
import org.jetbrains.compose.resources.ComposeEnvironment
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LanguageQualifier
import org.jetbrains.compose.resources.LocalComposeEnvironment
import org.jetbrains.compose.resources.RegionQualifier
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getSystemEnvironment
import java.util.Locale

// Inspired by https://youtrack.jetbrains.com/issue/CMP-6614/iOS-Localization-strings-for-language-qualifiers-that-are-not-the-same-between-platforms-appear-not-translated#focus=Comments-27-10849123.0-0

private fun fixResourceEnvironment() {
    org.jetbrains.compose.resources.getResourceEnvironment = ::desktopResourceEnvironment
}

@Composable
internal fun DesktopResourceEnvironmentFix(content: @Composable () -> Unit) {
    fixResourceEnvironment()

    val default = LocalComposeEnvironment.current
    val appLanguageTag by DataStore.appLanguage.flow()
        .collectAsState("")

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
    ) {
        content()
    }
}

private fun desktopResourceEnvironment(): ResourceEnvironment {
    val environment = getSystemEnvironment()
    return mapResourceEnvironment(environment, DataStore.appLanguage.getBlocking())
}

private fun mapResourceEnvironment(
    environment: ResourceEnvironment,
    appLanguageTag: String?,
): ResourceEnvironment {
    val languageTag = appLanguageTag.orEmpty().trim()
    if (languageTag.isEmpty()) return environment

    val locale = Locale.forLanguageTag(languageTag)
    val language = locale.language
    if (language.isEmpty()) return environment

    val region = locale.country.ifEmpty {
        when (language) {
            "zh" if locale.script.equals("Hans", ignoreCase = true) -> "CN"
            "zh" if locale.script.equals("Hant", ignoreCase = true) -> "TW"
            else -> ""
        }
    }

    return ResourceEnvironment(
        language = LanguageQualifier(language),
        region = RegionQualifier(region),
        theme = environment.theme,
        density = environment.density,
    )
}
