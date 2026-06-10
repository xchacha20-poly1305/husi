@file:OptIn(ExperimentalHazeApi::class, ExperimentalLayoutApi::class)

package fr.husi.compose

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.liquidglass.SurfaceProfile
import dev.chrisbanes.haze.liquidglass.liquidGlassEffect
import dev.chrisbanes.haze.rememberHazeState
import fr.husi.Key
import fr.husi.bg.ServiceStatus
import fr.husi.compose.material3.Text
import fr.husi.database.DataStore
import fr.husi.ktx.readableUrlTestError
import fr.husi.libcore.Libcore
import fr.husi.resources.Res
import fr.husi.resources.connection_test_available
import fr.husi.resources.connection_test_available_http
import fr.husi.resources.connection_test_error
import fr.husi.resources.connection_test_testing
import fr.husi.resources.speed
import fr.husi.resources.vpn_connected
import fr.husi.ui.MainViewModel
import fr.husi.ui.URLTestStatus
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource

@Composable
fun rememberStatsBarHazeState(): HazeState = rememberHazeState()

@Composable
fun Modifier.statsBarHazeSource(hazeState: HazeState): Modifier {
    return hazeSource(state = hazeState)
        .background(MaterialTheme.colorScheme.background)
}

@Composable
fun StatsBar(
    modifier: Modifier = Modifier,
    status: ServiceStatus,
    visible: Boolean = true,
    mainViewModel: MainViewModel,
    hazeState: HazeState,
) {
    val urlTestStatus by mainViewModel.urlTestStatus.collectAsStateWithLifecycle()
    val isHTTPS by remember {
        DataStore.configurationStore
            .stringFlow(Key.CONNECTION_TEST_URL)
            .map { it.startsWith("https://") }
    }.collectAsStateWithLifecycle(false)

    var totalHeight by remember { mutableIntStateOf(0) }
    val offsetY by animateIntAsState(
        targetValue = if (visible) 0 else totalHeight,
        label = "statsBarOffset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { totalHeight = it.height }
            .graphicsLayer { translationY = offsetY.toFloat() }
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = navigationBarsAlwaysInsets()
                    .asPaddingValues()
                    .calculateBottomPadding() + 8.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val statsBarShape = RoundedCornerShape(28.dp)
        val statsBarTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.24f)
        val statsBarFallbackBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)
        val statsBarBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .then(
                    if (visible) {
                        Modifier.clickable { mainViewModel.urlTest() }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(statsBarShape)
                    .hazeEffect(state = hazeState) {
                        inputScale = HazeInputScale.Auto
                        if (isStatsBarLiquidGlassRuntimeSupported()) {
                            liquidGlassEffect {
                                shape = statsBarShape
                                tint = statsBarTint
                                refractionStrength = 0.82f
                                specularIntensity = 0.48f
                                depth = 0.56f
                                ambientResponse = 0.62f
                                edgeSoftness = 14.dp
                                blurRadius = 8.dp
                                refractionHeight = 1f
                                surfaceProfile = SurfaceProfile.Squircle
                                chromaticAberrationStrength = 0.08f
                                contentNormalBlend = 0.28f
                            }
                        } else {
                            blurEffect {
                                blurEnabled = true
                                blurRadius = 22.dp
                                noiseFactor = 0.08f
                                backgroundColor = statsBarFallbackBackground
                                colorEffects = listOf(HazeColorEffect.tint(statsBarTint))
                                fallbackTint = HazeColorEffect.tint(statsBarTint)
                            }
                        }
                    }
                    .border(
                        width = 1.dp,
                        color = statsBarBorder,
                        shape = statsBarShape,
                    ),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "▲ " + stringResource(
                        Res.string.speed,
                        Libcore.formatBytes(status.speed?.txRateProxy ?: 0L),
                    ),
                )
                Text(
                    text = "▼ " + stringResource(
                        Res.string.speed,
                        Libcore.formatBytes(status.speed?.rxRateProxy ?: 0L),
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
                val text = when (urlTestStatus) {
                    URLTestStatus.Initial -> stringResource(Res.string.vpn_connected)
                    URLTestStatus.Testing -> stringResource(Res.string.connection_test_testing)

                    is URLTestStatus.Success -> stringResource(
                        if (isHTTPS) {
                            Res.string.connection_test_available
                        } else {
                            Res.string.connection_test_available_http
                        },
                        (urlTestStatus as URLTestStatus.Success).legacy,
                    )

                    is URLTestStatus.Exception -> {
                        val exception = (urlTestStatus as URLTestStatus.Exception).exception
                        stringResource(
                            Res.string.connection_test_error,
                            readableUrlTestError(exception)?.let {
                                stringResource(it)
                            } ?: exception,
                        )
                    }
                }
                Text(text)
            }
        }
    }
}
