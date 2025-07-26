package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.widget.DurationPreference
import io.nekohasekai.sagernet.widget.MaterialSwitchPreference
import io.nekohasekai.sagernet.widget.PasswordSummaryProvider
import rikka.preference.SimpleMenuPreference

abstract class StandardV2RaySettingsActivity : ProfileSettingsActivity<StandardV2RayBean>() {

    override fun StandardV2RayBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort

        // V2Ray Transport
        DataStore.serverNetwork = v2rayTransport
        DataStore.serverHost = host
        DataStore.serverPath = path
        DataStore.serverHeaders = headers
        DataStore.serverWsMaxEarlyData = wsMaxEarlyData
        DataStore.serverWsEarlyDataHeaderName = earlyDataHeaderName

        // Security
        DataStore.serverSecurity = security
        DataStore.serverSNI = sni
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverDisableSNI = disableSNI
        DataStore.serverFragment = fragment
        DataStore.serverFragmentFallbackDelay = fragmentFallbackDelay
        DataStore.serverRecordFragment = recordFragment
        DataStore.serverUtlsFingerPrint = utlsFingerprint
        DataStore.serverRealityPublicKey = realityPublicKey
        DataStore.serverRealityShortID = realityShortID
        DataStore.serverECH = ech
        DataStore.serverECHConfig = echConfig

        // Mux
        DataStore.serverMux = serverMux
        DataStore.serverBrutal = serverBrutal
        DataStore.serverMuxType = serverMuxType
        DataStore.serverMuxStrategy = serverMuxStrategy
        DataStore.serverMuxNumber = serverMuxNumber
        DataStore.serverMuxPadding = serverMuxPadding

