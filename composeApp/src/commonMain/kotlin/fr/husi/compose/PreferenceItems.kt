package fr.husi.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.ListPreference as BaseListPreference
import me.zhanghai.compose.preference.MultiSelectListPreference as BaseMultiSelectListPreference
import me.zhanghai.compose.preference.Preference as BasePreference
import me.zhanghai.compose.preference.SliderPreference as BaseSliderPreference
import me.zhanghai.compose.preference.SwitchPreference as BaseSwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference as BaseTextFieldPreference
import me.zhanghai.compose.preference.TwoTargetSwitchPreference as BaseTwoTargetSwitchPreference

/**
 * [me.zhanghai.compose.preference.TextFieldPreferenceDefaults] is `internal` so we copy out.
 */
object PreferenceTextFieldDefaults {
    val TextField: @Composable (
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        onOk: () -> Unit,
    ) -> Unit = { value, onValueChange, onOk ->
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = KeyboardActions { onOk() },
            singleLine = true,
        )
    }
}

@Composable
fun Preference(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
    widgetContainer: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) = PreferenceItemSurface {
    BasePreference(
        title = title,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = summary,
        widgetContainer = widgetContainer,
        onClick = onClick,
    )
}

@Composable
fun SwitchPreference(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
) = PreferenceItemSurface {
    BaseSwitchPreference(
        value = value,
        onValueChange = onValueChange,
        title = title,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = summary,
    )
}

@Composable
fun TwoTargetSwitchPreference(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
    switchEnabled: Boolean = enabled,
    onClick: (() -> Unit)? = null,
) = PreferenceItemSurface {
    BaseTwoTargetSwitchPreference(
        value = value,
        onValueChange = onValueChange,
        title = title,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = summary,
        switchEnabled = switchEnabled,
        onClick = onClick,
    )
}

@Composable
fun <T> ListPreference(
    value: T,
    onValueChange: (T) -> Unit,
    values: List<T>,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
    type: ListPreferenceType = ListPreferenceType.ALERT_DIALOG,
    valueToText: @Composable (T) -> AnnotatedString = { AnnotatedString(it.toString()) },
) = PreferenceItemSurface {
    BaseListPreference(
        value = value,
        onValueChange = onValueChange,
        values = values,
        title = title,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = summary,
        type = type,
        valueToText = valueToText,
    )
}

@Composable
fun <T> MultiSelectListPreference(
    value: Set<T>,
    onValueChange: (Set<T>) -> Unit,
    values: List<T>,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
    valueToText: @Composable (T) -> AnnotatedString = { AnnotatedString(it.toString()) },
) = PreferenceItemSurface {
    BaseMultiSelectListPreference(
        value = value,
        onValueChange = onValueChange,
        values = values,
        title = title,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = summary,
        valueToText = valueToText,
    )
}

@Composable
fun <T> TextFieldPreference(
    value: T,
    onValueChange: (T) -> Unit,
    title: @Composable () -> Unit,
    textToValue: (String) -> T?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
    valueToText: (T) -> String = { it.toString() },
    textField: @Composable (
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        onOk: () -> Unit,
    ) -> Unit = PreferenceTextFieldDefaults.TextField,
) = PreferenceItemSurface {
    BaseTextFieldPreference(
        value = value,
        onValueChange = onValueChange,
        title = title,
        textToValue = textToValue,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = summary,
        valueToText = valueToText,
        textField = textField,
    )
}

@Composable
fun SliderPreference(
    value: Float,
    onValueChange: (Float) -> Unit,
    sliderValue: Float,
    onSliderValueChange: (Float) -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    valueSteps: Int = 0,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
    valueText: @Composable (() -> Unit)? = null,
) = PreferenceItemSurface {
    BaseSliderPreference(
        value = value,
        onValueChange = onValueChange,
        sliderValue = sliderValue,
        onSliderValueChange = onSliderValueChange,
        title = title,
        modifier = modifier,
        valueRange = valueRange,
        valueSteps = valueSteps,
        enabled = enabled,
        icon = icon,
        summary = summary,
        valueText = valueText,
    )
}
