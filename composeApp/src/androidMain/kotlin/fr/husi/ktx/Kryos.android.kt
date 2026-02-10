package fr.husi.ktx

import android.os.Parcel
import android.os.Parcelable

fun Parcelable.marshall(): ByteArray {
    val parcel = Parcel.obtain()
    writeToParcel(parcel, 0)
    val bytes = parcel.marshall()
    parcel.recycle()
    return bytes
}

fun ByteArray.unmarshall(): Parcel {
    val parcel = Parcel.obtain()
    parcel.unmarshall(this, 0, size)
    parcel.setDataPosition(0) // This is extremely important!
    return parcel
}

fun <T> ByteArray.unmarshall(constructor: (Parcel) -> T): T {
    val parcel = unmarshall()
    val result = constructor(parcel)
    parcel.recycle()
    return result
}
