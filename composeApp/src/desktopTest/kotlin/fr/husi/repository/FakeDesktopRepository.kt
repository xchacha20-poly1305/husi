package fr.husi.repository

import io.github.vinceglb.filekit.PlatformFile
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

class FakeDesktopRepository(
    dataDir: PlatformFile,
    instanceId: String? = null,
) : DesktopRepository(dataDir, instanceId) {

    override suspend fun getString(resource: StringResource) = resource.key
    override suspend fun getString(resource: StringResource, vararg formatArgs: Any) =
        stubResourceString(resource.key, formatArgs)

    override suspend fun getPluralString(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ) = stubResourceString("${resource.key}#$quantity", formatArgs)
}
