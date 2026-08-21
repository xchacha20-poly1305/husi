package fr.husi.io

fun <T> BinaryInput.readList(deserialize: (BinaryInput) -> T): List<T> {
    val size = readInt()
    return buildList(size) {
        repeat(size) {
            add(deserialize(this@readList))
        }
    }
}

fun <T> BinaryOutput.writeList(list: List<T>, serialize: T.(BinaryOutput) -> Unit) {
    writeInt(list.size)
    for (item in list) {
        item.serialize(this@writeList)
    }
}
