package io.nekohasekai.sagernet.ui

import android.content.Context
import android.support.annotation.StringRes

sealed interface StringOrRes {
    data class Direct(val value: String) : StringOrRes
    data class Res(@param:StringRes val id: Int) : StringOrRes
    class ResWithParams(@param:StringRes val id: Int, vararg val params: Any) : StringOrRes
}

fun Context.getStringOrRes(str: StringOrRes): String = when (str) {
    is StringOrRes.Direct -> str.value
    is StringOrRes.Res -> getString(str.id)
    is StringOrRes.ResWithParams -> getString(str.id, *str.params)
}