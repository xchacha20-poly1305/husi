package fr.husi.ktx

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.bouncycastle.util.encoders.DecoderException
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemReader
import org.bouncycastle.util.io.pem.PemWriter
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter

fun decodePemBlocks(pem: String): List<ByteString> {
    val blocks = mutableListOf<ByteString>()
    PemReader(StringReader(pem)).use { reader ->
        while (true) {
            val pemObject = try {
                reader.readPemObject()
            } catch (_: IOException) {
                continue
            } catch (_: DecoderException) {
                continue
            } ?: break
            blocks += pemObject.content.toByteString()
        }
    }
    return blocks
}

fun encodePemBlock(type: String, der: ByteString): String {
    val writer = StringWriter()
    PemWriter(writer).use { pemWriter ->
        pemWriter.writeObject(PemObject(type, der.toByteArray()))
    }
    return writer.toString().replace("\r\n", "\n")
}
