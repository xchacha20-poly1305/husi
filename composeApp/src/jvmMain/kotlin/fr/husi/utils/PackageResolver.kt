package fr.husi.utils

expect object PackageResolver {
    suspend fun awaitLoad()
    fun awaitLoadSync()
    fun findUidForPackage(packageName: String): Int?
    fun findPackagesForUid(uid: Int): Set<String>?
    fun isAppInstalled(packageName: String): Boolean
    fun loadAppLabel(packageName: String): String?
    fun loadAppIcon(packageName: String): Any?
}
