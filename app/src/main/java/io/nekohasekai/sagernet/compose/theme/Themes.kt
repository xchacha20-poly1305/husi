package io.nekohasekai.sagernet.compose.theme

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.annotation.ColorInt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.database.DataStore

const val RED = 1
const val PINK_SSR = 2
const val PINK = 3
const val PURPLE = 4
const val DEEP_PURPLE = 5
const val INDIGO = 6
const val BLUE = 7
const val LIGHT_BLUE = 8
const val CYAN = 9
const val TEAL = 10
const val GREEN = 11
const val LIGHT_GREEN = 12
const val LIME = 13
const val YELLOW = 14
const val AMBER = 15
const val ORANGE = 16
const val DEEP_ORANGE = 17
const val BROWN = 18
const val GREY = 19
const val BLUE_GREY = 20
const val BLACK = 21
const val DYNAMIC = 22

val DEFAULT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    DYNAMIC
} else {
    RED
}

fun Resources.isDarkMode(mode: Int) = when (mode) {
    1 -> true
    2 -> false
    else -> (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val resources = LocalResources.current
    val nightModeValue by DataStore.configurationStore
        .intFlow(Key.NIGHT_THEME)
        .collectAsStateWithLifecycle(0)
    val isDarkMode = remember(nightModeValue, resources.configuration) {
        resources.isDarkMode(nightModeValue)
    }
    val appTheme by DataStore.configurationStore
        .intFlow(Key.APP_THEME)
        .collectAsStateWithLifecycle(DEFAULT)
    when (appTheme) {
        RED -> Red.Theme(isDarkMode, content)
        PINK_SSR -> PinkSSR.Theme(isDarkMode, content)
        PINK -> Pink.Theme(isDarkMode, content)
        PURPLE -> Purple.Theme(isDarkMode, content)
        DEEP_PURPLE -> DeepPurple.Theme(isDarkMode, content)
        INDIGO -> Indigo.Theme(isDarkMode, content)
        BLUE -> Blue.Theme(isDarkMode, content)
        LIGHT_BLUE -> LightBlue.Theme(isDarkMode, content)
        CYAN -> Cyan.Theme(isDarkMode, content)
        TEAL -> Teal.Theme(isDarkMode, content)
        GREEN -> Green.Theme(isDarkMode, content)
        LIGHT_GREEN -> LightGreen.Theme(isDarkMode, content)
        LIME -> Lime.Theme(isDarkMode, content)
        YELLOW -> Yellow.Theme(isDarkMode, content)
        AMBER -> Amber.Theme(isDarkMode, content)
        ORANGE -> Orange.Theme(isDarkMode, content)
        DEEP_ORANGE -> DeepOrange.Theme(isDarkMode, content)
        BROWN -> Brown.Theme(isDarkMode, content)
        GREY -> Grey.Theme(isDarkMode, content)
        BLUE_GREY -> BlueGrey.Theme(isDarkMode, content)
        BLACK -> Black.Theme(isDarkMode, content)

        DYNAMIC -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

@ColorInt
fun Context.getPrimaryColor(): Int {
    val isDark = resources.isDarkMode(DataStore.nightTheme)
    val theme = DataStore.appTheme
    if (theme == DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return ContextCompat.getColor(
            this,
            if (isDark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
        )
    }
    val color = when (theme) {
        RED -> if (isDark) Red.primaryDark else Red.primaryLight
        PINK_SSR -> if (isDark) PinkSSR.primaryDark else PinkSSR.primaryLight
        PINK -> if (isDark) Pink.primaryDark else Pink.primaryLight
        PURPLE -> if (isDark) Purple.primaryDark else Purple.primaryLight
        DEEP_PURPLE -> if (isDark) DeepPurple.primaryDark else DeepPurple.primaryLight
        INDIGO -> if (isDark) Indigo.primaryDark else Indigo.primaryLight
        BLUE -> if (isDark) Blue.primaryDark else Blue.primaryLight
        LIGHT_BLUE -> if (isDark) LightBlue.primaryDark else LightBlue.primaryLight
        CYAN -> if (isDark) Cyan.primaryDark else Cyan.primaryLight
        TEAL -> if (isDark) Teal.primaryDark else Teal.primaryLight
        GREEN -> if (isDark) Green.primaryDark else Green.primaryLight
        LIGHT_GREEN -> if (isDark) LightGreen.primaryDark else LightGreen.primaryLight
        LIME -> if (isDark) Lime.primaryDark else Lime.primaryLight
        YELLOW -> if (isDark) Yellow.primaryDark else Yellow.primaryLight
        AMBER -> if (isDark) Amber.primaryDark else Amber.primaryLight
        ORANGE -> if (isDark) Orange.primaryDark else Orange.primaryLight
        DEEP_ORANGE -> if (isDark) DeepOrange.primaryDark else DeepOrange.primaryLight
        BROWN -> if (isDark) Brown.primaryDark else Brown.primaryLight
        GREY -> if (isDark) Grey.primaryDark else Grey.primaryLight
        BLUE_GREY -> if (isDark) BlueGrey.primaryDark else BlueGrey.primaryLight
        BLACK -> if (isDark) Black.primaryDark else Black.primaryLight
        else -> if (isDark) Red.primaryDark else Red.primaryLight
    }
    return color.toArgb()
}
