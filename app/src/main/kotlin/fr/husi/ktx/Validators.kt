package fr.husi.ktx

import androidx.annotation.RawRes
import fr.husi.R
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.anytls.AnyTLSBean
import fr.husi.fmt.http.HttpBean
import fr.husi.fmt.hysteria.HysteriaBean
import fr.husi.fmt.juicity.JuicityBean
import fr.husi.fmt.shadowquic.ShadowQUICBean
import fr.husi.fmt.shadowsocks.ShadowsocksBean
import fr.husi.fmt.shadowtls.ShadowTLSBean
import fr.husi.fmt.socks.SOCKSBean
import fr.husi.fmt.trojan.TrojanBean
import fr.husi.fmt.tuic.TuicBean
import fr.husi.fmt.v2ray.VLESSBean
import fr.husi.fmt.v2ray.VMessBean

sealed interface ValidateResult {
    object Secure : ValidateResult
    class Deprecated(@param:RawRes val textRes: Int) : ValidateResult
    class Insecure(@param:RawRes val textRes: Int) : ValidateResult
}

val ssSecureList = "(gcm|poly1305)".toRegex()

fun AbstractBean.isInsecure(): ValidateResult {
    if (serverAddress.isIpAddress()) {
        if (serverAddress.startsWith("127.") || serverAddress.startsWith("::")) {
            return ValidateResult.Secure
        }
    }
    when (this) {
        is ShadowsocksBean -> {
            if (plugin.isBlank() || plugin.startsWith("obfs-local;")) {
                if (!method.contains(ssSecureList)) {
                    return ValidateResult.Insecure(R.raw.shadowsocks_stream_cipher)
                }
            }
        }

        is HttpBean -> if (!isTLS) return ValidateResult.Insecure(R.raw.not_encrypted)

        is SOCKSBean -> return ValidateResult.Insecure(R.raw.not_encrypted)

        is VMessBean -> {
            if (alterId > 0) return ValidateResult.Insecure(R.raw.vmess_md5_auth)
            if (encryption in arrayOf("none", "zero")) {
                if (!isTLS) return ValidateResult.Insecure(R.raw.not_encrypted)
            }
            if (allowInsecure) return ValidateResult.Insecure(R.raw.insecure)
        }

        is VLESSBean -> {
            if (encryption in arrayOf("", "none")) {
                if (!isTLS) return ValidateResult.Insecure(R.raw.not_encrypted)
            }
            if (allowInsecure) return ValidateResult.Insecure(R.raw.insecure)
        }

        is TrojanBean -> {
            if (!isTLS) return ValidateResult.Insecure(R.raw.not_encrypted)
            if (allowInsecure) return ValidateResult.Insecure(R.raw.insecure)
        }

        is HysteriaBean -> {
            if (allowInsecure) return ValidateResult.Insecure(R.raw.insecure)
            if (protocolVersion < HysteriaBean.PROTOCOL_VERSION_2) {
                return ValidateResult.Deprecated(R.raw.hysteria_legacy)
            }
        }

        is TuicBean -> {
            if (allowInsecure) return ValidateResult.Insecure(R.raw.insecure)
            if (zeroRTT) return ValidateResult.Insecure(R.raw.quic_0_rtt)
        }

        is ShadowTLSBean -> {
            if (allowInsecure) return ValidateResult.Insecure(R.raw.insecure)
            if (protocolVersion < 3) return ValidateResult.Deprecated(R.raw.shadowtls_legacy)
        }

        is JuicityBean -> {
            if (allowInsecure) return ValidateResult.Insecure(R.raw.insecure)
        }

        is AnyTLSBean -> {
            if (allowInsecure) return ValidateResult.Insecure(R.raw.insecure)
        }

        is ShadowQUICBean -> {
            if (zeroRTT) return ValidateResult.Insecure(R.raw.quic_0_rtt)
        }
    }

    return ValidateResult.Secure
}
