package fr.husi.ktx

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
import fr.husi.resources.Res
import fr.husi.resources.warn_hysteria_legacy
import fr.husi.resources.warn_insecure
import fr.husi.resources.warn_not_encrypted
import fr.husi.resources.warn_quic_0_rtt
import fr.husi.resources.warn_shadowsocks_stream_cipher
import fr.husi.resources.warn_shadowtls_legacy
import fr.husi.resources.warn_vmess_md5_auth
import org.jetbrains.compose.resources.StringResource

sealed interface ValidateResult {
    object Secure : ValidateResult
    class Deprecated(val textRes: StringResource) : ValidateResult
    class Insecure(val textRes: StringResource) : ValidateResult
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
                    return ValidateResult.Insecure(Res.string.warn_shadowsocks_stream_cipher)
                }
            }
        }

        is HttpBean -> if (!isTLS) return ValidateResult.Insecure(Res.string.warn_not_encrypted)

        is SOCKSBean -> return ValidateResult.Insecure(Res.string.warn_not_encrypted)

        is VMessBean -> {
            if (alterId > 0) return ValidateResult.Insecure(Res.string.warn_vmess_md5_auth)
            if (encryption in arrayOf("none", "zero")) {
                if (!isTLS) return ValidateResult.Insecure(Res.string.warn_not_encrypted)
            }
            if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
        }

        is VLESSBean -> {
            if (encryption in arrayOf("", "none")) {
                if (!isTLS) return ValidateResult.Insecure(Res.string.warn_not_encrypted)
            }
            if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
        }

        is TrojanBean -> {
            if (!isTLS) return ValidateResult.Insecure(Res.string.warn_not_encrypted)
            if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
        }

        is HysteriaBean -> {
            if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
            if (protocolVersion < HysteriaBean.PROTOCOL_VERSION_2) {
                return ValidateResult.Deprecated(Res.string.warn_hysteria_legacy)
            }
        }

        is TuicBean -> {
            if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
            if (zeroRTT) return ValidateResult.Insecure(Res.string.warn_quic_0_rtt)
        }

        is ShadowTLSBean -> {
            if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
            if (protocolVersion < 3) return ValidateResult.Deprecated(Res.string.warn_shadowtls_legacy)
        }

        is JuicityBean -> {
            if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
        }

        is AnyTLSBean -> {
            if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
        }

        is ShadowQUICBean -> {
            if (zeroRTT) return ValidateResult.Insecure(Res.string.warn_quic_0_rtt)
        }
    }

    return ValidateResult.Secure
}
