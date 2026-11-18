package fr.husi.cli

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.types.file
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists

/**
 * [com.github.ajalt.clikt.parameters.types.file] fails when enabling `mustBeWritable` but the
 * directory does not exist. `directory` wrapped it to make the directory first if not exist.
 */
fun RawOption.directory(
    mustExist: Boolean = false,
    mustBeWritable: Boolean = false,
    mustBeReadable: Boolean = true,
    canBeSymlink: Boolean = true,
): NullableOption<PlatformFile, PlatformFile> = convert(
    metavar = { localization.pathMetavar() },
    completionCandidates = CompletionCandidates.Path,
) { path ->
    if (mustExist || mustBeWritable || mustBeReadable) {
        val directory = PlatformFile(path)
        if (!directory.exists() && runCatching { directory.createDirectories() }.isFailure) {
            fail("Directory \"$path\" does not exist and could not be created.")
        }
    }
    path
}.file(
    mustExist = true,
    canBeFile = false,
    mustBeWritable = mustBeWritable,
    mustBeReadable = mustBeReadable,
    canBeSymlink = canBeSymlink,
).convert { PlatformFile(it) }
