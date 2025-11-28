package io.nekohasekai.sagernet.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

@Immutable
sealed interface StringOrRes {
    data class Direct(val value: String) : StringOrRes
    data class Res(@param:StringRes val id: Int) : StringOrRes
    class ResWithParams(@param:StringRes val id: Int, vararg val params: Any) : StringOrRes
    class PluralsRes(
        @param:androidx.annotation.PluralsRes val id: Int,
        val quantity: Int,
        vararg val params: Any,
    ) : StringOrRes
    data class Compound(val parts: List<StringOrRes>, val separator: String = "\n\n") : StringOrRes
}

fun Context.getStringOrRes(str: StringOrRes): String = when (str) {
    is StringOrRes.Direct -> str.value
    is StringOrRes.Res -> getString(str.id)
    is StringOrRes.ResWithParams -> getString(str.id, *str.params)
    is StringOrRes.PluralsRes -> resources.getQuantityString(str.id, str.quantity, *str.params)
    is StringOrRes.Compound -> str.parts.joinToString(str.separator) { getStringOrRes(it) }
}

@Composable
@ReadOnlyComposable
fun stringResource(str: StringOrRes): String = when (str) {
    is StringOrRes.Direct -> str.value
    is StringOrRes.Res -> stringResource(str.id)
    is StringOrRes.ResWithParams -> stringResource(str.id, *str.params)
    is StringOrRes.PluralsRes -> pluralStringResource(str.id, str.quantity, *str.params)
    is StringOrRes.Compound -> {
        @Suppress("SimplifiableCallChain") // joinToString is not inline
        str.parts.map { stringResource(it) }.joinToString(str.separator)
    }
}