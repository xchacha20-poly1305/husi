package fr.husi.bg

import android.content.Context
import android.content.pm.PackageInstaller

internal class SystemApkInstallSessionHost(context: Context) : ApkInstallSessionHost {

    private val packageInstaller = context.packageManager.packageInstaller

    override fun createSession(params: PackageInstaller.SessionParams): Int =
        packageInstaller.createSession(params)

    override fun openSession(sessionId: Int): PackageInstaller.Session =
        packageInstaller.openSession(sessionId)

    override fun abandonSession(sessionId: Int) {
        packageInstaller.abandonSession(sessionId)
    }
}
