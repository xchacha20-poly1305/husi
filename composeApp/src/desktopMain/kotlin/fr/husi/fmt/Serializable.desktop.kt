package fr.husi.fmt

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput

actual abstract class Serializable {
    actual open fun initializeDefaultValues() {}
    actual abstract fun serializeToBuffer(output: ByteBufferOutput)
    actual abstract fun deserializeFromBuffer(input: ByteBufferInput)
    actual open fun describeContents(): Int = 0

    actual abstract class CREATOR<T : Serializable> {
        actual abstract fun newInstance(): T
        actual abstract fun newArray(size: Int): Array<T?>
    }
}
