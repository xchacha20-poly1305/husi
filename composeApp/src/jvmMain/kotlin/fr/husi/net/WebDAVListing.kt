package fr.husi.net

import okhttp3.Headers
import okhttp3.HttpUrl
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2
import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

internal fun readDavListing(input: InputStream, directory: HttpUrl): List<WebDAVEntry> {
    val handler = DavListing(directory)
    val reader = SAXParserFactory.newInstance()
        .apply {
            isNamespaceAware = true
        }
        .newSAXParser()
        .xmlReader
    reader.contentHandler = handler
    reader.errorHandler = handler
    reader.entityResolver = handler
    reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
    try {
        reader.parse(InputSource(input))
    } catch (error: SAXException) {
        throw IOException("decode PROPFIND response", error)
    }
    return handler.entries
}

private class DavListing(private val directory: HttpUrl) : DefaultHandler2() {
    val entries = ArrayList<WebDAVEntry>()
    private val path = ArrayList<String>()
    private var href = ""
    private var properties = Properties()
    private var propstat = Properties()
    private var status = ""
    private var text: StringBuilder? = null

    private data class Properties(
        var collection: Boolean = false,
        var size: Long? = null,
        var modified: Long? = null,
    )

    override fun startDTD(name: String?, publicId: String?, systemId: String?) {
        throw SAXException("DOCTYPE is forbidden in a WebDAV listing")
    }

    override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
        throw SAXException("External entities are forbidden in a WebDAV listing")
    }

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
        val name = if (uri == "DAV:") localName else ""
        path += name
        if (path.size == 1 && name != "multistatus") {
            throw SAXException("expected DAV:multistatus")
        }
        when (path) {
            RESPONSE -> {
                href = ""
                properties = Properties()
            }

            PROPSTAT -> {
                propstat = Properties()
                status = ""
            }

            HREF, STATUS, LENGTH, MODIFIED -> {
                text = StringBuilder()
            }

            COLLECTION -> propstat.collection = true
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        text?.appendRange(ch, start, start + length)
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        when (path) {
            HREF -> href = takeText()
            STATUS -> status = takeText()
            LENGTH -> propstat.size = takeText().toLongOrNull()
            MODIFIED -> {
                val date = takeText()
                propstat.modified = runCatching {
                    Headers.headersOf("Last-Modified", date).getDate("Last-Modified")?.time
                }.getOrNull()
            }

            PROPSTAT -> if (isOkStatus(status)) {
                properties.collection = properties.collection || propstat.collection
                propstat.size?.let { properties.size = it }
                propstat.modified?.let { properties.modified = it }
            }

            RESPONSE -> addEntry()
        }
        path.removeAt(path.lastIndex)
    }

    private fun isOkStatus(statusLine: String): Boolean {
        return statusLine.substringAfter(' ').substringBefore(' ') == HttpStatus.OK.toString()
    }

    private fun takeText(): String = text.toString().trim().also { text = null }

    private fun addEntry() {
        if (href.isEmpty() || properties.collection) return
        val link = directory.resolve(href) ?: return
        val segments = link.pathSegments
        val directorySegments = directory.pathSegments
        val isDirectChild = segments.size == directorySegments.size
            && segments.dropLast(1) == directorySegments.dropLast(1)
        if (!isDirectChild) return
        val name = segments.last()
        if (!isDavFileName(name)) return
        entries += WebDAVEntry(name, properties.size ?: 0, properties.modified ?: 0)
    }

    private companion object {
        val RESPONSE = listOf("multistatus", "response")
        val HREF = RESPONSE + "href"
        val PROPSTAT = RESPONSE + "propstat"
        val STATUS = PROPSTAT + "status"
        val PROP = PROPSTAT + "prop"
        val LENGTH = PROP + "getcontentlength"
        val MODIFIED = PROP + "getlastmodified"
        val COLLECTION = PROP + listOf("resourcetype", "collection")
    }
}
