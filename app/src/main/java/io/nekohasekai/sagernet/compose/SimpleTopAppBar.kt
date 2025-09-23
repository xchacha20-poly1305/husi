package io.nekohasekai.sagernet.compose

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.nekohasekai.sagernet.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopAppBar(
    @StringRes title: Int,
    navigationIcon: ImageVector,
    navigationDescription: String? = null,
    onNavigationClick: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(title)) },
        navigationIcon = {
            SimpleIconButton(
                imageVector = navigationIcon,
                contentDescription = navigationDescription,
            ) {
                onNavigationClick()
            }
        },
    )
}

@Preview
@Composable
private fun PreviewSimpleTopAppBar() {
    SimpleTopAppBar(R.string.app_name, Icons.Filled.Menu) {}
}