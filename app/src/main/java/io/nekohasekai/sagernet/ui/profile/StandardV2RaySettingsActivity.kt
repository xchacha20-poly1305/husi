package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.isTLS
import io.nekohasekai.sagernet.widget.DurationPreference
import rikka.preference.SimpleMenuPreference

abstract class StandardV2RaySettingsActivity : ProfileSettingsActivity<StandardV2RayBean>() {

    lateinit var bean: StandardV2RayBean

    override fun StandardV2RayBean.init() {
        bean = this

        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort

        DataStore.serverNetwork = v2rayTransport
        DataStore.serverHost = host
        DataStore.serverPath = path
        DataStore.serverHeaders = headers
        DataStore.serverWsMaxEarlyData = wsMaxEarlyData
        DataStore.serverWsEarlyDataHeaderName = earlyDataHeaderName

        DataStore.serverSecurity = security
        DataStore.serverSNI = sni
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverFragment = fragment
        DataStore.serverFragmentFallbackDelay = fragmentFallbackDelay
        DataStore.serverRecordFragment = recordFragment
        DataStore.serverUtlsFingerPrint = utlsFingerprint
        DataStore.serverRealityPublicKey = realityPublicKey
        DataStore.serverRealityShortID = realityShortID
        DataStore.serverECH = ech
        DataStore.serverECHConfig = echConfig

        DataStore.serverMux = serverMux
        DataStore.serverBrutal = serverBrutal
        DataStore.serverMuxType = serverMuxType
        DataStore.serverMuxStrategy = serverMuxStrategy
        DataStore.serverMuxNumber = serverMuxNumber
        DataStore.serverMuxPadding = serverMuxPadding

        when (this) {
            is HttpBean -> {
                DataStore.serverUsername = username
                DataStore.serverPassword = password
            }

            is TrojanBean -> {
                DataStore.serverPassword = password
            }

            is VMessBean -> {
                DataStore.serverUserID = uuid
                DataStore.serverAlterID = alterId
                DataStore.serverEncryption = encryption
                DataStore.serverPacketEncoding = packetEncoding
                DataStore.serverAuthenticatedLength = authenticatedLength
            }
        }
    }

    override fun StandardV2RayBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort

        v2rayTransport = DataStore.serverNetwork
        host = DataStore.serverHost
        path = DataStore.serverPath
        headers = DataStore.serverHeaders
        wsMaxEarlyData = DataStore.serverWsMaxEarlyData
        earlyDataHeaderName = DataStore.serverWsEarlyDataHeaderName

        security = DataStore.serverSecurity
        sni = DataStore.serverSNI
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        allowInsecure = DataStore.serverAllowInsecure
        fragment = DataStore.serverFragment
        fragmentFallbackDelay = DataStore.serverFragmentFallbackDelay
        recordFragment = DataStore.serverRecordFragment
        utlsFingerprint = DataStore.serverUtlsFingerPrint
        realityPublicKey = DataStore.serverRealityPublicKey
        realityShortID = DataStore.serverRealityShortID
        ech = DataStore.serverECH
        echConfig = DataStore.serverECHConfig

        serverMux = DataStore.serverMux
        serverBrutal = DataStore.serverBrutal
        serverMuxType = DataStore.serverMuxType
        serverMuxStrategy = DataStore.serverMuxStrategy
        serverMuxNumber = DataStore.serverMuxNumber
        serverMuxPadding = DataStore.serverMuxPadding

