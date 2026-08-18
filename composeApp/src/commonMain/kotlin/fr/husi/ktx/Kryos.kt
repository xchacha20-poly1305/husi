package fr.husi.ktx

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import java.io.InputStream
import java.io.OutputStream

fun InputStream.byteBuffer() = ByteBufferInput(this)
fun OutputStream.byteBuffer() = ByteBufferOutput(this)

fun <T> ByteBufferInput.readList(deserialize: (ByteBufferInput) -> T): List<T> {
    val size = readInt()
    return buildList(size) {
        repeat(size) {
            add(deserialize(this@readList))
        }
    }
}

fun <T> ByteBufferOutput.writeList(list: List<T>, serialize: T.(ByteBufferOutput) -> Unit) {
    writeInt(list.size)
    for (item in list) {
        item.serialize(this@writeList)
    }
}
