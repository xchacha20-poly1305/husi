package fr.husi.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.theme.LocalAppDarkMode
import fr.husi.resources.Res
import fr.husi.resources.cancel
import fr.husi.resources.not_set
import fr.husi.resources.ok
import fr.husi.resources.password
import fr.husi.resources.settings
import fr.husi.resources.wifi
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

object PreferenceType {
    const val CATEGORY = 0
    const val SWITCH = 1
    const val LIST = 2
    const val TEXT_FIELD = 3
    const val MULTI_SELECT_LIST = 4
    const val COLOR_PICKER = 5
}

/**
 * Not only support icon, but also use spacer as icon if not set.
 * */
@Composable
fun PreferenceCategory(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = { Spacer(Modifier.size(24.dp)) },
    text: @Composable () -> Unit,
) {
    PreferenceCategory(
        title = {
            Row {
                icon()
                Spacer(Modifier.padding(8.dp))
                text()
            }
        },
        modifier = modifier,
    )
}

@Composable
fun PasswordPreference(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    title: @Composable () -> Unit = { Text(stringResource(Res.string.password)) },
    enabled: Boolean = true,
    icon: @Composable (() -> Unit) = {
        MaskedIcon(
            resource = Res.drawable.password,
            color = IconMaskColors.IconCoral,
        )
    },
) {
    TextFieldPreference(
        value = value,
        onValueChange = onValueChange,
        title = title,
        textToValue = { it },
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = {
            val summaryText = if (value.isEmpty()) {
                stringResource(Res.string.not_set)
            } else {
                "\u2022".repeat(value.length)
            }
            Text(text = summaryText)
        },
        valueToText = { it },
        textField = { textFieldValue, onTextFieldValueChange, onOk ->
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = onTextFieldValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                // visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onOk() },
                ),
            )
        },
    )
}

@Composable
fun <K, V> MapPreference(
    modifier: Modifier = Modifier,
    value: LinkedHashMap<K, V>,
    keys: LinkedHashSet<K>,
    onValueChange: (LinkedHashMap<K, V>) -> Unit,
    displayKey: (K) -> String = { it.toString() },
    valueToText: (V) -> String = { it.toString() },
    textToValue: (String) -> V,
    enabled: Boolean = true,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    summary: (@Composable () -> Unit)? = null,
) {
    var openDialog by remember { mutableStateOf(false) }

    Preference(
        title = title,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = summary,
        onClick = { openDialog = true },
    )

    if (!openDialog) return

    val firstKey = remember(keys) { keys.firstOrNull() }
    val focusRequester = remember { FocusRequester() }
    val textStates = remember(keys, value) {
        mutableStateMapOf<K, TextFieldValue>().apply {
            for (key in keys) {
                val text = value[key]?.let(valueToText).orEmpty()
                this[key] = TextFieldValue(text, TextRange(text.length))
            }
        }
    }
    val onOk = {
        val newMap = LinkedHashMap<K, V>()
        for (key in keys) {
            val text = textStates[key]!!.text
            newMap[key] = textToValue(text)
        }
        onValueChange(newMap)
        openDialog = false
    }

    ScrollableDialog(
        onDismissRequest = { openDialog = false },
        title = { title() },
        textPadding = PaddingValues(horizontal = 24.dp),
        text = {
            var isFirst = true
            for (key in keys) {
                val textFieldValue = textStates.getOrPut(key) {
                    val text = value[key]?.let(valueToText).orEmpty()
                    TextFieldValue(text, TextRange(text.length))
                }
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textStates[key] = it },
                    label = { Text(displayKey(key)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (isFirst) 0.dp else 12.dp)
                        .then(if (key == firstKey) Modifier.focusRequester(focusRequester) else Modifier),
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onOk() }),
                )
                isFirst = false
            }
        },
        confirmButton = {
            TextButton(stringResource(Res.string.ok), onClick = onOk)
        },
        dismissButton = {
            TextButton(stringResource(Res.string.cancel)) {
                openDialog = false
            }
        },
    )

    LaunchedEffect(focusRequester) {
        if (firstKey != null) focusRequester.requestFocus()
    }
}

@Composable
fun PreferenceDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

fun LazyListScope.preferenceGroup(
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = key) {
        ElevatedCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        ) {
            Column(content = content)
        }
    }
}

/** Inspired by Android system preference. */
object IconMaskColors {
    // network
    val IconLightBlue = Color(0xFFD6E3FF)

    // notification
    val IconLightPink = Color(0xFFFFD9E2)

    // theme/style
    val IconLightOrange = Color(0xFFFADEBC)

    // storage
    val IconLightYellow = Color(0xFFFAE2A6)

    // battery or ???
    val IconLightGreen = Color(0xFFC2EFB3)

    // not important
    val IconWarmGray = Color(0xFFEAE2D5)

    // misc
    val IconCyan = Color(0xFFC4EBF2)

    // security / alert
    val IconCoral = Color(0xFFFFDAD6)

    // misc
    val IconLavender = Color(0xFFEADDFF)
}

@Composable
private fun IconMask(
    color: Color,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val darkTheme = LocalAppDarkMode.current
    val containerColor = if (darkTheme) color.copy(alpha = 0.33f) else color.blend(Color.White, 0.5f)
    val contentColor = if (darkTheme) color else color.blend(Color.Black, 0.45f)
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(42.dp)
            .background(color = containerColor, shape = shape),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

private fun Color.blend(other: Color, fraction: Float): Color {
    val inverse = 1f - fraction
    return Color(
        red = red * inverse + other.red * fraction,
        green = green * inverse + other.green * fraction,
        blue = blue * inverse + other.blue * fraction,
        alpha = alpha,
    )
}

@Composable
fun MaskedIcon(
    resource: DrawableResource,
    color: Color = IconMaskColors.IconCyan,
    shape: Shape = CircleShape,
) {
    IconMask(
        color = color,
        shape = shape,
    ) {
        Icon(
            imageVector = vectorResource(resource),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
    }
}

object IconMaskShapes {
    @Composable
    fun risk(): Shape = MaterialShapes.SoftBurst.toShape()

    @Composable
    fun credential(): Shape = MaterialShapes.Slanted.toShape()

    @Composable
    fun route(): Shape = MaterialShapes.Triangle.toShape()
}

@Suppress("MutableCollectionMutableState")
@Preview
@Composable
private fun PreviewCustomPreference() {
    ProvidePreferenceLocals {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            PreferenceCategory(
                icon = {
                    Icon(
                        vectorResource(Res.drawable.settings),
                        contentDescription = null,
                    )
                },
                text = { Text("Account Settings") },
            )

            Spacer(Modifier.height(16.dp))

            var password by remember { mutableStateOf("") }
            PasswordPreference(
                value = password,
                onValueChange = { password = it },
                enabled = true,
            )

            Spacer(Modifier.height(16.dp))

            var mapValue by remember {
                mutableStateOf(
                    linkedMapOf(
                        "Server" to "192.168.1.1",
                        "Port" to "8080",
                    ),
                )
            }

            val keys = linkedSetOf("Server", "Port")

            MapPreference(
                value = mapValue,
                keys = keys,
                onValueChange = { mapValue = it },
                displayKey = { it },
                textToValue = { it },
                title = { Text("Connection Settings") },
                icon = {
                    Icon(
                        vectorResource(Res.drawable.wifi),
                        contentDescription = null,
                    )
                },
                summary = {
                    Text(mapValue.entries.joinToString { "${it.key}: ${it.value}" })
                },
            )
        }
    }
}
