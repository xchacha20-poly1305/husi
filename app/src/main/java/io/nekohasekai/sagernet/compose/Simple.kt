package io.nekohasekai.sagernet.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.nekohasekai.sagernet.R

@Composable
fun SimpleIconButton(
    imageVector: ImageVector,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
        )
    }
}

@Composable
fun TextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopAppBar(
    @StringRes title: Int,
    navigationIcon: ImageVector,
    navigationDescription: String? = null,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onNavigationClick: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(title)) },
        navigationIcon = {
            SimpleIconButton(
                imageVector = navigationIcon,
                contentDescription = navigationDescription,
                onClick = onNavigationClick,
            )
        },
        windowInsets = windowInsets,
        scrollBehavior = scrollBehavior,
    )
}

@Preview
@Composable
private fun PreviewSimpleTopAppBar() {
    SimpleTopAppBar(R.string.app_name, Icons.Filled.Menu) {}
}