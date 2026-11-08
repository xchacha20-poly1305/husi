package fr.husi.fmt

object HttpVersion {
    const val HTTP_1 = 1
    const val HTTP_2 = 2
    const val HTTP_3 = 3

    fun isValid(version: Int) = version in HTTP_1..HTTP_3

    fun supported(isTLS: Boolean) = if (isTLS) {
        listOf(HTTP_1, HTTP_2, HTTP_3)
    } else {
        listOf(HTTP_1, HTTP_2)
    }
}
