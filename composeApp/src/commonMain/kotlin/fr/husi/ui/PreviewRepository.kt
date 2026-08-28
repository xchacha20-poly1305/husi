package fr.husi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import fr.husi.compose.theme.AppTheme
import fr.husi.database.preference.createSimpleConfigurationDataStore
import fr.husi.di.initHusiKoin
import fr.husi.repository.Repository
import kotlinx.coroutines.CoroutineScope
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import java.io.File
import kotlin.io.path.createTempDirectory

@Suppress("NewApi")
internal fun createPreviewRoot(): File = createTempDirectory("husi-preview").toFile()

internal fun previewConfigurationDataStore(
    root: File,
    scope: CoroutineScope,
): DataStore<Preferences> = createSimpleConfigurationDataStore(
    root.resolve("configuration.preferences_pb"),
    scope,
)

@Composable
internal expect fun previewRepository(): Repository

private fun preparePreviewKoin(repository: Repository): Koin {
    GlobalContext.getOrNull()?.let { koin ->
        if (koin.getOrNull<Repository>() != null) return koin
        // A previous preview registered the context and then failed while loading
        // modules, leaving an empty graph behind. Nothing can resolve from it.
        stopKoin()
    }
    initHusiKoin(repository)
    return GlobalContext.get()
}

@Composable
internal fun PreviewContainer(content: @Composable () -> Unit) {
    val repository = previewRepository()
    remember(repository) { preparePreviewKoin(repository) }
    AppTheme(content)
}
