package fr.husi.bg

import android.annotation.SuppressLint
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import org.lsposed.hiddenapibypass.HiddenApiBypass

private const val PACKAGE_MANAGER_INTERFACE = "android.content.pm.IPackageManager"

private const val PACKAGE_INSTALLER_INTERFACE = "android.content.pm.IPackageInstaller"

private const val INSTALLER_SESSION_INTERFACE = "android.content.pm.IPackageInstallerSession"

private const val STUB_SUFFIX = $$"$Stub"

internal const val INSTALL_REPLACE_EXISTING = 0x00000002

@SuppressLint("PrivateApi")
internal object PrivilegedPackageApi {

    private val exempted by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        } else {
            true
        }
    }

    private val packageManagerInterface by lazy { hiddenClass(PACKAGE_MANAGER_INTERFACE) }
    private val packageInstallerInterface by lazy { hiddenClass(PACKAGE_INSTALLER_INTERFACE) }

    private val asPackageManager by lazy { asInterfaceMethod(PACKAGE_MANAGER_INTERFACE) }
    private val asPackageInstaller by lazy { asInterfaceMethod(PACKAGE_INSTALLER_INTERFACE) }
    private val asInstallerSession by lazy { asInterfaceMethod(INSTALLER_SESSION_INTERFACE) }

    private val getPackageInstaller by lazy {
        packageManagerInterface.getMethod("getPackageInstaller")
    }

    private val openInstallerSession by lazy {
        packageInstallerInterface.getMethod("openSession", Int::class.javaPrimitiveType)
    }

    private val packageInstallerConstructor by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PackageInstaller::class.java.getConstructor(
                packageInstallerInterface,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
            )
        } else {
            PackageInstaller::class.java.getConstructor(
                packageInstallerInterface,
                String::class.java,
                Int::class.javaPrimitiveType,
            )
        }
    }

    private val installerSessionConstructor by lazy {
        PackageInstaller.Session::class.java.getConstructor(hiddenClass(INSTALLER_SESSION_INTERFACE))
    }

    private val installFlagsField by lazy {
        PackageInstaller.SessionParams::class.java
            .getDeclaredField("installFlags")
            .apply { isAccessible = true }
    }

    fun packageManagerOf(binder: IBinder): Any = checkNotNull(asPackageManager.invoke(null, binder)) {
        "the package service returned no IPackageManager"
    }

    fun packageInstallerOf(packageManager: Any): Any =
        checkNotNull(getPackageInstaller.invoke(packageManager)) {
            "IPackageManager returned no IPackageInstaller"
        }

    fun privilegedPackageInstallerOf(binder: IBinder): Any =
        checkNotNull(asPackageInstaller.invoke(null, binder)) {
            "the package installer binder is not an IPackageInstaller"
        }

    fun newPackageInstaller(
        privilegedInstaller: Any,
        installerPackageName: String,
        userId: Int,
    ): PackageInstaller = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        packageInstallerConstructor.newInstance(
            privilegedInstaller,
            installerPackageName,
            null,
            userId,
        )
    } else {
        packageInstallerConstructor.newInstance(privilegedInstaller, installerPackageName, userId)
    }

    fun openSessionBinder(privilegedInstaller: Any, sessionId: Int): IBinder {
        val session = checkNotNull(openInstallerSession.invoke(privilegedInstaller, sessionId)) {
            "IPackageInstaller returned no session"
        }
        return (session as IInterface).asBinder()
    }

    fun newSession(binder: IBinder): PackageInstaller.Session =
        installerSessionConstructor.newInstance(asInstallerSession.invoke(null, binder))

    fun addInstallFlags(params: PackageInstaller.SessionParams, flags: Int) {
        installFlagsField.setInt(params, installFlagsField.getInt(params) or flags)
    }

    private fun hiddenClass(name: String): Class<*> {
        check(exempted) { "the hidden framework API could not be unlocked" }
        return Class.forName(name)
    }

    private fun asInterfaceMethod(interfaceName: String) =
        hiddenClass(interfaceName + STUB_SUFFIX).getMethod("asInterface", IBinder::class.java)
}
