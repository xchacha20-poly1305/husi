package fr.husi.ui

import androidx.compose.runtime.Composable
import fr.husi.ktx.blankAsNull
import fr.husi.remote.RemoteState
import org.koin.compose.koinInject

@Composable
internal fun RemoteAboutBinding() {
    val remote = koinInject<RemoteState>()
    val navigator = LocalNavigator.current
    AboutScreenContent(
        onBackPress = { navigator.popBackStack() },
        onNavigateToLibraries = { navigator.navigateTo(RemoteRoute.Libraries) },
        boxVersion = remote.version.blankAsNull(),
    )
}
