package fr.husi.ktx

import java.io.InputStream
import java.io.OutputStream

fun InputStream.use(out: OutputStream) {
    use { input ->
        out.use { output ->
            input.copyTo(output)
        }
    }
}
