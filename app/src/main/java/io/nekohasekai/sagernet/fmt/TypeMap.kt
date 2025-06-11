package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.database.ProxyEntity

object TypeMap : HashMap<String, Int>() {
    init {
        this["socks"] = ProxyEntity.TYPE_SOCKS
        this["http"] = ProxyEntity.TYPE_HTTP
        this["ss"] = ProxyEntity.TYPE_SS
        this["vmess"] = ProxyEntity.TYPE_VMESS
        this["trojan"] = ProxyEntity.TYPE_TROJAN
        this["mieru"] = ProxyEntity.TYPE_MIERU
        this["naive"] = ProxyEntity.TYPE_NAIVE
        this["hysteria"] = ProxyEntity.TYPE_HYSTERIA
        this["ssh"] = ProxyEntity.TYPE_SSH
        this["wg"] = ProxyEntity.TYPE_WG
        this["tuic"] = ProxyEntity.TYPE_TUIC
        this["juicity"] = ProxyEntity.TYPE_JUICITY
        this["direct"] = ProxyEntity.TYPE_DIRECT
        this["anytls"] = ProxyEntity.TYPE_ANYTLS
        this["shadowquic"] = ProxyEntity.TYPE_SHADOWQUIC
        this["config"] = ProxyEntity.TYPE_CONFIG
    }

    val reversed = HashMap<Int, String>()

    init {
        TypeMap.forEach { (key, type) ->
            reversed[type] = key
        }
    }

}