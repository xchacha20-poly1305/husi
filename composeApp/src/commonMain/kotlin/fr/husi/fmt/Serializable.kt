package fr.husi.fmt

import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput

expect abstract class Serializable() {
    open fun initializeDefaultValues()
    abstract fun serializeToBuffer(output: BinaryOutput)
    abstract fun deserializeFromBuffer(input: BinaryInput)
    open fun describeContents(): Int

    abstract class CREATOR<T : Serializable>() {
        abstract fun newInstance(): T
        abstract fun newArray(size: Int): Array<T?>
    }
}
