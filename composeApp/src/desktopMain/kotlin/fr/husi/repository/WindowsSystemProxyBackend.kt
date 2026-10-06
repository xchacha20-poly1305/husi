package fr.husi.repository

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Union
import com.sun.jna.WString

@Suppress("FunctionName")
private interface WinInetLibrary : Library {
    fun InternetSetOptionW(
        hInternet: Pointer?,
        dwOption: Int,
        lpBuffer: Pointer?,
        dwBufferLength: Int,
    ): Boolean
}

/** `INTERNET_PER_CONN_OPTIONW`. The FILETIME member of the value union is never written, so it is left out. */
@Structure.FieldOrder("dwOption", "value")
internal class InternetPerConnOption : Structure() {
    @JvmField
    var dwOption: Int = 0

    @JvmField
    var value: Value = Value()

    class Value : Union() {
        @JvmField
        var dwValue: Int = 0

        @JvmField
        var pszValue: WString? = null
    }
}

/** `INTERNET_PER_CONN_OPTION_LISTW`; a null [pszConnection] targets the LAN connection. */
@Structure.FieldOrder("dwSize", "pszConnection", "dwOptionCount", "dwOptionError", "pOptions")
internal class InternetPerConnOptionList : Structure() {
    @JvmField
    var dwSize: Int = 0

    @JvmField
    var pszConnection: WString? = null

    @JvmField
    var dwOptionCount: Int = 0

    @JvmField
    var dwOptionError: Int = 0

    @JvmField
    var pOptions: Pointer? = null
}

internal object WindowsSystemProxyBackend : SystemProxyBackend {

    private const val WININET_LIBRARY_NAME = "wininet"
    private val wininet by lazy {
        Native.load(WININET_LIBRARY_NAME, WinInetLibrary::class.java)
    }

    // InternetSetOption options.
    private const val INTERNET_OPTION_REFRESH = 37
    private const val INTERNET_OPTION_SETTINGS_CHANGED = 39
    private const val INTERNET_OPTION_PER_CONNECTION_OPTION = 75
    private const val INTERNET_OPTION_PROXY_SETTINGS_CHANGED = 95

    // INTERNET_PER_CONN_OPTION.dwOption values.
    private const val INTERNET_PER_CONN_FLAGS = 1
    private const val INTERNET_PER_CONN_PROXY_SERVER = 2

    // INTERNET_PER_CONN_FLAGS bits.
    private const val PROXY_TYPE_DIRECT = 0x1
    private const val PROXY_TYPE_PROXY = 0x2
    private const val PROXY_TYPE_AUTO_DETECT = 0x8

    override fun enable(host: String, port: Int) {
        setPerConnectionOptions(
            flags = PROXY_TYPE_PROXY or PROXY_TYPE_DIRECT,
            proxyServer = "http://$host:$port",
        )
    }

    override fun disable() {
        setPerConnectionOptions(
            flags = PROXY_TYPE_DIRECT or PROXY_TYPE_AUTO_DETECT,
            proxyServer = null,
        )
    }

    private fun setPerConnectionOptions(flags: Int, proxyServer: String?) {
        val optionCount = if (proxyServer == null) {
            1
        } else {
            2
        }
        @Suppress("UNCHECKED_CAST")
        val options = InternetPerConnOption().toArray(optionCount) as Array<InternetPerConnOption>
        options[0].apply {
            dwOption = INTERNET_PER_CONN_FLAGS
            value.dwValue = flags
            value.setType(Int::class.javaPrimitiveType)
        }
        if (proxyServer != null) {
            options[1].apply {
                dwOption = INTERNET_PER_CONN_PROXY_SERVER
                value.pszValue = WString(proxyServer)
                value.setType(WString::class.java)
            }
        }
        options.forEach { it.write() }

        val optionList = InternetPerConnOptionList().apply {
            dwSize = size()
            dwOptionCount = optionCount
            pOptions = options[0].pointer
            write()
        }
        setOption(INTERNET_OPTION_PER_CONNECTION_OPTION, optionList.pointer, optionList.size())
        setOption(INTERNET_OPTION_SETTINGS_CHANGED)
        setOption(INTERNET_OPTION_PROXY_SETTINGS_CHANGED)
        setOption(INTERNET_OPTION_REFRESH)
    }

    private fun setOption(option: Int, buffer: Pointer? = null, bufferLength: Int = 0) {
        check(wininet.InternetSetOptionW(null, option, buffer, bufferLength)) {
            "InternetSetOptionW($option) failed with error ${Native.getLastError()}"
        }
    }
}