        // Protocol Specific
        when (this) {
            is HttpBean -> {
                DataStore.serverUsername = username
                DataStore.serverPassword = password
                DataStore.udpOverTcp = udpOverTcp
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
        // Basic
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort

        // V2Ray Transport
        v2rayTransport = DataStore.serverNetwork
        host = DataStore.serverHost
        path = DataStore.serverPath
        headers = DataStore.serverHeaders
        wsMaxEarlyData = DataStore.serverWsMaxEarlyData
        earlyDataHeaderName = DataStore.serverWsEarlyDataHeaderName

        // Security
        security = DataStore.serverSecurity
        sni = DataStore.serverSNI
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        allowInsecure = DataStore.serverAllowInsecure
        disableSNI = DataStore.serverDisableSNI
        fragment = DataStore.serverFragment
        fragmentFallbackDelay = DataStore.serverFragmentFallbackDelay
        recordFragment = DataStore.serverRecordFragment
        utlsFingerprint = DataStore.serverUtlsFingerPrint
        realityPublicKey = DataStore.serverRealityPublicKey
        realityShortID = DataStore.serverRealityShortID
        ech = DataStore.serverECH
        echConfig = DataStore.serverECHConfig

        // Mux
        serverMux = DataStore.serverMux
        serverBrutal = DataStore.serverBrutal
        serverMuxType = DataStore.serverMuxType
        serverMuxStrategy = DataStore.serverMuxStrategy
        serverMuxNumber = DataStore.serverMuxNumber
        serverMuxPadding = DataStore.serverMuxPadding

        // Protocol Specific
        when (this) {
            is HttpBean -> {
                username = DataStore.serverUsername
                password = DataStore.serverPassword
                udpOverTcp = DataStore.udpOverTcp
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

    private lateinit var serverV2rayTransport: SimpleMenuPreference
    private lateinit var serverHost: EditTextPreference
    private lateinit var serverPath: EditTextPreference
    private lateinit var serverHeaders: EditTextPreference
    private lateinit var serverSecurity: SimpleMenuPreference
    private lateinit var serverEncryption: SimpleMenuPreference // VLESS: flow

    private lateinit var serverMux: MaterialSwitchPreference
    private lateinit var serverBrutal: MaterialSwitchPreference
    private lateinit var serverMuxType: SimpleMenuPreference
    private lateinit var serverMuxNumber: EditTextPreference
    private lateinit var serverMuxStrategy: SimpleMenuPreference
    private lateinit var serverMuxPadding: MaterialSwitchPreference

    private lateinit var realityPublicKey: EditTextPreference
    private lateinit var disableSNI: MaterialSwitchPreference
    private lateinit var fragment: MaterialSwitchPreference
    private lateinit var fragmentFallbackDelay: DurationPreference

    private lateinit var experimentsCategory: PreferenceCategory
    private lateinit var authenticatedLength: MaterialSwitchPreference
    private lateinit var udpOverTcp: MaterialSwitchPreference

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.standard_v2ray_preferences)

        bindPreferences()
        setupInitialState()
        setupListeners()
    }

    /** Binds all preference views from the XML to their corresponding variables. */
    private fun PreferenceFragmentCompat.bindPreferences() {
        securityCategory = findPreference(Key.SERVER_SECURITY_CATEGORY)!!
        tlsCamouflageCategory = findPreference(Key.SERVER_TLS_CAMOUFLAGE_CATEGORY)!!
        echCategory = findPreference(Key.SERVER_ECH_CATEGORY)!!
        wsCategory = findPreference(Key.SERVER_WS_CATEGORY)!!
        muxCategory = findPreference(Key.SERVER_MUX_CATEGORY)!!

        serverHost = findPreference(Key.SERVER_HOST)!!
        serverPath = findPreference(Key.SERVER_PATH)!!
        serverHeaders = findPreference(Key.SERVER_HEADERS)!!
        serverSecurity = findPreference(Key.SERVER_SECURITY)!!
        serverV2rayTransport = findPreference(Key.SERVER_V2RAY_TRANSPORT)!!
        serverEncryption = findPreference(Key.SERVER_ENCRYPTION)!!

        serverMux = findPreference(Key.SERVER_MUX)!!
        serverBrutal = findPreference(Key.SERVER_BRUTAL)!!
        serverMuxType = findPreference(Key.SERVER_MUX_TYPE)!!
        serverMuxStrategy = findPreference(Key.SERVER_MUX_STRATEGY)!!
        serverMuxPadding = findPreference(Key.SERVER_MUX_PADDING)!!
        serverMuxNumber = findPreference(Key.SERVER_MUX_NUMBER)!!

        realityPublicKey = findPreference(Key.SERVER_REALITY_PUBLIC_KEY)!!
        disableSNI = findPreference(Key.SERVER_DISABLE_SNI)!!
        fragment = findPreference(Key.SERVER_FRAGMENT)!!
        fragmentFallbackDelay = findPreference(Key.SERVER_FRAGMENT_FALLBACK_DELAY)!!

        experimentsCategory = findPreference(Key.SERVER_VMESS_EXPERIMENTS_CATEGORY)!!
        authenticatedLength = findPreference(Key.SERVER_AUTHENTICATED_LENGTH)!!
        udpOverTcp = findPreference(Key.UDP_OVER_TCP)!!
    }

    /** Sets up the initial state of preferences based on the profile bean. */
    private fun PreferenceFragmentCompat.setupInitialState() {
        val isHttp = bean is HttpBean
        val isVmess = bean is VMessBean && !bean!!.isVLESS
        val isVless = bean!!.isVLESS
        val isTrojan = bean is TrojanBean

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.setOnBindEditTextListener(
            EditTextPreferenceModifiers.Port
        )
        findPreference<EditTextPreference>(Key.SERVER_ALTER_ID)!!.apply {
            isVisible = isVmess
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_USER_ID)!!.apply {
            isVisible = isVmess || isVless
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<SimpleMenuPreference>(Key.SERVER_PACKET_ENCODING)!!
            .isVisible = isVmess || isVless
        findPreference<SimpleMenuPreference>(Key.SERVER_ENCRYPTION)!!.apply {
            isVisible = isVmess || isVless
            if (isVless) {
                title = getString(R.string.xtls_flow)
                setIcon(R.drawable.ic_baseline_stream_24)
                setEntries(R.array.xtls_flow_value)
                setEntryValues(R.array.xtls_flow_value)

                setOnPreferenceChangeListener { _, newValue ->
                    muxCategory.isVisible = newValue.toString().isBlank()
                    true
                }
            } else {
                setEntries(R.array.vmess_encryption_value)
                setEntryValues(R.array.vmess_encryption_value)
            }
        }
        findPreference<EditTextPreference>(Key.SERVER_USERNAME)!!.isVisible = isHttp
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            isVisible = isHttp || isTrojan
            summaryProvider = PasswordSummaryProvider

            if (isTrojan) {
                setDialogTitle(R.string.password)
                setTitle(R.string.password)
            }
        }
        experimentsCategory.isVisible = isVmess || isHttp
        authenticatedLength.isVisible = isVmess
        udpOverTcp.isVisible = isHttp
        serverV2rayTransport.isVisible = !isHttp
        serverMuxNumber.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)

        updateUiState(serverV2rayTransport.value, serverSecurity.value, getFlow())
    }

    /** Sets up listeners for preferences that change the UI dynamically. */
    private fun setupListeners() {
        serverV2rayTransport.setOnPreferenceChangeListener { _, newValue ->
            updateUiState(newValue as String, serverSecurity.value, getFlow())
            true
        }

        serverSecurity.setOnPreferenceChangeListener { _, newValue ->
            updateUiState(serverV2rayTransport.value, newValue as String, getFlow())
            true
        }

        serverMux.setOnPreferenceChangeListener { _, newValue ->
            updateMuxControlsVisibility(newValue as Boolean)
            true
        }

        serverBrutal.setOnPreferenceChangeListener { _, newValue ->
            updateBrutalState(newValue as Boolean)
            true
        }

        realityPublicKey.setOnPreferenceChangeListener { _, newValue ->
            val isRealityEnabled = newValue.toString().isNotBlank()
            updateTlsCategoriesVisibility(isTLS(serverSecurity.value), isRealityEnabled)
            true
        }
        fragment.setOnPreferenceChangeListener { _, newValue ->
            fragmentFallbackDelay.isEnabled = newValue as Boolean
            true
        }
    }

    /**
     * The single source of truth for updating the UI based on transport and security settings.
     * Call this whenever a setting that affects layout visibility changes.
     */
    private fun updateUiState(network: String, security: String, flow: String?) {
        val isTls = isTLS(security)
        val isHttp = bean is HttpBean

        updateTlsCategoriesVisibility(isTls, realityPublicKey.text?.isNotBlank() == true)

        updateTransportViews(network, isHttp)

        muxCategory.isVisible = shouldShowMuxCategory(network, isTls, isHttp, flow)
        updateMuxControlsVisibility(serverMux.isChecked)
        updateBrutalState(serverBrutal.isChecked)
        fragmentFallbackDelay.isEnabled = fragment.isChecked
    }

    private fun updateTransportViews(network: String, isHttp: Boolean) {
        serverHost.isVisible = false
        serverPath.isVisible = false
        serverHeaders.isVisible = false
        wsCategory.isVisible = false

        if (isHttp) {
            serverHost.isVisible = true
            serverPath.isVisible = true
            serverHeaders.isVisible = true
            serverHost.setTitle(R.string.http_host)
            serverPath.setTitle(R.string.http_path)
            return
        }

        when (network) {
            "", "tcp" -> {}

            "http" -> {
                // V2Ray's tcp + http or so-called "http" (h2)
                serverHost.isVisible = true
                serverPath.isVisible = true
                serverHeaders.isVisible = true
                serverHost.setTitle(R.string.http_host)
                serverPath.setTitle(R.string.http_path)
            }

            "ws" -> {
                serverHost.isVisible = true
                serverPath.isVisible = true
                serverHeaders.isVisible = true
                wsCategory.isVisible = true
                serverHost.setTitle(R.string.ws_host)
                serverPath.setTitle(R.string.ws_path)
            }

            "grpc" -> {
                serverPath.isVisible = true
                serverPath.setTitle(R.string.grpc_service_name)
            }

            "httpupgrade" -> {
                serverHost.isVisible = true
                serverPath.isVisible = true
                serverHeaders.isVisible = true
                serverHost.setTitle(R.string.http_upgrade_host)
                serverPath.setTitle(R.string.http_upgrade_path)
            }

            "quic" -> {}
        }
    }

    private fun updateTlsCategoriesVisibility(isTls: Boolean, isReality: Boolean) {
        securityCategory.isVisible = isTls
        tlsCamouflageCategory.isVisible = isTls
        disableSNI.isVisible = !isReality
        echCategory.isVisible = isTls
    }

    private fun updateMuxControlsVisibility(enabled: Boolean) {
        serverBrutal.isVisible = enabled
        serverMuxType.isVisible = enabled
        serverMuxStrategy.isVisible = enabled
        serverMuxNumber.isVisible = enabled
        serverMuxPadding.isVisible = enabled
    }

    private fun updateBrutalState(isBrutalEnabled: Boolean) {
        serverMuxStrategy.isEnabled = !isBrutalEnabled
        serverMuxNumber.isEnabled = !isBrutalEnabled
    }

    private fun shouldShowMuxCategory(
        network: String,
        isTls: Boolean,
        isHttp: Boolean,
        flow: String?,
    ): Boolean {
        if (flow?.isNotBlank() == true) return false
        if (isHttp) return false
        return when (network) {
            "quic", "grpc" -> false
            "http" -> !isTls // h2 when TLS is enabled
            else -> true // tcp, ws, httpupgrade
        }
    }

    private fun isTLS(security: String): Boolean = security == "tls"

    private fun getFlow(): String? = if (bean!!.isVLESS) {
        serverEncryption.value
    } else {
        null
    }
}