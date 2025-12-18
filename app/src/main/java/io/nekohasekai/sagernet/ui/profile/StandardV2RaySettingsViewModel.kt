package io.nekohasekai.sagernet.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean

@Immutable
internal sealed interface StandardV2RayUiState : ProfileSettingsUiState {
    val name: String
    val address: String
    val port: Int

    val v2rayTransport: String
    val host: String
    val path: String
    val headers: String
    val wsMaxEarlyData: Int
    val wsEarlyDataHeaderName: String

    val security: String
    val sni: String
    val alpn: String
    val certificate: String
    val certPublicKeySha256: String
    val allowInsecure: Boolean
    val disableSNI: Boolean
    val tlsFragment: Boolean
    val tlsFragmentFallbackDelay: String
    val tlsRecordFragment: Boolean
    val utlsFingerprint: String
    val realityPublicKey: String
    val realityShortID: String
    val ech: Boolean
    val echConfig: String
    val echQueryServerName: String
    val clientCert: String
    val clientKey: String

    val enableMux: Boolean
    val brutal: Boolean
    val muxType: Int
    val muxStrategy: Int
    val muxNumber: Int
    val muxPadding: Boolean
}

@Stable
internal abstract class StandardV2RaySettingsViewModel<T : StandardV2RayBean> :
    ProfileSettingsViewModel<T>() {

    abstract fun setName(name: String)
    abstract fun setAddress(address: String)
    abstract fun setPort(port: Int)
    abstract fun setTransport(transport: String)
    abstract fun setHost(host: String)
    abstract fun setPath(path: String)
    abstract fun setHeaders(headers: String)
    abstract fun setWsMaxEarlyData(maxEarlyData: Int)
    abstract fun setWsEarlyDataHeaderName(headerName: String)
    abstract fun setSecurity(security: String)
    abstract fun setSni(sni: String)
    abstract fun setAlpn(alpn: String)
    abstract fun setCertificate(certificate: String)
    abstract fun setCertPublicKeySha256(sha256: String)
    abstract fun setAllowInsecure(allow: Boolean)
    abstract fun setDisableSNI(disable: Boolean)
    abstract fun setTlsFragment(enable: Boolean)
    abstract fun setTlsFragmentFallbackDelay(delay: String)
    abstract fun setTlsRecordFragment(enable: Boolean)
    abstract fun setUtlsFingerprint(fingerprint: String)
    abstract fun setRealityPublicKey(publicKey: String)
    abstract fun setRealityShortID(shortID: String)
    abstract fun setEch(enable: Boolean)
    abstract fun setEchConfig(config: String)
    abstract fun setEchQueryServerName(queryServerName: String)
    abstract fun setClientCert(cert: String)
    abstract fun setClientKey(key: String)
    abstract fun setEnableMux(enable: Boolean)
    abstract fun setBrutal(enable: Boolean)
    abstract fun setMuxType(type: Int)
    abstract fun setMuxStrategy(strategy: Int)
    abstract fun setMuxNumber(number: Int)
    abstract fun setMuxPadding(enable: Boolean)
}