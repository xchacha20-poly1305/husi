package fr.husi.tailscale

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import fr.husi.ktx.deleteBlocking
import fr.husi.ktx.listFilesOrNull
import fr.husi.ktx.toJavaFile
import fr.husi.ktx.usableSpace
import fr.husi.lib.R
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.lastModified
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.size
import java.io.FileNotFoundException

class TaildropDocumentsProvider : DocumentsProvider() {

    /** `ContentProvider.requireContext` needs API 30. */
    private val providerContext get() = checkNotNull(context) { "provider is not attached" }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val context = providerContext
        val cursor = MatrixCursor(projection ?: ROOT_PROJECTION)
        for (root in TaildropDocumentRoot.entries) {
            val directory = existingDirectory(root) ?: continue
            cursor.newRow().also { row ->
                row.add(DocumentsContract.Root.COLUMN_ROOT_ID, root.rootId)
                row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, root.rootId)
                row.add(
                    DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_LOCAL_ONLY or DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD,
                )
                row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
                row.add(DocumentsContract.Root.COLUMN_TITLE, context.getString(R.string.app_name))
                row.add(DocumentsContract.Root.COLUMN_SUMMARY, root.directoryName)
                row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, directory.usableSpace())
            }
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DOCUMENT_PROJECTION)
        val document = parseTaildropDocumentId(documentId) ?: throw FileNotFoundException(documentId)
        val directory = existingDirectory(document.root) ?: throw FileNotFoundException(documentId)
        val name = document.name
        if (name == null) {
            addDirectoryRow(cursor, document.root, directory)
        } else {
            addFileRow(cursor, document.root, resolveFile(directory, name))
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DOCUMENT_PROJECTION)
        val parent = parseTaildropDocumentId(parentDocumentId) ?: return cursor
        if (parent.name != null) return cursor
        val directory = existingDirectory(parent.root) ?: return cursor
        cursor.setNotificationUri(providerContext.contentResolver, childDocumentsUri(parent.root))
        directory.listFilesOrNull()
            ?.filter(::isTaildropReceivedFile)
            ?.sortedBy { file -> file.name }
            ?.forEach { file -> addFileRow(cursor, parent.root, file) }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        if (ParcelFileDescriptor.parseMode(mode) != ParcelFileDescriptor.MODE_READ_ONLY) {
            throw FileNotFoundException("$documentId is read-only")
        }
        return ParcelFileDescriptor.open(requireFile(documentId).toJavaFile(), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun deleteDocument(documentId: String) {
        if (!requireFile(documentId).deleteBlocking()) {
            throw FileNotFoundException("failed to delete $documentId")
        }
        val root = parseTaildropDocumentId(documentId)?.root ?: return
        providerContext.contentResolver.notifyChange(childDocumentsUri(root), null)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = parseTaildropDocumentId(parentDocumentId) ?: return false
        val child = parseTaildropDocumentId(documentId) ?: return false
        return parent.name == null && child.name != null && parent.root == child.root
    }

    private fun existingDirectory(root: TaildropDocumentRoot): PlatformFile? =
        root.directory(providerContext).takeIf { directory -> directory.isDirectory() }

    private fun childDocumentsUri(root: TaildropDocumentRoot) = DocumentsContract.buildChildDocumentsUri(
        taildropDocumentsAuthority(providerContext),
        root.rootId,
    )

    private fun requireFile(documentId: String): PlatformFile {
        val document = parseTaildropDocumentId(documentId) ?: throw FileNotFoundException(documentId)
        val name = document.name ?: throw FileNotFoundException(documentId)
        val directory = existingDirectory(document.root) ?: throw FileNotFoundException(documentId)
        return resolveFile(directory, name)
    }

    private fun resolveFile(directory: PlatformFile, name: String): PlatformFile =
        directory.resolve(name).takeIf(::isTaildropReceivedFile) ?: throw FileNotFoundException(name)

    private fun addDirectoryRow(cursor: MatrixCursor, root: TaildropDocumentRoot, directory: PlatformFile) {
        cursor.newRow().also { row ->
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, root.rootId)
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, root.directoryName)
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, directory.lastModified().toEpochMilliseconds())
            row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            row.add(DocumentsContract.Document.COLUMN_SIZE, null)
        }
    }

    private fun addFileRow(cursor: MatrixCursor, root: TaildropDocumentRoot, file: PlatformFile) {
        cursor.newRow().also { row ->
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, root.fileDocumentId(file.name))
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, taildropMimeType(file.name))
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified().toEpochMilliseconds())
            row.add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_SUPPORTS_DELETE)
            row.add(DocumentsContract.Document.COLUMN_SIZE, file.size())
        }
    }

    private companion object {
        val ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
        )

        val DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}
