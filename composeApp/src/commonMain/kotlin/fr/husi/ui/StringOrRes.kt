package fr.husi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import fr.husi.repository.resolveRepository
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Immutable
sealed interface StringOrRes {
    data class Direct(val value: String) : StringOrRes
    data class Res(val resource: StringResource) : StringOrRes
    class ResWithParams(val resource: StringResource, vararg val params: Any) : StringOrRes
    class PluralsRes(
        val resource: PluralStringResource,
        val quantity: Int,
        vararg val params: Any,
    ) : StringOrRes

    data class Compound(val parts: List<StringOrRes>, val separator: String = "\n\n") : StringOrRes
}

@Composable
fun stringOrRes(str: StringOrRes): String = when (str) {
    is StringOrRes.Direct -> str.value
    is StringOrRes.Res -> stringResource(str.resource)
    is StringOrRes.ResWithParams -> stringResource(str.resource, *str.params)
    is StringOrRes.PluralsRes -> pluralStringResource(str.resource, str.quantity, *str.params)
    is StringOrRes.Compound -> {
        val parts = ArrayList<String>(str.parts.size)
        for (part in str.parts) {
            parts += stringOrRes(part)
        }
        parts.joinToString(str.separator)
    }
}

suspend fun getStringOrRes(str: StringOrRes): String = when (str) {
    is StringOrRes.Direct -> str.value
    is StringOrRes.Res -> resolveRepository().getString(str.resource)
    is StringOrRes.ResWithParams -> resolveRepository().getString(str.resource, *str.params)
    is StringOrRes.PluralsRes -> resolveRepository().getPluralString(str.resource, str.quantity, *str.params)
    is StringOrRes.Compound -> {
        val parts = ArrayList<String>(str.parts.size)
        for (part in str.parts) {
            parts += getStringOrRes(part)
        }
        parts.joinToString(str.separator)
    }
}
