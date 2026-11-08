package fr.husi.repository

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString as getComposePluralString
import org.jetbrains.compose.resources.getString as getComposeString

interface Repository {
    suspend fun getString(resource: StringResource): String = getComposeString(resource)

    suspend fun getString(resource: StringResource, vararg formatArgs: Any): String =
        getComposeString(resource, *formatArgs)

    suspend fun getPluralString(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ): String = getComposePluralString(resource, quantity, *formatArgs)
}
