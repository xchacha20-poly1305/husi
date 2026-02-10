package fr.husi.compose.theme

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.AndroidUiModes
import androidx.core.content.ContextCompat
import fr.husi.database.DataStore

fun Resources.isDarkMode(mode: Int) = when (mode) {
    1 -> true
    2 -> false
    else -> (configuration.uiMode and AndroidUiModes.UI_MODE_NIGHT_MASK) == AndroidUiModes.UI_MODE_NIGHT_YES
}

@ColorInt
fun Context.getPrimaryColor(): Int {
    val isDark = resources.isDarkMode(DataStore.nightTheme)
    val theme = DataStore.appTheme
    if (theme == DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return ContextCompat.getColor(
            this,
            if (isDark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600,
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
