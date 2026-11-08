package fr.husi.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.koin.compose.koinInject
import org.koin.mp.KoinPlatformTools

internal interface PlatformThemeApi {
    @Composable
    fun ApplyTheme(
        colorScheme: ColorScheme,
        isDarkMode: Boolean,
        content: @Composable () -> Unit,
    )
}

private object MaterialPlatformThemeApi : PlatformThemeApi {
    @Composable
    override fun ApplyTheme(
        colorScheme: ColorScheme,
        isDarkMode: Boolean,
        content: @Composable () -> Unit,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

internal fun standardPlatformThemeApi(): PlatformThemeApi = MaterialPlatformThemeApi

@Composable
internal fun currentPlatformThemeApi(): PlatformThemeApi {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) return MaterialPlatformThemeApi
    return koinInject()
}
