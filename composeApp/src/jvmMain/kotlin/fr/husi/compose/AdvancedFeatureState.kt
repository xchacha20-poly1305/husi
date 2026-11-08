package fr.husi.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.database.DataStore
import kotlinx.coroutines.runBlocking

@Composable
fun collectAdvancedFeatureAsStateWithLifecycle(key: String): State<String?> {
    val initial = remember(key) { runBlocking { DataStore.advancedFeature(key) } }
    val flow = remember(key) { DataStore.advancedFeatureFlow(key) }
    return flow.collectAsStateWithLifecycle(initialValue = initial)
}
