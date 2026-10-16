package fr.husi.tailscale

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

private const val TAILDROP_DOCUMENTS_AUTHORITY_SUFFIX = ".taildrop"

private val TAILDROP_INTERNAL_SUFFIXES = listOf(TAILDROP_PARTIAL_SUFFIX, ".deleted")

internal enum class TaildropDocumentRoot(val rootId: String, val directoryName: String) {
    Receive("taildrop", TAILDROP_DIRECTORY_NAME),
    Downloads("taildrop-downloads", TAILDROP_DOWNLOAD_DIRECTORY_NAME),
    ;

    fun directory(context: Context): File = context.noBackupFilesDir.resolve(directoryName)

    fun fileDocumentId(name: String): String = "$rootId/$name"

    companion object {
        fun ofDirectory(context: Context, directory: File): TaildropDocumentRoot? =
            entries.firstOrNull { root -> root.directory(context) == directory }
    }
}

internal data class TaildropDocument(val root: TaildropDocumentRoot, val name: String?)

internal fun parseTaildropDocumentId(documentId: String): TaildropDocument? {
    val rootId = documentId.substringBefore("/")
    val root = TaildropDocumentRoot.entries.firstOrNull { it.rootId == rootId } ?: return null
    if ("/" !in documentId) return TaildropDocument(root, null)
    val name = documentId.substringAfter("/")
    if (!isTaildropPathSegment(name)) return null
    return TaildropDocument(root, name)
}

internal fun taildropDocumentsAuthority(context: Context): String =
    context.packageName + TAILDROP_DOCUMENTS_AUTHORITY_SUFFIX

internal fun isTaildropReceivedFile(file: File): Boolean =
    file.isFile && TAILDROP_INTERNAL_SUFFIXES.none { suffix -> file.name.endsWith(suffix) }

/** The document URI of [file], or null when it is not a received file directly under a root. */
internal fun taildropDocumentUri(context: Context, file: File): Uri? {
    val parent = file.parentFile ?: return null
    val root = TaildropDocumentRoot.ofDirectory(context, parent) ?: return null
    if (!isTaildropReceivedFile(file)) return null
    return DocumentsContract.buildDocumentUri(
        taildropDocumentsAuthority(context),
        root.fileDocumentId(file.name),
    )
}
