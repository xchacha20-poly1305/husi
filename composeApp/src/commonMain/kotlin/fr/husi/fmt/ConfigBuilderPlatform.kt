package fr.husi.fmt

internal expect suspend fun SingBoxOptions.Inbound_TunOptions.applyPlatformConfig()

internal expect val localDNSSupportRaw: Boolean

internal expect val anchorDeviceName: String

/** Whether the core runs a protect service that plugins can hand their sockets to. */
internal expect val protectServiceAvailable: Boolean

/** File name of the protect service socket, shared with libcore's `protectPathName`. */
internal const val PROTECT_PATH_NAME = "protect_path"

internal expect val protectPath: String
