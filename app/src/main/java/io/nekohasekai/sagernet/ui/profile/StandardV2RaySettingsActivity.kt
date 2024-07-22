package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type
import moe.matsuri.nb4a.ui.SimpleMenuPreference

abstract class StandardV2RaySettingsActivity : ProfileSettingsActivity<StandardV2RayBean>() {

    private var tmpBean: StandardV2RayBean? = null

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))
    private val serverAddress = pbm.add(PreferenceBinding(Type.Text, Key.SERVER_ADDRESS))
    private val serverPort = pbm.add(PreferenceBinding(Type.TextToInt, Key.SERVER_PORT))
    private val uuid = pbm.add(PreferenceBinding(Type.Text, "uuid"))
    private val username = pbm.add(PreferenceBinding(Type.Text, "username"))
    private val password = pbm.add(PreferenceBinding(Type.Text, "password"))
    private val alterId = pbm.add(PreferenceBinding(Type.TextToInt, "alterId"))
    private val encryption = pbm.add(PreferenceBinding(Type.Text, "encryption"))
    private val type = pbm.add(PreferenceBinding(Type.Text, "type"))
    private val host = pbm.add(PreferenceBinding(Type.Text, "host"))
    private val path = pbm.add(PreferenceBinding(Type.Text, "path"))
    private val packetEncoding = pbm.add(PreferenceBinding(Type.TextToInt, "packetEncoding"))
    private val wsMaxEarlyData = pbm.add(PreferenceBinding(Type.TextToInt, "wsMaxEarlyData"))
    private val earlyDataHeaderName = pbm.add(PreferenceBinding(Type.Text, "earlyDataHeaderName"))
    private val security = pbm.add(PreferenceBinding(Type.Text, "security"))
    private val sni = pbm.add(PreferenceBinding(Type.Text, "sni"))
    private val alpn = pbm.add(PreferenceBinding(Type.Text, "alpn"))
    private val certificates = pbm.add(PreferenceBinding(Type.Text, "certificates"))
    private val allowInsecure = pbm.add(PreferenceBinding(Type.Bool, "allowInsecure"))
    private val utlsFingerprint = pbm.add(PreferenceBinding(Type.Text, "utlsFingerprint"))
    private val realityPubKey = pbm.add(PreferenceBinding(Type.Text, "realityPubKey"))
    private val realityShortId = pbm.add(PreferenceBinding(Type.Text, "realityShortId"))
    private val ech = pbm.add(PreferenceBinding(Type.Bool, Key.ECH))
    private val echCfg = pbm.add(PreferenceBinding(Type.Text, Key.ECH_CFG))
    private val authenticatedLength =
        pbm.add(PreferenceBinding(Type.Bool, Key.AUTHENTICATED_LENGTH))

    private val serverMux = pbm.add(PreferenceBinding(Type.Bool, Key.SERVER_MUX))
    private val serverBrutal = pbm.add(PreferenceBinding(Type.Bool, Key.SERVER_BRUTAL))
    private val serverMuxType = pbm.add(PreferenceBinding(Type.Int, Key.SERVER_MUX_TYPE))
    private val serverMuxConcurrency =
        pbm.add(PreferenceBinding(Type.TextToInt, Key.SERVER_MUX_CONCURRENCY))
    private val serverMuxPadding = pbm.add(PreferenceBinding(Type.Bool, Key.SERVER_MUX_PADDING))

    override fun StandardV2RayBean.init() {
        if (this is TrojanBean) {
            this@StandardV2RaySettingsActivity.uuid.fieldName = "password"
            this@StandardV2RaySettingsActivity.password.disable = true
        }

        tmpBean = this // copy bean
        pbm.writeToCacheAll(this)
    }

    override fun StandardV2RayBean.serialize() {
        pbm.fromCacheAll(this)
    }

    private lateinit var securityCategory: PreferenceCategory
    private lateinit var tlsCamouflageCategory: PreferenceCategory
    private lateinit var echCategory: PreferenceCategory
    private lateinit var wsCategory: PreferenceCategory
    private lateinit var muxCategory: PreferenceCategory
    private lateinit var experimentsCategory: PreferenceCategory

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.standard_v2ray_preferences)
        pbm.setPreferenceFragment(this)
        securityCategory = findPreference(Key.SERVER_SECURITY_CATEGORY)!!
        tlsCamouflageCategory = findPreference(Key.SERVER_TLS_CAMOUFLAGE_CATEGORY)!!
        echCategory = findPreference(Key.SERVER_ECH_CATEGORY)!!
        wsCategory = findPreference(Key.SERVER_WS_CATEGORY)!!
        muxCategory = findPreference(Key.SERVER_MUX_CATEGORY)!!
        experimentsCategory = findPreference(Key.SERVER_VMESS_EXPERIMENTS_CATEGORY)!!


        // vmess/vless/http/trojan
        val isHttp = tmpBean is HttpBean
        val isVmess = tmpBean is VMessBean && tmpBean?.isVLESS == false
        val isVless = tmpBean?.isVLESS == true

        serverPort.preference.apply {
            this as EditTextPreference
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }

        alterId.preference.apply {
            this as EditTextPreference
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }

        uuid.preference.summaryProvider = PasswordSummaryProvider

        type.preference.isVisible = !isHttp
        uuid.preference.isVisible = !isHttp
        packetEncoding.preference.isVisible = isVmess || isVless
        alterId.preference.isVisible = isVmess
        encryption.preference.isVisible = isVmess || isVless
        username.preference.isVisible = isHttp
        password.preference.isVisible = isHttp
        experimentsCategory.isVisible = isVmess

        muxCategory.isVisible = if (isHttp) {
            false
        } else when (tmpBean?.type) {
            "quic", "grpc" -> false
            "h2" -> tmpBean?.security != "tls"
            else -> true
        }
        updateMuxState(tmpBean?.serverMux ?: false)

        if (tmpBean is TrojanBean) {
            uuid.preference.title = resources.getString(R.string.password)
        }

        encryption.preference.apply {
            this as SimpleMenuPreference
            if (tmpBean!!.isVLESS) {
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

        type.preference.apply {
            val tls = security.readStringFromCache()
            updateView(isHttp, type.readStringFromCache(), tls)
            this as SimpleMenuPreference
            setOnPreferenceChangeListener { _, newValue ->
                updateView(isHttp, newValue as String, tls)
                true
            }
        }

        security.preference.apply {
            updateTls(security.readStringFromCache())
            this as SimpleMenuPreference

            setOnPreferenceChangeListener { _, newValue ->
                newValue as String
                updateTls(newValue)
                updateView(isHttp, type.readStringFromCache(), newValue)
                true
            }
        }

        serverMux.preference.apply {
            this as SwitchPreference
            setOnPreferenceChangeListener { _, newValue ->
                updateMuxState(newValue as Boolean)
                true
            }
        }
        serverBrutal.preference.apply {
            this as SwitchPreference
            setOnPreferenceChangeListener { _, newValue ->
                serverMuxConcurrency.preference.isEnabled = !(newValue as Boolean)
                true
            }
        }
        serverMuxConcurrency.preference.isEnabled = !(tmpBean?.serverBrutal ?: false)
        (serverMuxConcurrency.preference as EditTextPreference)
            .setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
    }

    private fun updateView(isHttp: Boolean, network: String, tls: String) {
        host.preference.isVisible = false
        path.preference.isVisible = false
        wsCategory.isVisible = false
        muxCategory.isVisible = !isHttp

        when (network) {
            "tcp" -> {
                host.preference.setTitle(R.string.http_host)
                path.preference.setTitle(R.string.http_path)
            }

            "http" -> {
                host.preference.setTitle(R.string.http_host)
                path.preference.setTitle(R.string.http_path)
                host.preference.isVisible = true
                path.preference.isVisible = true

                // http + TLS = h2
                if (tls == "tls") muxCategory.isVisible = false
            }

            "ws" -> {
                host.preference.setTitle(R.string.ws_host)
                path.preference.setTitle(R.string.ws_path)
                host.preference.isVisible = true
                path.preference.isVisible = true
                wsCategory.isVisible = true
            }

            "grpc" -> {
                path.preference.setTitle(R.string.grpc_service_name)
                path.preference.isVisible = true

                muxCategory.isVisible = false
            }

            "quic" -> {
                muxCategory.isVisible = false
            }

            "httpupgrade" -> {
                host.preference.setTitle(R.string.http_upgrade_host)
                path.preference.setTitle(R.string.http_upgrade_path)
                host.preference.isVisible = true
                path.preference.isVisible = true
            }
        }
    }

    private fun updateMuxState(enabled: Boolean) {
        if (enabled) {
            serverBrutal.preference.isVisible = true
            serverMuxType.preference.isVisible = true
            serverMuxConcurrency.preference.isVisible = true
            serverMuxPadding.preference.isVisible = true
        } else {
            serverBrutal.preference.isVisible = false
            serverMuxType.preference.isVisible = false
            serverMuxConcurrency.preference.isVisible = false
            serverMuxPadding.preference.isVisible = false
        }
    }

    private fun updateTls(tls: String) {
        val isTLS = tls == "tls"
        securityCategory.isVisible = isTLS
        tlsCamouflageCategory.isVisible = isTLS
        echCategory.isVisible = isTLS
    }

}