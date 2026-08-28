package fr.husi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import fr.husi.repository.DesktopRepository
import fr.husi.repository.Repository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

private class PreviewDesktopRepository : DesktopRepository(createPreviewRoot()) {
    override val preferenceStoreDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

    override fun createConfigurationDataStore(scope: CoroutineScope): DataStore<Preferences> =
        previewConfigurationDataStore(dataDir, scope)

    override fun startService() = Unit
    override fun reloadService() = Unit
    override fun stopService() = Unit
}

@Composable
internal actual fun previewRepository(): Repository = remember { PreviewDesktopRepository() }
