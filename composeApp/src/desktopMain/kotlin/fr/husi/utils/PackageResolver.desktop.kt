package fr.husi.utils

actual object PackageResolver {
    actual suspend fun awaitLoad() {}
    actual fun awaitLoadSync() {}
    actual fun findUidForPackage(packageName: String): Int? = null
    actual fun findPackagesForUid(uid: Int): Set<String>? = null
    actual fun isAppInstalled(packageName: String) = false
    actual fun loadAppLabel(packageName: String): String? = null
    actual fun loadAppIcon(packageName: String): Any? = null
}
