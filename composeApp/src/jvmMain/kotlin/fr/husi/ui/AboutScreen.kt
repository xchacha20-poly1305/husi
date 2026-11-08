package fr.husi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fr.husi.core.CoreVersionCache
import fr.husi.proto.v1.GetVersionResponse
import org.koin.compose.koinInject

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit,
    onNavigateToLibraries: () -> Unit,
    coreVersionCache: CoreVersionCache = koinInject(),
) {
    val coreVersion by produceState<GetVersionResponse?>(null, coreVersionCache) {
        value = coreVersionCache.get()
    }
    val shouldRequestBattery = rememberShouldRequestBatteryOptimizations()
    val requestIgnoreBatteryOptimizations = rememberRequestIgnoreBatteryOptimizations()
    AboutScreenContent(
        modifier = modifier,
        onBackPress = onBackPress,
        onNavigateToLibraries = onNavigateToLibraries,
        boxVersion = coreVersion?.singBoxVersion,
        buildEnvironment = coreVersion?.buildEnvironment,
        requestIgnoreBatteryOptimizations = requestIgnoreBatteryOptimizations.takeIf { shouldRequestBattery },
    )
}

@Preview
@Composable
private fun PreviewAboutScreen() {
    PreviewContainer {
        AboutScreenContent(
            onBackPress = {},
            onNavigateToLibraries = {},
        )
    }
}
