/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ktx

import androidx.annotation.RawRes
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.anytls.AnyTLSBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.juicity.JuicityBean
import io.nekohasekai.sagernet.fmt.shadowquic.ShadowQUICBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.tuic.TuicBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.fmt.v2ray.isTLS
import io.nekohasekai.sagernet.fmt.shadowtls.ShadowTLSBean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean

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

        is HttpBean -> if (!isTLS()) return ValidateResult.Insecure(R.raw.not_encrypted)

        is SOCKSBean -> return ValidateResult.Insecure(R.raw.not_encrypted)

        is VMessBean -> {
            if (alterId > 0) return ValidateResult.Insecure(R.raw.vmess_md5_auth)
            if (encryption in arrayOf("none", "zero")) {
                if (!isTLS()) return ValidateResult.Insecure(R.raw.not_encrypted)
            }
            if (allowInsecure) return ValidateResult.Insecure(R.raw.insecure)
        }

        is VLESSBean -> {
            if (!isTLS()) return ValidateResult.Insecure(R.raw.not_encrypted)
            if (allowInsecure) return ValidateResult.Insecure(R.raw.insecure)
        }

        is TrojanBean -> {
            if (!isTLS()) return ValidateResult.Insecure(R.raw.not_encrypted)
            if (allowInsecure) return ValidateResult.Insecure(R.raw.insecure)
        }

        is HysteriaBean -> {
            if (allowInsecure) return ValidateResult.Insecure(R.raw.insecure)
            if (protocolVersion < HysteriaBean.PROTOCOL_VERSION_2) return ValidateResult.Deprecated(
                R.raw.hysteria_legacy
            )
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
