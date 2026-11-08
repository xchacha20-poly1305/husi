package fr.husi.ktx

fun String?.emptyAsNull(): String? = this?.takeIf { it.isNotEmpty() }
fun String?.blankAsNull(): String? = this?.takeIf { it.isNotBlank() }
