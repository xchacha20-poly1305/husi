package io.nekohasekai.sagernet.compose.theme

import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.utils.Theme

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val resources = LocalResources.current
    val nightModeFlow by DataStore.configurationStore
        .intFlow(Key.NIGHT_THEME)
        .collectAsStateWithLifecycle(0)
    val isDarkMode = remember(nightModeFlow, resources.configuration) {
        when (nightModeFlow) {
            1 -> true
            2 -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }
    val appTheme by DataStore.configurationStore
        .intFlow(Key.APP_THEME)
        .collectAsStateWithLifecycle(Theme.DEFAULT)
    when (appTheme) {
        Theme.RED -> Red.Theme(isDarkMode, content)
        Theme.PINK_SSR -> PinkSSR.Theme(isDarkMode, content)
        Theme.PINK -> Pink.Theme(isDarkMode, content)
        Theme.PURPLE -> Purple.Theme(isDarkMode, content)
        Theme.DEEP_PURPLE -> DeepPurple.Theme(isDarkMode, content)
        Theme.INDIGO -> Indigo.Theme(isDarkMode, content)
        Theme.BLUE -> Blue.Theme(isDarkMode, content)
        Theme.LIGHT_BLUE -> LightBlue.Theme(isDarkMode, content)
        Theme.CYAN -> Cyan.Theme(isDarkMode, content)
        Theme.TEAL -> Teal.Theme(isDarkMode, content)
        Theme.GREEN -> Green.Theme(isDarkMode, content)
        Theme.LIGHT_GREEN -> LightGreen.Theme(isDarkMode, content)
        Theme.LIME -> Lime.Theme(isDarkMode, content)
        Theme.YELLOW -> Yellow.Theme(isDarkMode, content)
        Theme.AMBER -> Amber.Theme(isDarkMode, content)
        Theme.ORANGE -> Orange.Theme(isDarkMode, content)
        Theme.DEEP_ORANGE -> DeepOrange.Theme(isDarkMode, content)
        Theme.BROWN -> Brown.Theme(isDarkMode, content)
        Theme.GREY -> Grey.Theme(isDarkMode, content)
        Theme.BLUE_GREY -> BlueGrey.Theme(isDarkMode, content)
        Theme.BLACK -> Black.Theme(isDarkMode, content)

        Theme.DYNAMIC -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MaterialTheme(
                colorScheme = if (isDarkMode) {
                    dynamicDarkColorScheme(LocalContext.current)
                } else {
                    dynamicLightColorScheme(LocalContext.current)
                },
                content = content,
            )
        } else {
            Red.Theme(isDarkMode, content)
        }

        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MaterialTheme(
                colorScheme = if (isDarkMode) {
                    dynamicDarkColorScheme(LocalContext.current)
                } else {
                    dynamicLightColorScheme(LocalContext.current)
                },
                content = content,
            )
        } else {
            Red.Theme(isDarkMode, content)
        }
    }
}
