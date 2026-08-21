package fr.husi.fmt

import android.os.Parcel
import android.os.Parcelable
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput

actual abstract class Serializable : Parcelable {
    actual open fun initializeDefaultValues() {}
    actual abstract fun serializeToBuffer(output: BinaryOutput)
    actual abstract fun deserializeFromBuffer(input: BinaryInput)

    actual override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByteArray(BeanConverters.serialize(this))
    }

    actual abstract class CREATOR<T : Serializable> : Parcelable.Creator<T> {
        actual abstract fun newInstance(): T
        actual abstract override fun newArray(size: Int): Array<T?>

        override fun createFromParcel(source: Parcel): T {
            return BeanConverters.deserialize(newInstance(), source.createByteArray())
        }
    }
}
