package io.nekohasekai.sagernet.database

import android.content.Context
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_ANYTLS
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_CHAIN
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_CONFIG
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_DIRECT
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_HTTP
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_HYSTERIA
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_JUICITY
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_MIERU
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_NAIVE
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_PROXY_SET
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_SHADOWQUIC
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_SHADOWTLS
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_SOCKS
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_SS
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_SSH
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_TROJAN
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_TRUST_TUNNEL
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_TUIC
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_VLESS
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_VMESS
import io.nekohasekai.sagernet.database.ProxyEntity.Companion.TYPE_WG
import io.nekohasekai.sagernet.fmt.v2ray.isTLS

fun ProxyEntity.displayType(context: Context): String = when (type) {
    TYPE_SOCKS -> socksBean!!.protocolName()
    TYPE_HTTP -> if (httpBean!!.isTLS()) "HTTPS" else "HTTP"
    TYPE_SS -> "Shadowsocks"
    TYPE_VMESS -> "VMess"
    TYPE_VLESS -> "VLESS"
    TYPE_TROJAN -> "Trojan"
    TYPE_MIERU -> "Mieru"
    TYPE_NAIVE -> "Naïve"
    TYPE_HYSTERIA -> "Hysteria" + hysteriaBean!!.protocolVersion
    TYPE_SSH -> "SSH"
    TYPE_WG -> "WireGuard"
    TYPE_TUIC -> "TUIC"
    TYPE_JUICITY -> "Juicity"
    TYPE_SHADOWTLS -> "ShadowTLS"
    TYPE_DIRECT -> "Direct"
    TYPE_ANYTLS -> "AnyTLS"
    TYPE_SHADOWQUIC -> "Shadow QUIC"
    TYPE_PROXY_SET -> proxySetBean!!.displayType()
    TYPE_TRUST_TUNNEL -> "Trust Tunnel"
    TYPE_CHAIN -> context.getString(R.string.proxy_chain)
    TYPE_CONFIG -> configBean!!.displayType()
    else -> "Undefined type $type"
}