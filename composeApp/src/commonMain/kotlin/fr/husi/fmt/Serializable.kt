package fr.husi.fmt

import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput

expect abstract class Serializable() {
    open fun initializeDefaultValues()
    abstract fun serializeToBuffer(output: ByteBufferOutput)
    abstract fun deserializeFromBuffer(input: ByteBufferInput)
    open fun describeContents(): Int

    abstract class CREATOR<T : Serializable>() {
        abstract fun newInstance(): T
        abstract fun newArray(size: Int): Array<T?>
    }
}
