package fr.husi.fmt

internal expect suspend fun SingBoxOptions.Inbound_TunOptions.applyPlatformConfig()

internal expect val localDNSSupportRaw: Boolean

internal expect val anchorDeviceName: String

/** File name of the protect service socket, shared with libcore's `protectPathName`. */
internal const val PROTECT_PATH_NAME = "protect_path"

internal expect val protectPath: String
