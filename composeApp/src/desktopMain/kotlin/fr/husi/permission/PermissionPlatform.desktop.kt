package fr.husi.permission

import androidx.compose.runtime.Composable

@Composable
actual fun ProvidePermissionPlatform(content: @Composable () -> Unit) = content()
