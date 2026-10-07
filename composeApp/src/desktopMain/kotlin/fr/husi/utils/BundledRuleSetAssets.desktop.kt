package fr.husi.utils

import java.io.InputStream

internal actual fun openBundledResource(path: String): InputStream? {
    val classLoader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
    return classLoader.getResourceAsStream(path)
}
