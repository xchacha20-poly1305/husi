package fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import fr.husi.compose.IconMaskColors
import fr.husi.compose.ListPreference
import fr.husi.compose.MaskedIcon
import fr.husi.compose.SwitchPreference
import fr.husi.compose.ThemeColorPreference
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.auto
import fr.husi.resources.disable
import fr.husi.resources.enable
import fr.husi.resources.follow_system
import fr.husi.resources.language
import fr.husi.resources.language_system_default
import fr.husi.resources.log_terminal_mode
import fr.husi.resources.log_terminal_mode_sum
import fr.husi.resources.night_mode
import fr.husi.resources.shutter_speed
import fr.husi.resources.speed_interval
import fr.husi.resources.terminal
import fr.husi.resources.theme
import fr.husi.resources.translate
import fr.husi.resources.wb_sunny
import fr.husi.ui.AppLanguage
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppearanceSettingsGroup(
    appTheme: Int,
    onAppThemeChange: (Int) -> Unit,
    nightTheme: Int,
    onNightThemeChange: (Int) -> Unit,
    appLanguage: String,
    onAppLanguageChange: (String) -> Unit,
    extraColors: List<Color> = emptyList(),
    nightModes: List<Int> = listOf(0, 1, 2, 3),
) {
    ThemeColorPreference(
        value = appTheme,
        onValueChange = onAppThemeChange,
        title = { Text(stringResource(Res.string.theme)) },
        extraColors = extraColors,
    )
    val nightLabels = mapOf(
        0 to stringResource(Res.string.follow_system),
        1 to stringResource(Res.string.enable),
        2 to stringResource(Res.string.disable),
        3 to stringResource(Res.string.auto),
    )
    ListPreference(
        value = nightTheme,
        onValueChange = onNightThemeChange,
        values = nightModes,
        title = { Text(stringResource(Res.string.night_mode)) },
        icon = { MaskedIcon(Res.drawable.wb_sunny, color = IconMaskColors.IconLightOrange) },
        summary = { Text(nightLabels[nightTheme] ?: nightLabels.getValue(0)) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(nightLabels.getValue(it)) },
    )
    val languageValues = AppLanguage.entries.map { it.tag }
    val selectedLanguage = appLanguage.takeIf { it in languageValues }.orEmpty()
    val systemLanguage = stringResource(Res.string.language_system_default)
    fun languageName(tag: String) = AppLanguage.fromTag(tag)?.displayName ?: systemLanguage
    ListPreference(
        value = selectedLanguage,
        onValueChange = onAppLanguageChange,
        values = languageValues,
        title = { Text(stringResource(Res.string.language)) },
        icon = { MaskedIcon(Res.drawable.translate, color = IconMaskColors.IconLavender) },
        summary = { Text(languageName(selectedLanguage)) },
        type = ListPreferenceType.ALERT_DIALOG,
        valueToText = { AnnotatedString(languageName(it)) },
    )
}

@Composable
internal fun SpeedIntervalPreference(value: Int, onValueChange: (Int) -> Unit) {
    val disabled = stringResource(Res.string.disable)
    fun intervalText(interval: Int) = when (interval) {
        0 -> disabled
        500 -> "500ms"
        3000 -> "3s"
        10000 -> "10s"
        else -> "1s"
    }
    ListPreference(
        value = value,
        onValueChange = onValueChange,
        values = listOf(0, 500, 1000, 3000, 10000),
        title = { Text(stringResource(Res.string.speed_interval)) },
        icon = { MaskedIcon(Res.drawable.shutter_speed, color = IconMaskColors.IconLightPink) },
        summary = { Text(intervalText(value)) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(intervalText(it)) },
    )
}

@Composable
internal fun LogTerminalModePreference(value: Boolean, onValueChange: (Boolean) -> Unit) {
    SwitchPreference(
        value = value,
        onValueChange = onValueChange,
        title = { Text(stringResource(Res.string.log_terminal_mode)) },
        icon = { MaskedIcon(Res.drawable.terminal, color = IconMaskColors.IconCyan) },
        summary = { Text(stringResource(Res.string.log_terminal_mode_sum)) },
    )
}
