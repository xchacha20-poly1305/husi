package fr.husi.repository

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import fr.husi.ktx.Logs
import kotlin.time.TimeSource

@Suppress("FunctionName")
private interface SystemConfigurationLibrary : Library {
    fun SCPreferencesCreateWithAuthorization(
        allocator: Pointer?, name: Pointer, id: Pointer?, authorization: Pointer,
    ): Pointer?
    fun SCPreferencesLock(preferences: Pointer, wait: Byte): Byte
    fun SCPreferencesUnlock(preferences: Pointer): Byte
    fun SCPreferencesCommitChanges(preferences: Pointer): Byte
    fun SCPreferencesApplyChanges(preferences: Pointer): Byte
    fun SCNetworkSetCopyCurrent(preferences: Pointer): Pointer?
    fun SCNetworkSetCopyServices(set: Pointer): Pointer?
    fun SCNetworkServiceCopy(preferences: Pointer, id: Pointer): Pointer?
    fun SCNetworkServiceGetServiceID(service: Pointer): Pointer
    fun SCNetworkServiceGetEnabled(service: Pointer): Byte
    fun SCNetworkServiceGetInterface(service: Pointer): Pointer?
    fun SCNetworkInterfaceGetBSDName(networkInterface: Pointer): Pointer?
    fun SCNetworkServiceCopyProtocol(service: Pointer, type: Pointer): Pointer?
    fun SCNetworkProtocolGetConfiguration(protocol: Pointer): Pointer?
    fun SCNetworkProtocolSetConfiguration(protocol: Pointer, configuration: Pointer): Byte
    fun SCError(): Int
    fun SCErrorString(status: Int): String?
}

@Suppress("FunctionName")
private interface CoreFoundationLibrary : Library {
    fun CFRelease(value: Pointer)
    fun CFGetTypeID(value: Pointer): NativeLong
    fun CFStringGetTypeID(): NativeLong
    fun CFNumberGetTypeID(): NativeLong
    fun CFStringCreateWithCString(allocator: Pointer?, value: ByteArray, encoding: Int): Pointer?
    fun CFStringGetLength(value: Pointer): NativeLong
    fun CFStringGetMaximumSizeForEncoding(length: NativeLong, encoding: Int): NativeLong
    fun CFStringGetCString(value: Pointer, buffer: ByteArray, size: NativeLong, encoding: Int): Byte
    fun CFArrayGetCount(array: Pointer): NativeLong
    fun CFArrayGetValueAtIndex(array: Pointer, index: NativeLong): Pointer
    fun CFDictionaryCreateMutableCopy(allocator: Pointer?, capacity: NativeLong, dictionary: Pointer): Pointer?
    fun CFDictionaryCreateMutable(
        allocator: Pointer?, capacity: NativeLong, keyCallbacks: Pointer, valueCallbacks: Pointer,
    ): Pointer?
    fun CFDictionaryGetValue(dictionary: Pointer, key: Pointer): Pointer?
    fun CFDictionarySetValue(dictionary: Pointer, key: Pointer, value: Pointer)
    fun CFNumberCreate(allocator: Pointer?, type: NativeLong, value: IntByReference): Pointer?
    fun CFNumberGetValue(number: Pointer, type: NativeLong, value: IntByReference): Byte
}

@Suppress("FunctionName")
private interface AuthorizationLibrary : Library {
    fun AuthorizationCreate(rights: Pointer?, environment: Pointer?, flags: Int, result: PointerByReference): Int
    fun AuthorizationFree(authorization: Pointer, flags: Int): Int
}

internal object MacOsSystemConfiguration : MacOsProxyPreferences {
    private const val UTF8 = 0x08000100
    private const val TRUE: Byte = 1
    private val signedInt32 = NativeLong(3) // kCFNumberSInt32Type
    private val zero = NativeLong(0)
    private val configuration by lazy { Native.load("SystemConfiguration", SystemConfigurationLibrary::class.java) }
    private val cf by lazy { Native.load("CoreFoundation", CoreFoundationLibrary::class.java) }
    private val authorization by lazy { Native.load("Security", AuthorizationLibrary::class.java) }
    private val foundation by lazy { NativeLibrary.getInstance("CoreFoundation") }

