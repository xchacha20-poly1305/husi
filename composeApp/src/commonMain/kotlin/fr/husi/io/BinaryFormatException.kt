package fr.husi.io

/** Thrown by [BinaryInput] when the bytes do not hold what the caller asked for. */
open class BinaryFormatException(message: String) : RuntimeException(message)

/** Thrown when a read runs past the end of the payload. */
class BinaryUnderflowException(message: String) : BinaryFormatException(message)
