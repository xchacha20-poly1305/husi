package fr.husi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import fr.husi.compose.LocalFlagEmojiTextStyler
import fr.husi.compose.WindowsFlagEmojiText
import fr.husi.database.DataStore
import fr.husi.database.preference.getBlocking
import fr.husi.platform.PlatformInfo

@Composable
internal fun ProvideDesktopEnvironment(content: @Composable () -> Unit) {
    installAppLanguageEnvironment { DataStore.appLanguage.getBlocking() }

    val appLanguageTag by DataStore.appLanguage.flow()
        .collectAsState("")

    if (PlatformInfo.isWindows) {
        CompositionLocalProvider(LocalFlagEmojiTextStyler provides WindowsFlagEmojiText) {
            ProvideAppLanguageEnvironment(appLanguageTag, content)
        }
    } else {
        ProvideAppLanguageEnvironment(appLanguageTag, content)
    }
}
