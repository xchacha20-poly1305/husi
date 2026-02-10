package fr.husi.database

import fr.husi.database.ProxyEntity.Companion.TYPE_ANYTLS
import fr.husi.database.ProxyEntity.Companion.TYPE_CHAIN
import fr.husi.database.ProxyEntity.Companion.TYPE_CONFIG
import fr.husi.database.ProxyEntity.Companion.TYPE_DIRECT
import fr.husi.database.ProxyEntity.Companion.TYPE_HTTP
import fr.husi.database.ProxyEntity.Companion.TYPE_HYSTERIA
import fr.husi.database.ProxyEntity.Companion.TYPE_JUICITY
import fr.husi.database.ProxyEntity.Companion.TYPE_MIERU
import fr.husi.database.ProxyEntity.Companion.TYPE_NAIVE
import fr.husi.database.ProxyEntity.Companion.TYPE_PROXY_SET
import fr.husi.database.ProxyEntity.Companion.TYPE_SHADOWQUIC
import fr.husi.database.ProxyEntity.Companion.TYPE_SHADOWTLS
import fr.husi.database.ProxyEntity.Companion.TYPE_SOCKS
import fr.husi.database.ProxyEntity.Companion.TYPE_SS
import fr.husi.database.ProxyEntity.Companion.TYPE_SSH
import fr.husi.database.ProxyEntity.Companion.TYPE_TROJAN
import fr.husi.database.ProxyEntity.Companion.TYPE_TRUST_TUNNEL
import fr.husi.database.ProxyEntity.Companion.TYPE_TUIC
import fr.husi.database.ProxyEntity.Companion.TYPE_VLESS
import fr.husi.database.ProxyEntity.Companion.TYPE_VMESS
import fr.husi.database.ProxyEntity.Companion.TYPE_WG
import fr.husi.repository.repo
import fr.husi.resources.*
import kotlinx.coroutines.runBlocking

fun ProxyEntity.displayType(): String = when (type) {
    TYPE_SOCKS -> socksBean!!.protocolName()
    TYPE_HTTP -> if (httpBean!!.isTLS) "HTTPS" else "HTTP"
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
    TYPE_SHADOWQUIC -> shadowQUICBean!!.displayType()
    TYPE_PROXY_SET -> proxySetBean!!.displayType()
    TYPE_TRUST_TUNNEL -> "Trust Tunnel"
    TYPE_CHAIN -> runBlocking {
        repo.getString(Res.string.proxy_chain)
    }
    TYPE_CONFIG -> configBean!!.displayType()
    else -> "Undefined type $type"
}
