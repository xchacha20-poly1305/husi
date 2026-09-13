package fr.husi.bg

import android.content.pm.PackageInstaller
import android.os.IInterface
import android.os.Process
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

internal class ShizukuApkInstallSessionHost(ownPackageName: String) : ApkInstallSessionHost {

    private companion object {
        const val PACKAGE_SERVICE = "package"
        const val SHELL_PACKAGE = "com.android.shell"
        const val ROOT_UID = 0
    }

    private val runsAsRoot = runCatching { Shizuku.getUid() == ROOT_UID }.getOrDefault(false)

    private val privilegedInstaller: Any

    private val packageInstaller: PackageInstaller

    init {
        val packageServiceBinder = checkNotNull(SystemServiceHelper.getSystemService(PACKAGE_SERVICE)) {
            "the package service is not available"
        }
        val packageManager = PrivilegedPackageApi.packageManagerOf(
            ShizukuBinderWrapper(packageServiceBinder),
        )
        val installer = PrivilegedPackageApi.packageInstallerOf(packageManager)
        privilegedInstaller = PrivilegedPackageApi.privilegedPackageInstallerOf(
            ShizukuBinderWrapper((installer as IInterface).asBinder()),
        )
        packageInstaller = PrivilegedPackageApi.newPackageInstaller(
            privilegedInstaller = privilegedInstaller,
            installerPackageName = if (runsAsRoot) ownPackageName else SHELL_PACKAGE,
            userId = if (runsAsRoot) Process.myUserHandle().hashCode() else 0,
        )
    }

    override fun createSession(params: PackageInstaller.SessionParams): Int {
        PrivilegedPackageApi.addInstallFlags(params, INSTALL_REPLACE_EXISTING)
        return packageInstaller.createSession(params)
    }

    override fun openSession(sessionId: Int): PackageInstaller.Session {
        val sessionBinder = PrivilegedPackageApi.openSessionBinder(privilegedInstaller, sessionId)
        return PrivilegedPackageApi.newSession(ShizukuBinderWrapper(sessionBinder))
    }

    override fun abandonSession(sessionId: Int) {
        packageInstaller.abandonSession(sessionId)
    }
}
