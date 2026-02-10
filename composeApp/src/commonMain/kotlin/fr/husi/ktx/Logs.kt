package fr.husi.ktx

import java.io.InputStream
import java.io.OutputStream

expect object Logs {

    fun d(message: String)

    fun d(message: String, exception: Throwable)

    fun i(message: String)

    fun i(message: String, exception: Throwable)

    fun w(message: String)

    fun w(message: String, exception: Throwable)

    fun w(exception: Throwable)

    fun e(message: String)

    fun e(message: String, exception: Throwable)

    fun e(exception: Throwable)

}

fun InputStream.use(out: OutputStream) {
    use { input ->
        out.use { output ->
            input.copyTo(output)
        }
    }
}
