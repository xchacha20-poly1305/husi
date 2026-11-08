package fr.husi.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.fastCoerceIn
import fr.husi.compose.theme.LocalAppDarkMode

private fun Color.dim(factor: Float): Color {
    return Color(
        (red * factor).fastCoerceIn(0f, 1f),
        (green * factor).fastCoerceIn(0f, 1f),
        (blue * factor).fastCoerceIn(0f, 1f),
        alpha,
    )
}

@Composable
fun colorForUrlTestDelay(urlTestDelay: Int): Color {
    val isDarkMode = LocalAppDarkMode.current
    val base = when (urlTestDelay) {
        in Short.MIN_VALUE..0 -> Color.Gray
        in 1..800 -> Color.Green
        in 801..1500 -> Color(0xFFFFA500) // Orange
        else -> Color.Red
    }

    return if (isDarkMode) {
        base.dim(0.7f)
    } else {
        base
    }
}
