package fr.husi.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.database.preference.PreferenceProxy

@Composable
fun <T> PreferenceProxy<T>.collectAsStateWithLifecycle(): State<T> =
    flow().collectAsStateWithLifecycle(initialValue = defaultValue())
