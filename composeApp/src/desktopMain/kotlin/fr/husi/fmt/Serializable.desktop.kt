package fr.husi.fmt

import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput

actual abstract class Serializable {
    actual open fun initializeDefaultValues() {}
    actual abstract fun serializeToBuffer(output: BinaryOutput)
    actual abstract fun deserializeFromBuffer(input: BinaryInput)
    actual open fun describeContents(): Int = 0

    actual abstract class CREATOR<T : Serializable> {
        actual abstract fun newInstance(): T
        actual abstract fun newArray(size: Int): Array<T?>
    }
}
