package fr.husi.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.Key
import fr.husi.database.DataStore
import fr.husi.resources.*
import org.jetbrains.compose.resources.StringResource

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

val themes = listOf(
    Color(0xFFF44336),
    Color(0xFFFB7299),
    Color(0xFFE91E63),
    Color(0xFF9C27B0),
    Color(0xFF673AB7),
    Color(0xFF3F51B5),
    Color(0xFF2196F3),
    Color(0xFF03A9F4),
    Color(0xFF00BCD4),
    Color(0xFF009688),
    Color(0xFF4CAF50),
    Color(0xFF8BC34A),
    Color(0xFFCDDC39),
    Color(0xFFFFEB3B),
    Color(0xFFFFC107),
    Color(0xFFFF9800),
    Color(0xFFFF5722),
    Color(0xFF795548),
    Color(0xFF9E9E9E),
    Color(0xFF607D8B),
    Color(0xFF212121),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val initialNightTheme = remember { DataStore.nightTheme }
    val initialAppTheme = remember { DataStore.appTheme }
    val nightModeValue by DataStore.configurationStore
        .intFlow(Key.NIGHT_THEME, initialNightTheme)
        .collectAsStateWithLifecycle(initialNightTheme)
    val systemDarkMode = isSystemInDarkTheme()
    val isDarkMode = remember(nightModeValue, systemDarkMode) {
        when (nightModeValue) {
            1 -> true
            2 -> false
            else -> systemDarkMode
        }
    }
    val appTheme by DataStore.configurationStore
        .intFlow(Key.APP_THEME, initialAppTheme)
        .collectAsStateWithLifecycle(initialAppTheme)
    val dynamicScheme = if (appTheme == DYNAMIC) rememberDynamicColorScheme(isDarkMode) else null
    val colorScheme = remember(appTheme, isDarkMode, dynamicScheme) {
        when (appTheme) {
            RED -> if (isDarkMode) Red.darkScheme else Red.lightScheme
            PINK_SSR -> if (isDarkMode) PinkSSR.darkScheme else PinkSSR.lightScheme
            PINK -> if (isDarkMode) Pink.darkScheme else Pink.lightScheme
            PURPLE -> if (isDarkMode) Purple.darkScheme else Purple.lightScheme
            DEEP_PURPLE -> if (isDarkMode) DeepPurple.darkScheme else DeepPurple.lightScheme
            INDIGO -> if (isDarkMode) Indigo.darkScheme else Indigo.lightScheme
            BLUE -> if (isDarkMode) Blue.darkScheme else Blue.lightScheme
            LIGHT_BLUE -> if (isDarkMode) LightBlue.darkScheme else LightBlue.lightScheme
            CYAN -> if (isDarkMode) Cyan.darkScheme else Cyan.lightScheme
            TEAL -> if (isDarkMode) Teal.darkScheme else Teal.lightScheme
            GREEN -> if (isDarkMode) Green.darkScheme else Green.lightScheme
            LIGHT_GREEN -> if (isDarkMode) LightGreen.darkScheme else LightGreen.lightScheme
            LIME -> if (isDarkMode) Lime.darkScheme else Lime.lightScheme
            YELLOW -> if (isDarkMode) Yellow.darkScheme else Yellow.lightScheme
            AMBER -> if (isDarkMode) Amber.darkScheme else Amber.lightScheme
            ORANGE -> if (isDarkMode) Orange.darkScheme else Orange.lightScheme
            DEEP_ORANGE -> if (isDarkMode) DeepOrange.darkScheme else DeepOrange.lightScheme
            BROWN -> if (isDarkMode) Brown.darkScheme else Brown.lightScheme
            GREY -> if (isDarkMode) Grey.darkScheme else Grey.lightScheme
            BLUE_GREY -> if (isDarkMode) BlueGrey.darkScheme else BlueGrey.lightScheme
            BLACK -> if (isDarkMode) Black.darkScheme else Black.lightScheme
            DYNAMIC -> dynamicScheme ?: if (isDarkMode) Red.darkScheme else Red.lightScheme
            else -> dynamicScheme ?: if (isDarkMode) Red.darkScheme else Red.lightScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

fun themeString(theme: Int): StringResource = when (theme) {
    RED -> Res.string.themes_red
    PINK_SSR -> Res.string.themes_pink_ssr
    PINK -> Res.string.themes_pink
    PURPLE -> Res.string.themes_purple
    DEEP_PURPLE -> Res.string.themes_deep_purple
    INDIGO -> Res.string.themes_indigo
    BLUE -> Res.string.themes_blue
    LIGHT_BLUE -> Res.string.themes_light_blue
    CYAN -> Res.string.themes_cyan
    TEAL -> Res.string.themes_teal
    GREEN -> Res.string.themes_green
    LIGHT_GREEN -> Res.string.themes_light_green
    LIME -> Res.string.themes_lime
    YELLOW -> Res.string.themes_yellow
    AMBER -> Res.string.themes_amber
    ORANGE -> Res.string.themes_orange
    DEEP_ORANGE -> Res.string.themes_deep_orange
    BROWN -> Res.string.themes_brown
    GREY -> Res.string.themes_grey
    BLUE_GREY -> Res.string.themes_blue_grey
    BLACK -> Res.string.themes_black
    DYNAMIC -> Res.string.themes_dynamic
    else -> error("Unknown theme $theme")
}