    override fun update(
        trackedServices: Set<String>,
        applyPending: Boolean,
        changes: (Map<String, MacOsProxyService>) -> Map<String, Map<String, Any>>,
        committed: (needsApply: Boolean) -> Unit,
    ) {
        val started = TimeSource.Monotonic.markNow()
        val timings = mutableListOf<String>()
        val references = mutableListOf<Pointer>()
        fun own(value: Pointer): Pointer = value.also(references::add)
        fun string(value: String): Pointer = own(checkNotNull(cfString(value)))
        fun runStage(name: String, action: () -> Unit) {
            val stageStarted = TimeSource.Monotonic.markNow()
            try {
                action()
            } finally {
                timings += "$name=${stageStarted.elapsedNow().inWholeMilliseconds}ms"
            }
        }

        val auth = PointerByReference()
        try {
            runStage("authorization") {
                val status = authorization.AuthorizationCreate(null, null, 0, auth)
                check(status == 0) { "macOS proxy authorization failed ($status)" }
            }
            val preferences = own(requireReference(
                configuration.SCPreferencesCreateWithAuthorization(null, string("Husi"), null, auth.value),
                "open network preferences",
            ))
            runStage("lock") {
                requireSuccess(configuration.SCPreferencesLock(preferences, TRUE), "lock network preferences")
            }
            try {
                val set = own(requireReference(configuration.SCNetworkSetCopyCurrent(preferences), "get current network location"))
                val services = own(requireReference(configuration.SCNetworkSetCopyServices(set), "list network services"))
                val proxyType = string("Proxies")
                val keys = macOsProxyKeys.flatten().associateWith(::string)
                val protocols = mutableMapOf<String, Pointer>()
                val current = buildMap {
                    fun addService(id: String, service: Pointer, inCurrentSet: Boolean) {
                        val protocol = configuration.SCNetworkServiceCopyProtocol(service, proxyType) ?: return
                        protocols[id] = own(protocol)
                        val device = configuration.SCNetworkServiceGetInterface(service)
                            ?.let(configuration::SCNetworkInterfaceGetBSDName)
                            ?.let(::readString)
                        val dictionary = configuration.SCNetworkProtocolGetConfiguration(protocol)
                        val fields = if (dictionary == null) {
                            emptyMap()
                        } else {
                            buildMap {
                                for ((key, nativeKey) in keys) {
                                    val value = cf.CFDictionaryGetValue(dictionary, nativeKey) ?: continue
                                    put(key, readValue(value))
                                }
                            }
                        }
                        put(id, MacOsProxyService(
                            device = device,
                            enabled = configuration.SCNetworkServiceGetEnabled(service) != 0.toByte(),
                            inCurrentSet = inCurrentSet,
                            values = fields,
                        ))
                    }
                    val locationServices = mutableSetOf<String>()
                    for (index in 0L until cf.CFArrayGetCount(services).toLong()) {
                        val service = cf.CFArrayGetValueAtIndex(services, NativeLong(index))
                        val id = readString(configuration.SCNetworkServiceGetServiceID(service))
                        locationServices += id
                        addService(id, service, inCurrentSet = true)
                    }
                    // A location change can move a previously configured service out of the active set.
                    for (id in trackedServices - locationServices) {
                        val service = configuration.SCNetworkServiceCopy(preferences, string(id)) ?: continue
                        addService(id, own(service), inCurrentSet = false)
                    }
                }
                val updates = changes(current)
                for ((id, fields) in updates) {
                    val protocol = protocols.getValue(id)
                    val original = configuration.SCNetworkProtocolGetConfiguration(protocol)
                    val dictionary = own(checkNotNull(
                        if (original == null) {
                            cf.CFDictionaryCreateMutable(
                                null, zero,
                                foundation.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks"),
                                foundation.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks"),
                            )
                        } else {
                            cf.CFDictionaryCreateMutableCopy(null, zero, original)
                        },
                    ))
                    for ((key, value) in fields) {
                        val nativeValue = when (value) {
                            is String -> string(value)
                            is Int -> own(checkNotNull(cf.CFNumberCreate(null, signedInt32, IntByReference(value))))
                            else -> error("Unsupported macOS proxy field: $key")
                        }
                        cf.CFDictionarySetValue(dictionary, keys.getValue(key), nativeValue)
                    }
                    requireSuccess(configuration.SCNetworkProtocolSetConfiguration(protocol, dictionary), "set proxy configuration")
                }
                if (updates.isNotEmpty()) {
                    runStage("commit") {
                        requireSuccess(configuration.SCPreferencesCommitChanges(preferences), "commit network preferences")
                    }
                }
                val needsApply = updates.isNotEmpty() || applyPending
                committed(needsApply)
                if (needsApply) {
                    runStage("apply") {
                        requireSuccess(configuration.SCPreferencesApplyChanges(preferences), "apply network preferences")
                    }
                }
            } finally {
                configuration.SCPreferencesUnlock(preferences)
            }
        } finally {
            for (value in references.asReversed()) {
                cf.CFRelease(value)
            }
            auth.value?.let { authorization.AuthorizationFree(it, 0) }
            Logs.d("macOS system proxy preferences: ${timings.joinToString()}, total=${started.elapsedNow().inWholeMilliseconds}ms")
        }
    }

    private fun cfString(value: String): Pointer? {
        return cf.CFStringCreateWithCString(null, value.encodeToByteArray() + 0.toByte(), UTF8)
    }

    private fun readString(value: Pointer): String {
        val maximum = cf.CFStringGetMaximumSizeForEncoding(cf.CFStringGetLength(value), UTF8).toInt()
        val bytes = ByteArray(maximum + 1)
        check(cf.CFStringGetCString(value, bytes, NativeLong(bytes.size.toLong()), UTF8) != 0.toByte())
        return bytes.decodeToString(endIndex = bytes.indexOf(0))
    }

    private fun readValue(value: Pointer): Any {
        return when (cf.CFGetTypeID(value)) {
            cf.CFStringGetTypeID() -> readString(value)
            cf.CFNumberGetTypeID() -> {
                val result = IntByReference()
                if (cf.CFNumberGetValue(value, signedInt32, result) != 0.toByte()) {
                    result.value
                } else {
                    Unit
                }
            }
            // Unexpected values still count as present and are replaced if the field is written.
            else -> Unit
        }
    }

    private fun requireReference(result: Pointer?, operation: String): Pointer {
        return result ?: fail(operation)
    }

    private fun requireSuccess(result: Byte, operation: String) {
        if (result == 0.toByte()) {
            fail(operation)
        }
    }

    private fun fail(operation: String): Nothing {
        val status = configuration.SCError()
        error("Could not $operation ($status): ${configuration.SCErrorString(status).orEmpty()}")
    }
}
