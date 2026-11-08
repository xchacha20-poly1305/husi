package fr.husi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import fr.husi.database.DataStore
import fr.husi.database.preference.getBlocking

@Composable
internal fun DesktopResourceEnvironmentFix(content: @Composable () -> Unit) {
    installAppLanguageEnvironment { DataStore.appLanguage.getBlocking() }

    val appLanguageTag by DataStore.appLanguage.flow()
        .collectAsState("")

    ProvideAppLanguageEnvironment(appLanguageTag, content)
}
