package fr.husi.fmt

import android.os.Parcel
import android.os.Parcelable
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput

actual abstract class Serializable : Parcelable {
    actual open fun initializeDefaultValues() {}
    actual abstract fun serializeToBuffer(output: ByteBufferOutput)
    actual abstract fun deserializeFromBuffer(input: ByteBufferInput)

    actual override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByteArray(KryoConverters.serialize(this))
    }

    actual abstract class CREATOR<T : Serializable> : Parcelable.Creator<T> {
        actual abstract fun newInstance(): T
        actual abstract override fun newArray(size: Int): Array<T?>

        override fun createFromParcel(source: Parcel): T {
            return KryoConverters.deserialize(newInstance(), source.createByteArray())
        }
    }
}
