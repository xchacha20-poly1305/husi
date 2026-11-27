package fr.husi.ui

import androidx.compose.runtime.Composable
import fr.husi.ktx.blankAsNull
import fr.husi.remote.RemoteState
import fr.husi.resources.Res
import fr.husi.resources.metacubex
import fr.husi.resources.sing_box_icon
import org.koin.compose.koinInject

@Composable
internal fun RemoteAboutBinding() {
    val remote = koinInject<RemoteState>()
    val navigator = LocalNavigator.current
    val version = remote.version.blankAsNull()
    val boxVersionIcon = if (remote.isClash && version?.contains("sing-box", ignoreCase = true) == false) {
        Res.drawable.metacubex
    } else {
        Res.drawable.sing_box_icon
    }
    AboutScreenContent(
        onBackPress = { navigator.popBackStack() },
        onNavigateToLibraries = { navigator.navigateTo(RemoteRoute.Libraries) },
        boxVersion = version,
        boxVersionIcon = boxVersionIcon,
    )
}