        when (this) {
            is HttpBean -> {
                username = DataStore.serverUsername
                password = DataStore.serverPassword
            }

            is TrojanBean -> {
                password = DataStore.serverPassword
            }

            is VMessBean -> {
                uuid = DataStore.serverUserID
                alterId = DataStore.serverAlterID
                encryption = DataStore.serverEncryption
                packetEncoding = DataStore.serverPacketEncoding
                authenticatedLength = DataStore.serverAuthenticatedLength
            }
        }
    }

    private lateinit var securityCategory: PreferenceCategory
    private lateinit var tlsCamouflageCategory: PreferenceCategory
    private lateinit var echCategory: PreferenceCategory
    private lateinit var wsCategory: PreferenceCategory
    private lateinit var muxCategory: PreferenceCategory
    private lateinit var experimentsCategory: PreferenceCategory

    private lateinit var serverV2rayTransport: SimpleMenuPreference
    private lateinit var serverHost: EditTextPreference
    private lateinit var serverPath: EditTextPreference
    private lateinit var serverHeaders: EditTextPreference
    private lateinit var serverSecurity: SimpleMenuPreference

    private lateinit var serverBrutal: SwitchPreference
    private lateinit var serverMuxType: SimpleMenuPreference
    private lateinit var serverMuxNumber: EditTextPreference
    private lateinit var serverMuxStrategy: SimpleMenuPreference
    private lateinit var serverMuxPadding: SwitchPreference

    private lateinit var fragment: SwitchPreference
    private lateinit var fragmentFallbackDelay: DurationPreference

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.standard_v2ray_preferences)

        securityCategory = findPreference(Key.SERVER_SECURITY_CATEGORY)!!
        tlsCamouflageCategory = findPreference(Key.SERVER_TLS_CAMOUFLAGE_CATEGORY)!!
        echCategory = findPreference(Key.SERVER_ECH_CATEGORY)!!
        wsCategory = findPreference(Key.SERVER_WS_CATEGORY)!!
        muxCategory = findPreference(Key.SERVER_MUX_CATEGORY)!!
        experimentsCategory = findPreference(Key.SERVER_VMESS_EXPERIMENTS_CATEGORY)!!

        serverHost = findPreference(Key.SERVER_HOST)!!
        serverPath = findPreference(Key.SERVER_PATH)!!
        serverHeaders = findPreference(Key.SERVER_HEADERS)!!

        // vmess/vless/http/trojan
        val isHttp = bean is HttpBean
        val isVmess = bean is VMessBean && bean.isVLESS == false
        val isVless = bean.isVLESS == true
        val isTrojan = bean is TrojanBean

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }

        findPreference<EditTextPreference>(Key.SERVER_ALTER_ID)!!.apply {
            isVisible = isVmess
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }

        findPreference<EditTextPreference>(Key.SERVER_USER_ID)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }

        findPreference<SimpleMenuPreference>(Key.SERVER_V2RAY_TRANSPORT)!!.apply {
            isVisible = !isHttp
        }
        findPreference<EditTextPreference>(Key.SERVER_USER_ID)!!.apply {
            isVisible = isVmess || isVless
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<SimpleMenuPreference>(Key.SERVER_PACKET_ENCODING)!!.apply {
            isVisible = isVmess || isVless
        }
        findPreference<SimpleMenuPreference>(Key.SERVER_ENCRYPTION)!!.apply {
            isVisible = isVmess || isVless
        }
        findPreference<EditTextPreference>(Key.SERVER_USERNAME)!!.apply {
            isVisible = isHttp
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            isVisible = isHttp || isTrojan
            summaryProvider = PasswordSummaryProvider

            if (isTrojan) {
                setDialogTitle(R.string.password)
                setTitle(R.string.password)
            }
        }
        experimentsCategory.isVisible = isVmess

        serverMuxType = findPreference<SimpleMenuPreference>(Key.SERVER_MUX_TYPE)!!
        serverMuxStrategy = findPreference<SimpleMenuPreference>(Key.SERVER_MUX_STRATEGY)!!
        serverMuxPadding = findPreference<SwitchPreference>(Key.SERVER_MUX_PADDING)!!
        serverMuxNumber = findPreference<EditTextPreference>(Key.SERVER_MUX_NUMBER)!!.also {
            it.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        serverBrutal = findPreference<SwitchPreference>(Key.SERVER_BRUTAL)!!.also {
            it.setOnPreferenceChangeListener { _, newValue ->
                serverMuxNumber.isEnabled = !(newValue as Boolean)
                true
            }
        }
        muxCategory.isVisible = if (isHttp) {
            false
        } else when (bean.v2rayTransport) {
            "quic", "grpc" -> false
            "h2" -> !bean.isTLS()
            else -> true
        }
        updateMuxState(bean.serverMux)
        findPreference<SwitchPreference>(Key.SERVER_MUX)!!.apply {
            setOnPreferenceChangeListener { _, newValue ->
                updateMuxState(newValue as Boolean)
                true
            }
        }

        findPreference<SimpleMenuPreference>(Key.SERVER_ENCRYPTION)!!.apply {
            if (isVless) {
                title = resources.getString(R.string.xtls_flow)
                setIcon(R.drawable.ic_baseline_stream_24)
                setEntries(R.array.xtls_flow_value)
                setEntryValues(R.array.xtls_flow_value)
            } else {
                setEntries(R.array.vmess_encryption_value)
                setEntryValues(R.array.vmess_encryption_value)
            }
        }

        // menu with listener

        serverSecurity = findPreference(Key.SERVER_SECURITY)!!
        serverV2rayTransport = findPreference<SimpleMenuPreference>(Key.SERVER_V2RAY_TRANSPORT)!!
        serverV2rayTransport.apply {
            val isTls = isTLS(serverSecurity.value)
            updateView(isHttp, serverV2rayTransport.value, isTls)
            setOnPreferenceChangeListener { _, newValue ->
                updateView(isHttp, newValue as String, isTls)
                true
            }
        }

        serverSecurity.apply {
            updateTls(isTLS(serverSecurity.value))
            setOnPreferenceChangeListener { _, newValue ->
                newValue as String
                val isTls = isTLS(newValue)
                updateTls(isTls)
                updateView(isHttp, serverV2rayTransport.value, isTls)
                true
            }
        }

        fragment = findPreference(Key.SERVER_FRAGMENT)!!
        fragmentFallbackDelay = findPreference(Key.SERVER_FRAGMENT_FALLBACK_DELAY)!!
        fun updateFragment(enabled: Boolean = fragment.isChecked) {
            fragmentFallbackDelay.isEnabled = enabled
        }
        updateFragment()
        fragment.setOnPreferenceChangeListener { _, newValue ->
            updateFragment(newValue as Boolean)
            true
        }
    }

    private fun updateView(isHttp: Boolean, network: String, isTLS: Boolean) {
        if (isHttp) {
            serverHost.setTitle(R.string.http_host)
            serverPath.setTitle(R.string.http_path)
        }

        serverHost.isVisible = isHttp
        serverPath.isVisible = isHttp
        serverHeaders.isVisible = isHttp
        wsCategory.isVisible = false
        muxCategory.isVisible = !isHttp

        when (network) {
            "tcp" -> {
                serverHost.setTitle(R.string.http_host)
                serverPath.setTitle(R.string.http_path)
            }

            "http" -> {
                serverHost.apply {
                    setTitle(R.string.http_host)
                    isVisible = true
                }
                serverPath.apply {
                    setTitle(R.string.http_path)
                    isVisible = true
                }
                serverHeaders.apply {
                    isVisible = true
                }

                // http + TLS = h2
                if (isTLS) muxCategory.isVisible = false
            }

            "ws" -> {
                serverHost.apply {
                    setTitle(R.string.ws_host)
                    isVisible = true
                }
                serverPath.apply {
                    setTitle(R.string.ws_path)
                    isVisible = true
                }
                serverHeaders.apply {
                    isVisible = true
                }
                wsCategory.isVisible = true
            }

            "grpc" -> {
                serverPath.apply {
                    setTitle(R.string.grpc_service_name)
                    isVisible = true
                }

                muxCategory.isVisible = false
            }

            "quic" -> {
                muxCategory.isVisible = false
            }

            "httpupgrade" -> {
                serverHost.apply {
                    setTitle(R.string.http_upgrade_host)
                    isVisible = true
                }
                serverPath.apply {
                    setTitle(R.string.http_upgrade_path)
                    isVisible = true
                }
                serverHeaders.apply {
                    isVisible = true
                }
            }
        }
    }

    private fun updateMuxState(enabled: Boolean) {
        serverBrutal.isVisible = enabled
        serverMuxType.isVisible = enabled
        serverMuxStrategy.isVisible = enabled
        serverMuxNumber.isVisible = enabled
        serverMuxPadding.isVisible = enabled
    }

    private fun updateTls(isTLS: Boolean) {
        securityCategory.isVisible = isTLS
        tlsCamouflageCategory.isVisible = isTLS
        echCategory.isVisible = isTLS
    }

    private fun isTLS(security: String): Boolean = security == "tls"

}