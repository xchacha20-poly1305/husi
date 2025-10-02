package io.nekohasekai.sagernet.ui

import android.content.Context
import android.support.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

sealed interface StringOrRes {
    data class Direct(val value: String) : StringOrRes
    data class Res(@param:StringRes val id: Int) : StringOrRes
    class ResWithParams(@param:StringRes val id: Int, vararg val params: Any) : StringOrRes
    class PluralsRes(
        @param:androidx.annotation.PluralsRes val id: Int,
        val quantity: Int,
        vararg val params: Any,
    ) : StringOrRes
}

fun Context.getStringOrRes(str: StringOrRes): String = when (str) {
    is StringOrRes.Direct -> str.value
    is StringOrRes.Res -> getString(str.id)
    is StringOrRes.ResWithParams -> getString(str.id, *str.params)
    is StringOrRes.PluralsRes -> resources.getQuantityString(str.id, str.quantity, *str.params)
}

@Composable
@ReadOnlyComposable
fun stringResource(str: StringOrRes): String = when (str) {
    is StringOrRes.Direct -> str.value
    is StringOrRes.Res -> stringResource(str.id)
    is StringOrRes.ResWithParams -> stringResource(str.id, *str.params)
    is StringOrRes.PluralsRes -> pluralStringResource(str.id, str.quantity, *str.params)
}