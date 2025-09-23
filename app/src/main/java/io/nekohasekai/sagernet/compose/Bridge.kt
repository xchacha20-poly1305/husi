package io.nekohasekai.sagernet.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource

@Composable
fun FromDrawable(@DrawableRes id: Int) {
    Image(
        painter = painterResource(id = id),
        contentDescription = "icon",
    )
}